import {sumProducts} from './vixen-float.mjs';
const j=(value,d1=0,d2=0)=>({value,d1,d2});
export const jet=(value,d1=0,d2=0)=>Object.freeze(j(value,d1,d2));
export const jetAdd=(a,b)=>j(a.value+b.value,a.d1+b.d1,a.d2+b.d2);
export const jetSub=(a,b)=>j(a.value-b.value,a.d1-b.d1,a.d2-b.d2);
export const jetMul=(a,b)=>{
 let d1=a.d1*b.value+a.value*b.d1,d2=a.d2*b.value+2*a.d1*b.d1+a.value*b.d2;
 if([a.value,a.d1,a.d2,b.value,b.d1,b.d2].every(Number.isFinite)){
  if(!Number.isFinite(d1))d1=sumProducts([[a.d1,b.value],[a.value,b.d1]]);
  if(!Number.isFinite(d2))d2=sumProducts([[a.d2,b.value],[2,a.d1,b.d1],[a.value,b.d2]]);
 }
 return j(a.value*b.value,d1,d2);
};
const unary=(a,value,first,second)=>j(value,first*a.d1,first*a.d2+second*a.d1*a.d1);
export const jetDiv=(a,b)=>{
 const value=a.value/b.value;let d1=(a.d1-value*b.d1)/b.value,d2=(a.d2-value*b.d2-2*d1*b.d1)/b.value;
 if(b.value!==0&&[a.value,a.d1,a.d2,b.value,b.d1,b.d2].every(Number.isFinite)){
  if(!Number.isFinite(d1))d1=sumProducts([[a.d1,b.value],[-1,a.value,b.d1]],[b.value,b.value]);
  if(!Number.isFinite(d2))d2=sumProducts([[a.d2,b.value,b.value],[-1,a.value,b.d2,b.value],[-2,a.d1,b.value,b.d1],[2,a.value,b.d1,b.d1]],[b.value,b.value,b.value]);
 }
 return j(value,d1,d2);
};
export const jetExp=a=>{const v=Math.exp(a.value);return unary(a,v,v,v);};
export const jetLog=a=>{
 const d1=a.d1/a.value;let d2=a.d2/a.value-d1*d1;
 if(!Number.isFinite(d2)&&[a.value,a.d1,a.d2].every(Number.isFinite)&&a.value>0)d2=sumProducts([[a.d2,a.value],[-1,a.d1,a.d1]],[a.value,a.value]);
 return j(Math.log(a.value),d1,d2);
};
export const jetTanh=a=>{const v=Math.tanh(a.value),z=Math.exp(-2*Math.abs(a.value)),p=4*z/((1+z)*(1+z));return unary(a,v,p,-2*v*p);};
export function jetSmooth5(a){if(a.value<=0)return j(0);if(a.value>=1)return j(1);const t=a.value,v=t*t*t*(10+t*(-15+6*t));return unary(a,v,30*t*t*(t-1)*(t-1),60*t*(t-1)*(2*t-1));}
export function jetBoundaryRamp(a){if(a.value<=0)return j(0);if(a.value>=1)return j(a.value,a.d1,a.d2);const t=a.value;return unary(a,t*t*t*(6+t*(-8+3*t)),18*t*t-32*t*t*t+15*t**4,36*t-96*t*t+60*t*t*t);}
const C=n=>j(n), add=(a,b)=>jetAdd(a,typeof b==='number'?C(b):b), sub=(a,b)=>jetSub(a,typeof b==='number'?C(b):b), mul=(a,b)=>jetMul(a,typeof b==='number'?C(b):b), div=(a,b)=>jetDiv(a,typeof b==='number'?C(b):b), neg=a=>mul(a,-1);
const smooth=n=>jetSmooth5(C(n)).value;
const MASK=0xffffffff;
function seed32(seed){if(typeof seed==='number'){if(!Number.isInteger(seed)||seed<0||seed>MASK)throw new RangeError('Invalid numeric seed');return seed>>>0;}if(typeof seed!=='string'||seed.length>1024)throw new TypeError('Invalid seed');let h=2166136261;for(const b of new TextEncoder().encode(seed))h=Math.imul(h^b,16777619)>>>0;return h;}
function hash32(seed,index,channel){let h=(seed^Math.imul(index>>>0,0x9e3779b9)^Math.imul(channel>>>0,0x85ebca6b))>>>0;h=Math.imul(h^(h>>>16),0x7feb352d)>>>0;h=Math.imul(h^(h>>>15),0x846ca68b)>>>0;return (h^(h>>>16))>>>0;}
const unit=(s,k,c)=>hash32(s,k,c)/4294967296;
function vector(v){if((!Array.isArray(v)&&!ArrayBuffer.isView(v))||v.length!==16||Array.from(v).some(n=>typeof n!=='number'||!Number.isFinite(n)||Math.abs(n)>1e6))throw new RangeError('Coordinates must be 16 finite numbers in [-1000000,1000000]');return Array.from(v);}
function native(curve){if(!curve||typeof curve!=='object'||typeof curve.sample!=='function'||typeof curve.at!=='function'||typeof curve.ridges!=='function'||!curve.config||!Array.isArray(curve.dips))throw new TypeError('A native leaf-like object is required');if(typeof curve.fork==='function'||'lineage' in curve)throw new TypeError('Pass a native leaf, not a forked timeline');const c=curve.config,r=curve.ridges();if(!Array.isArray(r)||r.length!==48)throw new TypeError('A native leaf must expose its 48 ridges');for(const q of r){vector(q.direction);for(const k of ['scale','phase','basePhase','slope','guidePhase','guideSlope','channel','index'])if(typeof q[k]!=='number'||!Number.isFinite(q[k]))throw new TypeError('Invalid ridge data');if(q.scale<=0)throw new TypeError('Invalid ridge scale');}seed32(c.seed);return {c,r};}
function noise(p,s,ch){const k=Math.floor(p.value),t=sub(p,k),a=2*unit(s,k,ch)-1,b=2*unit(s,k+1,ch)-1;return add(C(a),mul(sub(C(b),C(a)),jetSmooth5(t)));}
function authored(t,c,dips){
 const old=()=>sub(sub(mul(t,jetExp(div(t,100))),mul(jetExp(neg(div(mul(sub(t,42),sub(t,42)),32))),25)),mul(jetExp(neg(div(mul(sub(t,71),sub(t,71)),28))),38));
 const base=old();if(c.introVariation===0||t.value>=100)return base;
 let fresh=mul(t,jetExp(div(t,100)));
 for(const d of dips){const z=div(sub(t,d.center),d.width);fresh=sub(fresh,mul(jetExp(neg(mul(z,z))),d.depth));}
 const w=sub(C(1),jetSmooth5(div(sub(t,90),10)));return add(base,mul(sub(fresh,base),w));
}
function hardContact(y,bound,lower){
 const z=jetSub(y,bound);
 if(z.d1!==0)return {value:bound,hard:true};
 const inside=lower?z.d2>0:z.d2<0;
 return {value:inside?y:bound,hard:false};
}
function floorMap(y,lo,e){
 if(e.value===0&&y.value===lo.value)return hardContact(y,lo,true);
 if(y.value<=lo.value)return {value:lo,hard:false};
 if(e.value===0||y.value>=lo.value+e.value)return {value:y,hard:false};
 return {value:add(lo,mul(e,jetBoundaryRamp(div(sub(y,lo),e)))),hard:false};
}
function ceilMap(y,hi,e){
 if(e.value===0&&y.value===hi.value)return hardContact(y,hi,false);
 if(y.value>=hi.value)return {value:hi,hard:false};
 if(e.value===0||y.value<=hi.value-e.value)return {value:y,hard:false};
 return {value:sub(hi,mul(e,jetBoundaryRamp(div(sub(hi,y),e)))),hard:false};
}
export function applyBoundaryJets(y,lo,hi,e){
 if(lo&&hi&&hi.value<=lo.value)return {value:lo,hard:false};
 let z=lo?floorMap(y,lo,e):{value:y,hard:false};
 if(hi){const next=ceilMap(z.value,hi,e);z={value:next.value,hard:z.hard||next.hard};}
 return z;
}
const finiteJet=a=>Number.isFinite(a.value)&&Number.isFinite(a.d1)&&Number.isFinite(a.d2);
export function leafJet(curve,x,coordinates){
 const {c,r}=native(curve);if(typeof x!=='number'||!Number.isFinite(x))throw new RangeError('x must be finite');
 const h=coordinates===undefined?undefined:vector(coordinates),sample=h===undefined?curve.sample(x):curve.at(x,h),X=j(x,1,0),endpoint=c.openingEndpoint;
 let score=C(0),guide=C(0),scoreJump=x===endpoint;
 if(x>endpoint){const clock=c.openingEndpoint===100?X:add(C(1),mul(sub(X,1),99/(endpoint-1))),u=sub(clock,100),s=seed32(c.seed);
  for(const q of r){let p,g;if(h===undefined){p=add(C(q.phase),mul(u,q.slope));g=add(C(q.guidePhase),mul(u,q.guideSlope));}else{let projection=0;for(let i=0;i<16;i++)projection+=q.direction[i]*h[i];p=add(add(C(q.basePhase),projection),div(u,q.scale));g=add(add(C(q.basePhase),projection/32),div(u,32*q.scale));}score=add(score,mul(noise(p,s,q.channel),1/48));guide=add(guide,mul(noise(g,s,q.channel),1/48));}
 }
 const gate=jetSmooth5(div(sub(X,endpoint),c.ramp)),raw=mul(mul(mul(gate,c.strength*2.2),jetTanh(mul(score,c.contrast))),1),ug=mul(mul(gate,c.strength*.25),jetTanh(mul(guide,2))),w=smooth(c.stabilizer),u=w===0?raw:w===1?ug:add(raw,mul(sub(ug,raw),w));
 const base=authored(c.openingEndpoint===100?X:add(C(1),mul(sub(X,1),99/(endpoint-1))),c,curve.dips),uv=add(base,mul(base,u)),ugv=add(base,mul(base,ug));
 const floor=c.floor==='none'?null:c.floor==='zero'?C(0):c.floor==='baseline'?base:ugv;
 const ceiling=c.ceilingMargin===null?null:add(c.floor==='baseline'?base:ugv,mul(base,c.ceilingMargin));
 const a=mul(gate,c.strength*c.boundarySoftness);let e=C(0);
 if(floor&&ceiling){const gap=div(sub(ceiling,floor),base);e=gap.value<=0||a.value===0?C(0):mul(base,div(mul(a,gap),add(gap,mul(a,2))));}else if(floor||ceiling)e=mul(base,a);
 const valueApplied=applyBoundaryJets(uv,floor,ceiling,e),guideApplied=applyBoundaryJets(ugv,floor,ceiling,e),value=valueApplied.value,guideValue=guideApplied.value;
 const constrained=c.floor!=='none'||c.ceilingMargin!==null,d=constrained?sub(div(value,base),1):u,gd=constrained?sub(div(guideValue,base),1):ug,pos=jetExp(mul(jetTanh(d),.55));
 const jets={x:X,value,base,score,guideScore:guide,rawDeviation:raw,guideDeviation:gd,guideValue,stabilizationWeight:C(smooth(c.stabilizer)),deviation:d,multiplier:add(d,1),activity:add(mul(jetTanh(d),.5),.5),positiveMultiplier:pos,positiveValue:mul(base,pos),gate,unconstrainedValue:uv,unconstrainedDeviation:u,unconstrainedGuideValue:ugv,unconstrainedGuideDeviation:ug,floorValue:floor,ceilingValue:ceiling,boundaryWidth:e};
 const hard=valueApplied.hard,bad=new Set();if(hard)for(const k of ['value','deviation','multiplier','activity','positiveMultiplier','positiveValue'])bad.add(k);if(guideApplied.hard)for(const k of ['guideValue','guideDeviation'])bad.add(k);if(scoreJump){bad.add('score');bad.add('guideScore');}
 const fields={};for(const [key,val] of Object.entries(sample)){if(typeof val!=='number')continue;const q=jets[key];const ok=q&&!bad.has(key);fields[key]=Object.freeze({value:val,d1:ok&&Number.isFinite(q.d1)?q.d1:null,d2:ok&&Number.isFinite(q.d2)?q.d2:null});}
 const valueJet=jets.value,first=fields.value.d1!==null,second=fields.value.d2!==null,okValue=first&&second,sampleSmooth=Object.values(fields).every(f=>f.d1!==null&&f.d2!==null);
 const rationale=hard?'A zero-width constraint is evaluated at its hard boundary.':scoreJump?'The score branch changes at the opening endpoint; score field derivatives are undefined there.':null;
 return Object.freeze({sample,slope:first?valueJet.d1:null,curvature:second?valueJet.d2:null,fields:Object.freeze(fields),availability:Object.freeze({slope:first,curvature:second}),differentiability:Object.freeze({value:okValue,sample:sampleSmooth,rationale})});
}
const MAX_LOG=Math.log(Number.MAX_VALUE),MIN_LOG=Math.log(Number.MIN_VALUE),MAX_X=2**40;
function rawFromLog(logBase,factor){if(factor===0)return 0;const z=logBase+Math.log(Math.abs(factor));if(!Number.isFinite(z)||z>MAX_LOG||z<MIN_LOG)return null;const v=Math.exp(z);return Number.isFinite(v)&&v!==0?(factor<0?-v:v):null;}
function logSigned(logBase,factor){return Object.freeze(factor===0?{sign:0,logAbs:null}:{sign:factor<0?-1:1,logAbs:logBase+Math.log(Math.abs(factor))});}
function logBaseline(t,c,dips){
 const terms=[];const addDip=(depth,center,width,weight)=>{if(weight===0)return;const z=(t-center)/width,lr=Math.log(weight)+Math.log(depth)-Math.log(t)-t/100-z*z;terms.push(lr<-745?0:Math.exp(lr));};
 const old=()=>{addDip(25,42,Math.sqrt(32),1);addDip(38,71,Math.sqrt(28),1);};
 if(c.introVariation>0&&t<100){const w=1-smooth((t-90)/10);addDip(25,42,Math.sqrt(32),1-w);addDip(38,71,Math.sqrt(28),1-w);for(const d of dips)addDip(d.depth,d.center,d.width,w);}else old();
 const correction=terms.reduce((a,b)=>a+b,0);if(!(correction<1))throw new RangeError('Authored baseline is not positive');return Math.log(t)+t/100+Math.log1p(-correction);
}
function numberNoise(p,s,ch){const k=Math.floor(p),t=p-k,a=2*unit(s,k,ch)-1,b=2*unit(s,k+1,ch)-1;return a+(b-a)*t*t*t*(10+t*(-15+6*t));}
function numberRamp(t){return t<=0?0:t>=1?t:t*t*t*(6+t*(-8+3*t));}
function mapFloor(y,lo,e){if(y<=lo)return lo;if(e===0||y>=lo+e)return y;return lo+e*numberRamp((y-lo)/e);}
function mapCeil(y,hi,e){if(y>=hi)return hi;if(e===0||y<=hi-e)return y;return hi-e*numberRamp((hi-y)/e);}
function record(curve,x,coordinates){
 const {c,r}=native(curve);if(typeof x!=='number'||!Number.isFinite(x)||x<1||x>MAX_X)throw new RangeError('x must lie in [1,2^40]');const h=coordinates===undefined?undefined:vector(coordinates),domain=curve.domain;
 if(domain&&x<=domain[1]){const p=h===undefined?curve.sample(x):curve.at(x,h);for(const v of Object.values(p))if(typeof v==='number'&&!Number.isFinite(v))throw new RangeError('Nonfinite native sample data');const L=Math.log(p.base),factor=p.value/p.base;return Object.freeze({...p,logBase:L,signedLogValue:Object.freeze(p.value===0?{sign:0,logAbs:null}:{sign:p.value<0?-1:1,logAbs:Math.log(Math.abs(p.value))})});}
 const clock=c.openingEndpoint===100?x:1+(x-1)*99/(c.openingEndpoint-1),L=logBaseline(clock,c,curve.dips),s=seed32(c.seed);let score=0,guideScore=0;
 if(x>c.openingEndpoint){const u=clock-100;for(const q of r){let p,g;if(h===undefined){p=q.phase+u*q.slope;g=q.guidePhase+u*q.guideSlope;}else{let projection=0;for(let i=0;i<16;i++)projection+=q.direction[i]*h[i];p=q.basePhase+projection+u/q.scale;g=q.basePhase+projection/32+u/(32*q.scale);}score+=numberNoise(p,s,q.channel)/48;guideScore+=numberNoise(g,s,q.channel)/48;}}
 const gate=smooth((x-c.openingEndpoint)/c.ramp),raw=c.strength*gate*2.2*Math.tanh(c.contrast*score),ug=c.strength*gate*.25*Math.tanh(2*guideScore),w=smooth(c.stabilizer),u=w===0?raw:w===1?ug:raw+(ug-raw)*w;
 const floor=c.floor==='none'?null:c.floor==='zero'?0:c.floor==='baseline'?1:1+ug,ceiling=c.ceilingMargin===null?null:(c.floor==='baseline'?1:1+ug)+c.ceilingMargin,a=c.strength*gate*c.boundarySoftness;
 const gap=floor!==null&&ceiling!==null?ceiling-floor:null,e=gap!==null?(gap<=0||a===0?0:a*gap/(gap+2*a)):floor===null&&ceiling===null?0:a;
 const apply=y=>{if(floor!==null&&ceiling!==null&&ceiling<=floor)return floor;const lo=floor===null?y:mapFloor(y,floor,e);return ceiling===null?lo:mapCeil(lo,ceiling,e);};
 const unconstrained=1+u,unconstrainedGuide=1+ug,valueFactor=apply(unconstrained),guideFactor=apply(unconstrainedGuide),deviation=valueFactor-1,guideDeviation=guideFactor-1,positiveMultiplier=Math.exp(.55*Math.tanh(deviation));
 const boundaryWidthRatio=e,base=rawFromLog(L,1),value=rawFromLog(L,valueFactor),guideValue=rawFromLog(L,guideFactor),unconstrainedValue=rawFromLog(L,unconstrained),unconstrainedGuideValue=rawFromLog(L,unconstrainedGuide),positiveValue=rawFromLog(L,positiveMultiplier);
 return Object.freeze({x,logBase:L,score,guideScore,gate,rawDeviation:raw,unconstrainedDeviation:u,unconstrainedGuideDeviation:ug,deviation,guideDeviation,multiplier:1+deviation,activity:.5+.5*Math.tanh(deviation),positiveMultiplier,signedLogValue:logSigned(L,valueFactor),value,base,guideValue,unconstrainedValue,unconstrainedGuideValue,positiveValue,stabilizationWeight:w,floorRatio:floor,ceilingRatio:ceiling,floorValue:floor===null?null:rawFromLog(L,floor),ceilingValue:ceiling===null?null:rawFromLog(L,ceiling),boundaryWidthRatio,boundaryWidth:rawFromLog(L,boundaryWidthRatio),constraintApplied:valueFactor!==unconstrained});
}
export function nativeNormalized(curve,x,h){return record(curve,x,h);}
