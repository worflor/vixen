import {evaluateCurve,registerRootCurve,registerUnaryCurve,latestWriteTime,curveOrigin,curveLineage,curveConfig,curveDomain,curveInfluenceHead,appendInfluence,materializeInfluences,snapshotCurve,forkCurve} from './vixen-api.mjs';
import {Wide} from './vixen-wide.mjs';
import {applyWidePolicy,makeScaledSample} from './vixen-scaled-state.mjs';
import {compileGuide} from './vixen-guide.mjs';

export const EXTENDED_OPERATION_ALGORITHM='vixen-extended-operations/1';
const MAX=2**40, ONE=Wide.fromNumber(1), ZERO=Wide.fromNumber(0);
const ABS=['base','value','guideValue','positiveValue','unconstrainedValue','unconstrainedGuideValue'];
const WIDE_FIELDS=['base','value','guideValue','positiveValue','unconstrainedValue','unconstrainedGuideValue','floorValue','ceilingValue','boundaryWidth'];
const SCALE=k=>'scaled'+k[0].toUpperCase()+k.slice(1);
const nativeNodes=new WeakMap(), publicNodes=new WeakMap(), nodePublic=new WeakMap();
const CONSERVATION=Object.freeze({kind:'finite-precision-tolerance',potential:'t^4*(1-t)^4*P(t)',perturbation:'d/dt potential',claim:'Finite-precision conservation is tolerance-qualified; no exact-real claim'});
const finite=(v,n)=>{if(typeof v!=='number'||!Number.isFinite(v))throw new RangeError(`${n} must be finite`);return v;};
function fields(v,allowed,required=[],name='record'){
 if(!v||typeof v!=='object'||Array.isArray(v)||Reflect.ownKeys(v).some(k=>typeof k!=='string'||!allowed.includes(k))||required.some(k=>!Object.hasOwn(v,k)))throw new TypeError(`Invalid ${name} fields`);
}
function freeze(v){if(v&&typeof v==='object'&&!Object.isFrozen(v)){for(const x of Object.values(v))freeze(x);Object.freeze(v);}return v;}
function domain(v,lo=1,hi=MAX){if(!Array.isArray(v)||v.length!==2)throw new TypeError('Domain requires two endpoints');const a=finite(v[0],'domain lower bound'),b=finite(v[1],'domain upper bound');if(a<lo||b>hi||a>=b)throw new RangeError('Domain is outside its allowed interval');return Object.freeze([a,b]);}
function guideNumber(v){if(typeof v!=='number'||!Number.isFinite(v)||Math.abs(v)>1e250)throw new RangeError('Guide number outside finite bounds');return v;}
export function extendedPolynomialGuide(coefficients,options={}){
 fields(options,['center','scale','domain']);
 if(!Array.isArray(coefficients)||coefficients.length<1||coefficients.length>65)throw new RangeError('Polynomial requires 1..65 coefficients');
 const c=Object.freeze(coefficients.map(guideNumber)),center=finite(options.center??0,'center'),scale=finite(options.scale??1,'scale');
 if(Math.abs(center)>MAX||scale<1e-6||scale>1e6)throw new RangeError('Invalid extended guide center or scale');
 return Object.freeze({format:'vixen-extended-guide/1',kind:'polynomial',coefficients:c,center,scale,domain:domain(options.domain??[1,MAX])});
}
function compileTarget(target){
 if(!target||typeof target!=='object'||Array.isArray(target))throw new TypeError('Guide data is required');
 if(target.format==='vixen-extended-guide/1'){
  fields(target,['format','kind','coefficients','center','scale','domain'],['format','kind','coefficients','center','scale','domain'],'extended guide');
  if(target.kind!=='polynomial')throw new TypeError('Unsupported extended guide kind');
  const spec=extendedPolynomialGuide(target.coefficients,{center:target.center,scale:target.scale,domain:target.domain});
  return {spec,domain:spec.domain,zero:spec.coefficients.every(x=>x===0),evaluate:x=>{
   finite(x,'guide coordinate');if(x<spec.domain[0]||x>spec.domain[1])throw new RangeError('Guide coordinate outside its domain');
   const z=Wide.fromNumber(x-spec.center).div(Wide.fromNumber(spec.scale));let y=ZERO;
   for(let i=spec.coefficients.length-1;i>=0;i--)y=y.mul(z).add(Wide.fromNumber(spec.coefficients[i]));return y;
  }};
 }
 const g=compileGuide(target),s=g.spec,zero=s.kind==='polynomial'?s.coefficients.every(x=>x===0):s.knots.every(k=>k.y===0&&k.d1===0&&k.d2===0);
 return {spec:s,domain:g.domain,zero,evaluate:x=>Wide.fromNumber(g.evaluate(x))};
}
function vector(v){if(!Array.isArray(v)&&!ArrayBuffer.isView(v))throw new TypeError('Coordinates must contain 16 numbers');if(v.length!==16)throw new RangeError('Coordinates must contain exactly 16 numbers');const a=Array.from(v);if(a.some(x=>typeof x!=='number'||!Number.isFinite(x)||Math.abs(x)>1e6))throw new RangeError('Coordinates must be finite in [-1000000,1000000]');return a;}
function readWide(p){const r={...p};for(const k of WIDE_FIELDS){if(!Object.hasOwn(p,SCALE(k)))throw new TypeError(`Missing ${SCALE(k)}`);const s=p[SCALE(k)];r[k]=s===null?null:Wide.decode(s);}return r;}
function scaled(state){return makeScaledSample(applyWidePolicy(state));}
function smooth5(t){return t<=0?0:t>=1?1:t*t*t*(10+t*(-15+6*t));}
function ramp(t){return t<=0?0:t>=1?1:t*t*t*(6+t*(-8+3*t));}
function influenceWeight(x,r){const {at,amount,handoffWidth:w}=r;if(x<=at)return 0;if(!r.windowed)return amount*smooth5((x-at)/w);const e=at+w;if(x<e)return amount*smooth5((x-at)/w);if(r.releaseWidth===0)return x<r.until?amount:0;if(x<=r.until)return amount;const end=r.until+r.releaseWidth;return x<end?amount*(1-smooth5((x-r.until)/r.releaseWidth)):0;}
function influenceOptions(r){const o={at:r.at,amount:r.amount,handoffWidth:r.handoffWidth,reference:r.reference,mode:r.mode};if(r.windowed){o.until=r.until;o.releaseWidth=r.releaseWidth;}return o;}
function normalizeInfluence(parent,target,options={},restoring=false){
 fields(options,['at','amount','handoffWidth','count','until','releaseWidth','reference','mode']);if(Object.hasOwn(options,'count'))throw new TypeError('Callback capture is not supported by numeric extended operations');
 const d=curveDomain(parent),cfg=curveConfig(parent),last=latestWriteTime(parent),at=finite(options.at??Math.max(cfg.openingEndpoint,last,d[0]),'activation');
 if(at<(restoring?1:d[0])||at>d[1]||(!restoring&&at<last))throw new RangeError('Influence activation violates chronology or domain');
 const amount=options.amount??.5;if(amount<0||amount>1)throw new RangeError('Influence amount must lie in [0,1]');
 const mode=options.mode??'blend',reference=options.reference??(mode==='add'?'baseline':'value');
 if(!['blend','add'].includes(mode)||!['value','baseline','backbone'].includes(reference))throw new TypeError('Invalid influence mode or reference');
 const g=compileTarget(target),windowed=Object.hasOwn(options,'until')||Object.hasOwn(options,'releaseWidth');
 if(windowed&&(!Object.hasOwn(options,'until')||!Object.hasOwn(options,'releaseWidth')))throw new TypeError('Window requires until and releaseWidth');
 const limit=Math.min(d[1],g.domain[1]),w=finite(options.handoffWidth??Math.min(16,limit-at),'handoffWidth'),end=at+w;
 if(w<=0||w>50000||end<=at||end>d[1]||g.domain[0]>at||end>limit)throw new RangeError('Guide must cover the influence handoff');
 let until,releaseWidth,releaseEnd;
 if(windowed){until=finite(options.until,'until');releaseWidth=finite(options.releaseWidth,'releaseWidth');if(releaseWidth<=0||releaseWidth>50000||until<end||until>d[1])throw new RangeError('Invalid influence release window');releaseEnd=until+releaseWidth;if(releaseEnd<=until||releaseEnd>d[1]||g.domain[1]<releaseEnd)throw new RangeError('Guide must cover the active window through release');}
 const recipe=Object.freeze({at,amount,handoffWidth:w,windowed,until,releaseWidth,releaseEnd,reference,mode,guide:g.spec});
 return {recipe,g,domain:Object.freeze([d[0],windowed?d[1]:limit]),zeroGuide:mode==='add'&&g.zero};
}
function affected(p,target,a,cfg,reference,mode){
 if(a===0)return p;const q=readWide(p),base=q.base,extra=base.mul(Wide.fromNumber(a)).mul(Wide.fromNumber(cfg.boundarySoftness));let width=q.boundaryWidth;
 const lo=q.floorValue,hi=q.ceilingValue;
 if(lo!==null&&hi!==null){const room=hi.sub(lo).scale(-1).sub(width);if(room.cmp(ZERO)>0&&extra.cmp(ZERO)>0){const inc=extra.mul(room).div(room.add(extra));width=width.add(inc);}}
 else if(lo!==null||hi!==null)width=width.add(extra);
 let u=q.unconstrainedValue;
 if(mode==='add')u=u.add(reference==='value'?target.mul(Wide.fromNumber(a)):base.mul(Wide.fromNumber(a)).mul(target));
 else if(reference==='value')u=a===1?target:u.mul(Wide.fromNumber(1-a)).add(target.mul(Wide.fromNumber(a)));
 else if(reference==='baseline'){const t=base.mul(ONE.add(target));u=a===1?t:u.mul(Wide.fromNumber(1-a)).add(t.mul(Wide.fromNumber(a)));}
 else {const t=q.unconstrainedGuideValue.add(base.mul(target));u=a===1?t:u.mul(Wide.fromNumber(1-a)).add(t.mul(Wide.fromNumber(a)));}
 return scaled({...q,boundaryWidth:width,unconstrainedValue:u});
}
const FACTOR=[0,0,0,0,1,-4,6,-4,1];
function product(a,b){const o=Array(a.length+b.length-1).fill(0);for(let i=0;i<a.length;i++)for(let j=0;j<b.length;j++)o[i+j]+=a[i]*b[j];if(o.some(x=>!Number.isFinite(x)))throw new RangeError('Redistribution coefficients exceed finite range');return o;}
function derivative(c,n=1){let a=c.slice();while(n--)a=a.slice(1).map((v,i)=>v*(i+1));return a.length?a:[0];}
function horner(c,t){let y=0;for(let i=c.length-1;i>=0;i--)y=y*t+c[i];return y;}
function normalizeImpulse(input,d,restoring=false){
 fields(input,['type','at','magnitude','relaxation','attack'],['at','magnitude','relaxation'],'impulse recipe');if(input.type!==undefined&&input.type!=='impulse')throw new TypeError('Operation and recipe disagree');
 const at=finite(input.at,'at'),magnitude=finite(input.magnitude,'magnitude'),relaxation=finite(input.relaxation,'relaxation'),attack=finite(input.attack??relaxation/8,'attack'),rise=at+attack,end=rise+relaxation;
 if(attack<=0||relaxation<=0||rise<=at||end<=rise||!Number.isFinite(end)||(at<(restoring?1:d[0]))||rise>d[1]||end>d[1]||end<=d[0])throw new RangeError('Impulse support must lie inside the available domain');
 return Object.freeze({type:'impulse',at,magnitude,relaxation,attack});
}
function normalizeRedistribution(input,d,restoring=false){
 fields(input,['type','from','until','coefficients','magnitude','measure','certificate','conservation'],['from','until','coefficients'],'redistribution recipe');if(input.type!==undefined&&input.type!=='redistribution')throw new TypeError('Invalid redistribution recipe');
 const from=finite(input.from,'from'),until=finite(input.until,'until'),magnitude=finite(input.magnitude??1,'magnitude'),measure=input.measure??'actualDeviation';
 if(!Array.isArray(input.coefficients)||input.coefficients.length<1||input.coefficients.length>64)throw new RangeError('Shape requires 1..64 coefficients');
 const coefficients=input.coefficients.map(x=>finite(x,'shape coefficient'));if(until<=from||from+(until-from)<=from||(from<(restoring?1:d[0]))||until>d[1]||(restoring&&until<=d[0]))throw new RangeError('Redistribution window must lie inside the available domain');
 if(!['actualDeviation','unconstrainedDeviation'].includes(measure))throw new TypeError('Invalid redistribution measure');
 if(input.conservation!==undefined&&(!input.conservation||Object.keys(input.conservation).length!==Object.keys(CONSERVATION).length||Object.keys(CONSERVATION).some(k=>input.conservation[k]!==CONSERVATION[k])))throw new TypeError('Invalid redistribution conservation metadata');
 if(input.certificate!==undefined&&!['exact-real-endpoint-integral','finite-precision-tolerance'].includes(input.certificate.kind))throw new TypeError('Invalid redistribution certificate');
 const q=product(FACTOR,coefficients),radius=Math.abs(magnitude)*derivative(q).reduce((s,x)=>s+Math.abs(x),0);if(!Number.isFinite(radius))throw new RangeError('Redistribution bound exceeds finite range');
 return Object.freeze({type:'redistribution',from,until,coefficients:Object.freeze(coefficients),magnitude,measure,conservation:CONSERVATION});
}
function perturbation(r,x){if(r.magnitude===0)return 0;if(r.type==='impulse'){const rise=r.at+r.attack,end=rise+r.relaxation;if(x<=r.at||x>=end)return 0;if(x<=rise)return r.magnitude*smooth5((x-r.at)/r.attack);return r.magnitude*(1-smooth5((x-rise)/r.relaxation));}
 if(x<=r.from||x>=r.until)return 0;const t=(x-r.from)/(r.until-r.from),f=horner(derivative(FACTOR),t),p=horner(r.coefficients,t);return r.magnitude*(f*p+horner(FACTOR,t)*horner(derivative(r.coefficients),t));}
function constrained(curve,r){const cfg=curveConfig(curve);if(r.type==='redistribution'&&r.measure==='actualDeviation'&&r.magnitude!==0&&(cfg.floor!=='none'||cfg.ceilingMargin!==null))throw new RangeError('Actual-deviation redistribution requires an unconstrained source');}
function operationFrame(parents,kind,r){return Object.freeze({algorithm:EXTENDED_OPERATION_ALGORITHM,operation:kind,curve:parents[0],recipe:r});}
class NativeProxy{
 constructor(source){this.source=source;this.config=source.config;this.domain=Object.freeze(Array.from(source.domain));this.lineage=source.lineage;this.algorithm=source.algorithm;this.availableFrom=this.domain[0];registerRootCurve(this,{domain:this.domain,latest:latestSource(source)});Object.freeze(this);}
 sample(x){return this.source.sampleNormalized(x);} at(x,h){return this.source.atNormalized(x,h);}
 ridges(){return this.source.ridges();} get dips(){return this.source.dips;}
 snapshot(){return this.source.snapshot();} tail(x){return this.source.tail(x);}
 fork(o){return rootProxy(this.source.fork(o));} location(x){return this.source.location(x);} inspect(x){return this.source.inspect(x);}
 ridges(){return this.source.ridges();} get dips(){return this.source.dips;}
}
function latestSource(s){let n=Math.max(1,s.domain?.[0]??1);for(const e of s.lineage??[])n=Math.max(n,e.at??e['at']??1);for(const e of s.influences??[])n=Math.max(n,e.at??e['at']??1);return n;}
function rootProxy(source){let n=nativeNodes.get(source);if(n)return n;if(!source||typeof source.sampleNormalized!=='function'||typeof source.atNormalized!=='function'||!Array.isArray(source.domain))throw new TypeError('A native extended curve is required');n=new NativeProxy(source);nativeNodes.set(source,n);return n;}
export function extendedProxy(curve){return publicNodes.get(apiFor(curve));}
export function extendedPublic(node){return publicFor(node);}
function apiFor(curve){if(publicNodes.has(curve))return curve;const node=rootProxy(curve);return publicFor(node,curve);}
class OperationNode{
 constructor(parent,kind,recipe,compiled,domain,latest,start,end,identity,transform,rebuild){this.domain=domain;this.config=curveConfig(parent);this.algorithm=EXTENDED_OPERATION_ALGORITHM;this.parent=parent;this.kind=kind;this.recipe=recipe;this.compiled=compiled;this.transform=transform;this.snapshot=()=>snapshotCurve(this);this.tail=x=>snapshotCurve(this,x);this.fork=o=>publicFor(forkCurve(this,o));
  registerUnaryCurve(this,parent,{domain,latest,origin:curveOrigin(parent),influenceHead:curveInfluenceHead(parent),interval:[start,end],alwaysIdentity:identity,identityAt:x=>identity||x<=start||x>=end,transform:(p,x)=>transform(p,x),snapshotFrame:p=>operationFrame(p,kind,recipe),discardOnTail:x=>end!==Infinity&&end<=x,rebuild:p=>rebuild(p[0])});Object.freeze(this);
 }
 sample(x){return evaluateCurve(this,x);}at(x,h){return evaluateCurve(this,x,h,true);}
}
function makeInfluence(parent,target,options,restoring=false){const n=normalizeInfluence(parent,target,options,restoring),{recipe:r,g}=n,cfg=curveConfig(parent),entry=Object.freeze({at:r.at,amount:r.amount,handoffWidth:r.handoffWidth,guide:r.guide,reference:r.reference,mode:r.mode,...(r.windowed?{until:r.until,releaseWidth:r.releaseWidth}:{})}),head=appendInfluence(parent,entry),end=r.windowed?r.releaseEnd:Infinity;
 const run=(p,x)=>{const a=influenceWeight(x,r);if(a===0||n.zeroGuide)return p;return affected(p,g.evaluate(x),a,cfg,r.reference,r.mode);};
 return new OperationNode(parent,'influence',Object.freeze({guide:r.guide,at:r.at,amount:r.amount,handoffWidth:r.handoffWidth,reference:r.reference,mode:r.mode,...(r.windowed?{until:r.until,releaseWidth:r.releaseWidth}:{})}),g,n.domain,Math.max(latestWriteTime(parent),r.at),r.at,end,r.amount===0||n.zeroGuide,run,p=>makeInfluence(p,r.guide,influenceOptions(r),true));
}
function makeOperation(parent,r,restoring=false){constrained(parent,r);const start=r.type==='impulse'?r.at:r.from,end=r.type==='impulse'?r.at+r.attack+r.relaxation:r.until;
 const run=(p,x)=>{const delta=perturbation(r,x);if(delta===0)return p;const q=readWide(p),u=q.unconstrainedValue.add(q.base.mul(Wide.fromNumber(delta)));return scaled({...q,unconstrainedValue:u});};
 return new OperationNode(parent,r.type,r,null,parent.domain,Math.max(latestWriteTime(parent),start),start,end,r.magnitude===0,run,p=>makeOperation(p,r,true));
}
function publicFor(node,source=null){let api=nodePublic.get(node);if(api)return api;const check=x=>{finite(x,'coordinate');const d=curveDomain(node);if(x<d[0]||x>d[1])throw new RangeError('Coordinate outside available domain');};
 api=Object.freeze({get algorithm(){return source?.algorithm??EXTENDED_OPERATION_ALGORITHM;},get config(){return curveConfig(node);},get domain(){return Object.freeze(Array.from(curveDomain(node)));},get normalizedDomain(){return this.domain;},get availableFrom(){return curveDomain(node)[0];},get lineage(){return curveLineage(node);},get influences(){return materializeInfluences(curveInfluenceHead(node));},
 sampleNormalized:x=>{check(x);return evaluateCurve(node,x);},atNormalized:(x,h)=>{check(x);return evaluateCurve(node,x,vector(h),true);},
 sample(x){const p=this.sampleNormalized(x);for(const k of ABS)if(p[k]===null)throw new RangeError(`Raw ${k} unavailable; use sampleNormalized()`);return p;},
 at(x,h){const p=this.atNormalized(x,h);for(const k of ABS)if(p[k]===null)throw new RangeError(`Raw ${k} unavailable; use atNormalized()`);return p;},
 influence:(target,o={})=>withExtendedInfluence(api,target,o),impulse:r=>withExtendedImpulse(api,r),redistribute:(w,s)=>withExtendedRedistribution(api,{from:w[0],until:w[1],...(Array.isArray(s)?{coefficients:s}:s)}),
 fork:o=>publicFor(forkCurve(node,o)),snapshot:()=>snapshotCurve(node),tail:x=>{check(x);return snapshotCurve(node,x);},
 location:x=>{check(x);return curveOrigin(node).location(x);},inspect:x=>{check(x);return curveOrigin(node).inspect(x);},ridges:()=>curveOrigin(node).ridges(),get dips(){return curveOrigin(node).dips;}});
 publicNodes.set(api,node);nodePublic.set(node,api);return api;
}
export function withExtendedInfluence(curve,target,options={},restoring=false){if(typeof restoring!=='boolean')throw new TypeError('restoring must be boolean');const api=apiFor(curve),node=publicNodes.get(api);return publicFor(makeInfluence(node,target,options,restoring));}
export function withExtendedImpulse(curve,recipe,restoring=false){if(typeof restoring!=='boolean')throw new TypeError('restoring must be boolean');const api=apiFor(curve),node=publicNodes.get(api),r=normalizeImpulse(recipe,curveDomain(node),restoring);if(!restoring&&r.at<latestWriteTime(node))throw new RangeError('Operation precedes the latest authored coordinate');return publicFor(makeOperation(node,r,restoring));}
export function withExtendedRedistribution(curve,recipe,restoring=false){if(typeof restoring!=='boolean')throw new TypeError('restoring must be boolean');const api=apiFor(curve),node=publicNodes.get(api),r=normalizeRedistribution(recipe,curveDomain(node),restoring);if(!restoring&&r.from<latestWriteTime(node))throw new RangeError('Operation precedes the latest authored coordinate');return publicFor(makeOperation(node,r,restoring));}
function validateTree(root){const active=new Set(),stack=[[root,false,0]];let count=0;while(stack.length){const [v,leave,depth]=stack.pop();if(leave){active.delete(v);continue;}if(v===null||typeof v==='string'||typeof v==='boolean')continue;if(typeof v==='number'){if(!Number.isFinite(v))throw new TypeError('Snapshot contains invalid number');continue;}if(typeof v!=='object'||depth>128||++count>100000)throw new TypeError('Snapshot contains unsupported or excessive data');if(active.has(v))throw new TypeError('Snapshot cycles are not allowed');active.add(v);stack.push([v,true,depth]);if(Array.isArray(v)){for(let i=v.length-1;i>=0;i--)stack.push([v[i],false,depth+1]);}else{const proto=Object.getPrototypeOf(v);if(proto!==Object.prototype&&proto!==null)throw new TypeError('Snapshot objects must be plain');for(const k of Reflect.ownKeys(v)){if(typeof k!=='string')throw new TypeError('Snapshot symbols are not allowed');stack.push([v[k],false,depth+1]);}}}}
export function restoreExtendedOperation(snapshot,read){validateTree(snapshot);if(typeof read!=='function')throw new TypeError('A parent read function is required');fields(snapshot,['algorithm','operation','curve','recipe'],['algorithm','operation','curve','recipe'],'operation snapshot');if(snapshot.algorithm!==EXTENDED_OPERATION_ALGORITHM)throw new TypeError('Unsupported extended operation algorithm');const parent=apiFor(read(snapshot.curve)),r=snapshot.recipe;
 if(snapshot.operation==='influence'){fields(r,['guide','at','amount','handoffWidth','reference','mode','until','releaseWidth'],['guide','at','amount','handoffWidth','reference','mode'],'influence recipe');const o={at:r.at,amount:r.amount,handoffWidth:r.handoffWidth,reference:r.reference,mode:r.mode};if(Object.hasOwn(r,'until')){if(!Object.hasOwn(r,'releaseWidth'))throw new TypeError('Incomplete influence window');o.until=r.until;o.releaseWidth=r.releaseWidth;}else if(Object.hasOwn(r,'releaseWidth'))throw new TypeError('Incomplete influence window');return withExtendedInfluence(parent,r.guide,o,true);}
 if(snapshot.operation==='impulse'){fields(r,['type','at','magnitude','relaxation','attack'],['type','at','magnitude','relaxation','attack'],'impulse recipe');if(r.type!=='impulse')throw new TypeError('Operation and recipe disagree');return withExtendedImpulse(parent,r,true);}
 if(snapshot.operation==='redistribution'){fields(r,['type','from','until','coefficients','magnitude','measure','conservation'],['type','from','until','coefficients','magnitude','measure','conservation'],'redistribution recipe');return withExtendedRedistribution(parent,r,true);}
 throw new TypeError('Unknown extended operation kind');
}
