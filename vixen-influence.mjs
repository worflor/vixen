import {compileGuide,captureGuide} from './vixen-guide.mjs';
export const INFLUENCE_ALGORITHM='vixen-influence-c2/1';
const gate=t=>t<=0?0:t>=1?1:t*t*t*(10+t*(-15+6*t));
function finite(x,lo,hi){if(typeof x!=='number'||!Number.isFinite(x)||x<lo||x>hi)throw new RangeError('Influence number outside its finite domain');}
function ramp(t){return t<=0?0:t>=1?t:t*t*t*(6+t*(-8+3*t));}
function policy(y,p){const {floorValue:lo,ceilingValue:hi,boundaryWidth:e}=p;if(lo!==null&&hi!==null&&hi<=lo)return lo;if(lo!==null){if(y<=lo)y=lo;else if(e>0&&y<lo+e)y=lo+e*ramp((y-lo)/e);}if(hi!==null){if(y>=hi)y=hi;else if(e>0&&y>hi-e)y=hi-e*ramp((hi-y)/e);}return y;}
function affected(p,target,a,cfg){if(a===0)return p;const extra=p.base*a*cfg.boundarySoftness,lo=p.floorValue,hi=p.ceilingValue;let width=p.boundaryWidth;if(lo!==null&&hi!==null){const room=Math.max(0,(hi-lo)/2-width);if(extra>0&&room>0)width+=extra*room/(room+extra);}else if(lo!==null||hi!==null)width+=extra;p={...p,boundaryWidth:width};const u=a===1?target:(1-a)*p.unconstrainedValue+a*target,value=policy(u,p),deviation=(value-p.base)/p.base,positiveMultiplier=Math.exp(.55*Math.tanh(deviation));const guideValue=policy(p.unconstrainedGuideValue,p);return Object.freeze({...p,guideValue,guideDeviation:(guideValue-p.base)/p.base,value,unconstrainedValue:u,unconstrainedDeviation:(u-p.base)/p.base,deviation,multiplier:1+deviation,activity:.5+.5*Math.tanh(deviation),positiveMultiplier,positiveValue:p.base*positiveMultiplier,constraintApplied:value!==u});}
function latest(curve){return Math.max(1,...curve.lineage.map(n=>n.at),...(curve.influences??[]).map(n=>n.at));}
function makeInfluence(curve,target,options={},carried=false){
 if(!options||typeof options!=='object'||Array.isArray(options)||Reflect.ownKeys(options).some(k=>!['at','amount','handoffWidth','count'].includes(k)))throw new TypeError('Influence options: at, amount, handoffWidth, count');
 if(curve.lineage.length+(curve.influences?.length??0)>=32)throw new RangeError('Maximum timeline composition depth: 32');
 const at=options.at===undefined?Math.max(curve.config.openingEndpoint,latest(curve)):options.at,amount=options.amount===undefined?.5:options.amount;finite(at,curve.domain[0],curve.domain[1]);finite(amount,0,1);if(!carried&&at<latest(curve))throw new RangeError('Influence cannot precede existing timeline events');
 if(typeof target==='function')target=captureGuide(target,{domain:[at,curve.domain[1]],count:options.count===undefined?129:options.count});else if(options.count!==undefined)throw new TypeError('count applies only to callback capture');
 const compiled=compileGuide(target),end=Math.min(curve.domain[1],compiled.domain[1]),domain=Object.freeze([curve.domain[0],end]),handoffWidth=options.handoffWidth===undefined?Math.min(16,end-at):options.handoffWidth;
 finite(handoffWidth,Number.MIN_VALUE,50000);if(compiled.domain[0]>at||at+handoffWidth<=at||at+handoffWidth>end)throw new RangeError('Guide must cover a representable handoff inside the domain');
 const entry=Object.freeze({at,amount,handoffWidth,guide:compiled.spec}),influences=Object.freeze([...(curve.influences??[]),entry]);
 function check(x){finite(x,domain[0],domain[1]);}
 function sample(x,h){check(x);const p=h===undefined?curve.sample(x):curve.at(x,h),a=amount*gate((x-at)/handoffWidth);return a===0?p:affected(p,compiled.evaluate(x),a,curve.config);}
 const cfg=curve.config,lower=cfg.floor==='none'?-1e280:cfg.floor==='zero'?-1:cfg.floor==='baseline'?0:-.25*cfg.strength,upper=cfg.ceilingMargin===null?1e280:(cfg.floor==='baseline'?0:.25*cfg.strength)+cfg.ceilingMargin,bounds=Object.freeze({...curve.bounds,deviation:Object.freeze([lower,upper]),multiplier:Object.freeze([1+lower,1+upper])});
 const api=Object.freeze({...curve,algorithm:INFLUENCE_ALGORITHM,domain,bounds,influences,sample:x=>sample(x),at:(x,h)=>{check(x);const p=curve.at(x,h),a=amount*gate((x-at)/handoffWidth);return a===0?p:affected(p,compiled.evaluate(x),a,curve.config);},location:x=>{check(x);return curve.location(x);},inspect:x=>{check(x);return curve.inspect(x);},
 influenceAt:x=>{check(x);const weight=amount*gate((x-at)/handoffWidth),targetValue=weight===0?null:compiled.evaluate(x);return Object.freeze({weight,targetValue,sourceValue:curve.sample(x).value,followingError:targetValue===null?null:sample(x).value-targetValue});},
 snapshot:()=>Object.freeze({algorithm:INFLUENCE_ALGORITHM,curve:curve.snapshot(),guide:compiled.spec,at,amount,handoffWidth}),
 fork:opts=>{if(!opts||typeof opts!=='object'||Array.isArray(opts))throw new TypeError('Fork options required');finite(opts.at,Math.max(domain[0],latest(api)),domain[1]);const width=opts.handoffWidth??Math.min(16,domain[1]-opts.at);finite(width,Number.MIN_VALUE,50000);if(opts.at+width>domain[1])throw new RangeError('Fork exceeds guide domain');return makeInfluence(curve.fork({...opts,handoffWidth:width}),compiled.spec,{at,amount,handoffWidth},true);},
 influence:(g,o)=>makeInfluence(api,g,o)});return api;
}
export function withInfluence(curve,target,options){return makeInfluence(curve,target,options);}
export function restoreInfluence(snapshot,restore){
 const frames=[],seen=new Set();let s=snapshot;
 while(s?.algorithm===INFLUENCE_ALGORITHM){if(!s||typeof s!=='object'||Array.isArray(s)||seen.has(s)||frames.length>=32)throw new TypeError('Invalid influence composition');seen.add(s);const keys=['algorithm','curve','guide','at','amount','handoffWidth'];if(Reflect.ownKeys(s).some(k=>!keys.includes(k))||keys.some(k=>!Object.hasOwn(s,k)))throw new TypeError('Invalid influence snapshot fields');frames.push(s);s=s.curve;}
 let result=restore(s);for(const f of frames.reverse())result=makeInfluence(result,f.guide,{at:f.at,amount:f.amount,handoffWidth:f.handoffWidth},true);return result;
}
