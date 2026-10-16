import * as I from './vixen-interval.mjs';
import {nextCrossing,makeLipschitzEnclosure,calibrateOccupancy,validateOccupancy,restoreOccupancy,validateCorrelationMatrix,createCorrelatedChannels,measureChannelCorrelation} from './vixen-analysis.mjs';
export {nextCrossing,makeLipschitzEnclosure,calibrateOccupancy,validateOccupancy,restoreOccupancy,validateCorrelationMatrix,createCorrelatedChannels,measureChannelCorrelation};
import {leafJet,nativeNormalized,jet,jetSmooth5} from './vixen-calculus.mjs';
import {blendJets} from './vixen-jet-composition.mjs';
import {decorateCurve,registerCurveDecorator,scopeCurveEnclosure,ownsCurveEnclosure,queryControls,registerRootCurve,GRAPH_ALGORITHM,restoreCurveGraph} from './vixen-api.mjs';
import {withImpulse,withRedistribution,couple,perturbationJet,restoreOperation,OPERATION_ALGORITHM} from './vixen-operations.mjs';
import {historyWitness} from './vixen-checkpoint.mjs';
import {append as historyAppend, before as historyBefore, entries as historyEntries} from './vixen-timeline.mjs';
/** Deterministic progression fields and immutable timelines. */
import {withInfluence,restoreInfluence,INFLUENCE_ALGORITHM,INFLUENCE_ALGORITHM_V2,INFLUENCE_ALGORITHM_V3,isInfluenceAlgorithm} from './vixen-influence.mjs';
export {INFLUENCE_ALGORITHM,INFLUENCE_ALGORITHM_V2,INFLUENCE_ALGORITHM_V3};
export {polynomialGuide,taylorGuide,sampledGuide,captureGuide,compileGuide,evaluateGuide} from './vixen-guide.mjs';
import {Wide,WIDE_FORMAT} from './vixen-wide.mjs';
import {createExtended,restoreExtended,EXTENDED_ALGORITHM,EXTENDED_TAIL_ALGORITHM,MAX_DOMAIN_END} from './vixen-extended.mjs';
import {coupleExtended,restoreExtendedCoupling,EXTENDED_COUPLING_ALGORITHM} from './vixen-extended-coupling.mjs';
export {coupleExtended,EXTENDED_COUPLING_ALGORITHM};
import {extendedPolynomialGuide,withExtendedInfluence,withExtendedImpulse,withExtendedRedistribution,restoreExtendedOperation,EXTENDED_OPERATION_ALGORITHM} from './vixen-extended-operations.mjs';
export {extendedPolynomialGuide,withExtendedInfluence,withExtendedImpulse,withExtendedRedistribution,restoreExtendedOperation,EXTENDED_OPERATION_ALGORITHM};
export {Wide,WIDE_FORMAT,createExtended,restoreExtended,EXTENDED_ALGORITHM,EXTENDED_TAIL_ALGORITHM,MAX_DOMAIN_END};
export {wideExp} from './vixen-exponential.mjs';
export const ALGORITHM='vixen-hyperfield-c2/4';
export const COHERENT_ALGORITHM='vixen-hyperfield-c2/5';
export const COHERENT_TAIL_ALGORITHM='vixen-tail-c2/3';
export const DIMENSIONS=16,FEATURES=48;
export const DEFAULTS=Object.freeze({seed:'vixen',openingEndpoint:100,introVariation:0,floor:'none',ceilingMargin:null,boundarySoftness:.1,stabilizer:0,strength:1,ramp:16,tempo:.8,contrast:10,latent:Object.freeze(Array(16).fill(0)),velocity:Object.freeze(Array(16).fill(0))});
export function smooth5(t){if(t<=0)return 0;if(t>=1)return 1;return t*t*t*(10+t*(-15+6*t));}
export function boundaryRamp(t){if(typeof t!=='number'||!Number.isFinite(t))throw new RangeError('Finite boundary coordinate required');return t<=0?0:t>=1?t:t*t*t*(6+t*(-8+3*t));}
export function bump(t){if(Math.abs(t)>=1)return 0;const v=1-t*t;return v*v*v;}
export function seed32(seed){if(typeof seed==='number'){if(!Number.isInteger(seed)||seed<0||seed>0xffffffff)throw new RangeError('Numeric seed must be uint32');return seed>>>0;}if(typeof seed!=='string')throw new TypeError('Seed must be text or uint32');if(seed.length>1024)throw new RangeError('Text seed limit: 1024 UTF-16 code units');let h=2166136261;for(const b of new TextEncoder().encode(seed))h=Math.imul(h^b,16777619)>>>0;return h;}
export function hash32(seed,index,channel){let h=(seed^Math.imul(index>>>0,0x9e3779b9)^Math.imul(channel>>>0,0x85ebca6b))>>>0;h=Math.imul(h^(h>>>16),0x7feb352d)>>>0;h=Math.imul(h^(h>>>15),0x846ca68b)>>>0;return (h^(h>>>16))>>>0;}
const leafAt=new WeakMap();
const unit=(s,k,c)=>hash32(s,k,c)/4294967296;
const dot=(a,b)=>{let n=0;for(let j=0;j<16;j++)n+=a[j]*b[j];return n;};
function normal(a){const n=Math.sqrt(dot(a,a));if(n===0)return Array.from({length:16},(_,i)=>i===0?1:0);return a.map(v=>v/n);}
function vector(a,limit=1000000){if(!Array.isArray(a)&&!ArrayBuffer.isView(a))throw new TypeError('A vector must contain 16 numbers');if(a.length!==16)throw new RangeError('A vector must contain exactly 16 coordinates');const v=Array.from(a);if(v.some(n=>typeof n!=='number'||!Number.isFinite(n)||Math.abs(n)>limit))throw new RangeError(`Coordinates must be finite in [-${limit},${limit}]`);return v;}
function coordinate(x){if(typeof x!=='number'||!Number.isFinite(x)||x<1||x>50000)throw new RangeError('x must be finite in [1,50000]');}
export function baseline(x){return x*Math.exp(x/100)-25*Math.exp(-((x-42)**2)/32)-38*Math.exp(-((x-71)**2)/28);}
export function navigationBasis(index=0){if(!Number.isInteger(index)||index<0||index>100000)throw new RangeError('Plane index must be an integer in [0,100000]');let u,v;if(index===0){u=Array.from({length:16},(_,j)=>j===0?1:0);v=Array.from({length:16},(_,j)=>j===1?1:0);}else{const seed=seed32('vixen-navigation');u=normal(Array.from({length:16},(_,j)=>2*unit(seed,index,100+j)-1));const raw=Array.from({length:16},(_,j)=>2*unit(seed,index,200+j)-1),proj=dot(raw,u);v=raw.map((n,j)=>n-proj*u[j]);if(dot(v,v)<1e-20){const k=u.reduce((best,n,j)=>Math.abs(n)<Math.abs(u[best])?j:best,0);v=Array.from({length:16},(_,j)=>(j===k?1:0)-u[k]*u[j]);}v=normal(v);}return Object.freeze({u:Object.freeze(u),v:Object.freeze(v)});}
function createLeaf(options={},algorithm=ALGORITHM){
 if(!options||typeof options!=='object'||Array.isArray(options))throw new TypeError('Config must be an object');for(const key of Object.keys(options))if(!Object.hasOwn(DEFAULTS,key))throw new TypeError('Unknown config key: '+key);
 const c={...DEFAULTS,...options};if(!['none','zero','baseline','backbone'].includes(c.floor))throw new RangeError('Invalid floor');if(c.ceilingMargin!==null&&(typeof c.ceilingMargin!=='number'||!Number.isFinite(c.ceilingMargin)||c.ceilingMargin<0||c.ceilingMargin>16))throw new RangeError('ceilingMargin must be null or in [0,16]');const seed=seed32(c.seed);for(const [key,lo,hi] of [['openingEndpoint',2,50000],['introVariation',0,1],['boundarySoftness',0,1],['stabilizer',0,1],['strength',0,2],['ramp',1,64],['tempo',.25,4],['contrast',0,16]])if(typeof c[key]!=='number'||!Number.isFinite(c[key])||c[key]<lo||c[key]>hi)throw new RangeError(`${key} must lie in [${lo},${hi}]`);
 c.latent=Object.freeze(vector(c.latent,128));c.velocity=Object.freeze(vector(c.velocity,4));const config=Object.freeze(c),stabilizationWeight=smooth5(c.stabilizer);
 const clock=x=>c.openingEndpoint===100?x:1+(x-1)*99/(c.openingEndpoint-1);
 const domain=Object.freeze([1,Math.min(50000,1+(50000-1)*(c.openingEndpoint-1)/99)]);
 function check(x){coordinate(x);if(x>domain[1])throw new RangeError('x exceeds the canonical clock domain');}
 const v=c.introVariation,dips=Object.freeze([{center:42+v*(32+15*unit(seed,0,700)-42),width:Math.sqrt(32+v*((4+2*unit(seed,0,701))**2-32)),depth:25+v*(18+6*unit(seed,0,702)-25)},{center:71+v*(62+20*unit(seed,1,700)-71),width:Math.sqrt(28+v*((4+3*unit(seed,1,701))**2-28)),depth:38+v*(35+13*unit(seed,1,702)-38)}].map(Object.freeze));
 function authored(t){const old=baseline(t);if(c.introVariation===0||t>=100)return old;const fresh=t*Math.exp(t/100)-dips.reduce((sum,d)=>sum+d.depth*Math.exp(-(((t-d.center)/d.width)**2)),0),weight=1-smooth5((t-90)/10);return old+(fresh-old)*weight;}
 const ridges=[];for(let i=0;i<FEATURES;i++){const direction=Object.freeze(normal(Array.from({length:16},(_,j)=>2*unit(seed,i,20+j)-1))),scale=(4+76*unit(seed,i,1))*c.tempo,basePhase=unit(seed,i,2)*1024,phase=basePhase+dot(direction,c.latent),slope=1/scale+dot(direction,c.velocity)/100;ridges.push(Object.freeze({index:i,direction,scale,basePhase,phase,slope,guidePhase:basePhase+dot(direction,c.latent)/32,guideSlope:slope/32,channel:i+200}));}
 function noise(p,channel){const k=Math.floor(p),t=p-k,a=2*unit(seed,k,channel)-1,b=2*unit(seed,k+1,channel)-1;return a+(b-a)*smooth5(t);}
 function floorMap(y,lo,e){if(y<=lo)return lo;if(e===0||y>=lo+e)return y;const t=(y-lo)/e;return lo+e*boundaryRamp(t);}
 function ceilMap(y,hi,e){if(y>=hi)return hi;if(e===0||y<=hi-e)return y;const t=(hi-y)/e;return hi-e*boundaryRamp(t);}
 function limits(base,guide,gate){
 const floorValue=c.floor==='none'?null:c.floor==='zero'?0:c.floor==='baseline'?base:guide,ceilingValue=c.ceilingMargin===null?null:(c.floor==='baseline'?base:guide)+c.ceilingMargin*base,a=c.strength*gate*c.boundarySoftness,gap=floorValue!==null&&ceilingValue!==null?(ceilingValue-floorValue)/base:null,boundaryWidth=gap!==null?(gap<=0||a===0?0:base*(a*gap/(gap+2*a))):(floorValue===null&&ceilingValue===null?0:base*a);
 return {floorValue,ceilingValue,boundaryWidth};
 }
 function apply(y,lim){if(lim.floorValue!==null&&lim.ceilingValue!==null&&lim.ceilingValue<=lim.floorValue)return lim.floorValue;const lo=lim.floorValue===null?y:floorMap(y,lim.floorValue,lim.boundaryWidth);return lim.ceilingValue===null?lo:ceilMap(lo,lim.ceilingValue,lim.boundaryWidth);}
 function result(x,score,guideScore){
 const gate=smooth5((x-c.openingEndpoint)/c.ramp),rawDeviation=c.strength*gate*2.2*Math.tanh(c.contrast*score),unconstrainedGuideDeviation=c.strength*gate*.25*Math.tanh(2*guideScore),w=stabilizationWeight,u=w===0?rawDeviation:w===1?unconstrainedGuideDeviation:rawDeviation+(unconstrainedGuideDeviation-rawDeviation)*w,base=authored(clock(x)),unconstrainedValue=base+base*u,unconstrainedGuideValue=base+base*unconstrainedGuideDeviation,lim=limits(base,unconstrainedGuideValue,gate),value=apply(unconstrainedValue,lim),guideValue=apply(unconstrainedGuideValue,lim),deviation=c.floor==='none'&&c.ceilingMargin===null?u:(value-base)/base,guideDeviation=c.floor==='none'&&c.ceilingMargin===null?unconstrainedGuideDeviation:(guideValue-base)/base,positiveMultiplier=Math.exp(.55*Math.tanh(deviation));
 return Object.freeze({x,value,base,score,guideScore,rawDeviation,guideDeviation,guideValue,stabilizationWeight:w,deviation,multiplier:1+deviation,activity:.5+.5*Math.tanh(deviation),positiveMultiplier,positiveValue:base*positiveMultiplier,gate,unconstrainedValue,unconstrainedDeviation:u,unconstrainedGuideValue,unconstrainedGuideDeviation,...lim,constraintApplied:value!==unconstrainedValue});
 }
 function sample(x){check(x);let score=0,guideScore=0;if(x>c.openingEndpoint){const u=clock(x)-100;for(const r of ridges){score+=noise(r.phase+u*r.slope,r.channel)/FEATURES;guideScore+=noise(r.guidePhase+u*r.guideSlope,r.channel)/FEATURES;}}return result(x,score,guideScore);}
 function atValidated(x,latent){check(x);let score=0,guideScore=0;if(x>c.openingEndpoint){const u=clock(x)-100;for(const r of ridges){const projection=dot(r.direction,latent);score+=noise(r.basePhase+projection+u/r.scale,r.channel)/FEATURES;guideScore+=noise(r.basePhase+projection/32+u/(32*r.scale),r.channel)/FEATURES;}}return result(x,score,guideScore);}
 function at(x,h){return atValidated(x,vector(h));}
 function location(x){check(x);const t=Math.max(0,clock(x)-100)/100;return Object.freeze(c.latent.map((h,i)=>h+t*c.velocity[i]));}
 function inspect(x){check(x);if(x<=c.openingEndpoint)return Object.freeze([]);const u=clock(x)-100;return Object.freeze(ridges.map(r=>{const p=r.phase+u*r.slope;return Object.freeze({index:r.index,scale:r.scale,coordinate:p,cell:Math.floor(p),contribution:noise(p,r.channel)/FEATURES,guideContribution:noise(r.guidePhase+u*r.guideSlope,r.channel)/FEATURES});}).sort((a,b)=>Math.abs(b.contribution)-Math.abs(a.contribution)));}
 const envelope=(2.2*(1-stabilizationWeight)+.25*stabilizationWeight)*c.strength;
 const lower=c.floor==='none'?-envelope:c.floor==='zero'?-1:c.floor==='baseline'?0:-.25*c.strength;
 const upper=c.ceilingMargin===null?envelope:(c.floor==='baseline'?0:.25*c.strength)+c.ceilingMargin;
 const unconstrainedSpatialSlope=c.strength*((1-stabilizationWeight)*33*c.contrast/4+stabilizationWeight*15/256);
 const bounds=Object.freeze({unconstrainedSpatialSlope,score:Object.freeze([-1,1]),deviation:Object.freeze([lower,upper]),multiplier:Object.freeze([1+lower,1+upper]),activity:Object.freeze([0,1]),positiveMultiplier:Object.freeze([Math.exp(-.55),Math.exp(.55)])});
 const leaf=Object.freeze({algorithm,config,bounds,domain,dips,sample,at,location,inspect,ridges:()=>Object.freeze(ridges.slice())});leafAt.set(leaf,atValidated);return leaf;
}

function forkSeed(parentSeed,operation,at){
 const bits=new DataView(new ArrayBuffer(8));bits.setFloat64(0,at,false);
 return hash32(hash32(seed32(parentSeed),typeof parentSeed==='string'?1:2,seed32(operation)),bits.getUint32(0,false),bits.getUint32(4,false));
}
const MIX_FIELDS=Object.freeze(['x','value','base','score','guideScore','rawDeviation','guideValue','stabilizationWeight','gate','unconstrainedValue','unconstrainedGuideValue','boundaryWidth']);
function samplePolicy(y,p){
 const e=p.boundaryWidth,lo=p.floorValue,hi=p.ceilingValue,r=t=>t<=0?0:t>=1?t:t*t*t*(6+t*(-8+3*t));
 if(lo!==null&&hi!==null&&hi<=lo)return lo;
 if(lo!==null)y=y<=lo?lo:e===0||y>=lo+e?y:lo+e*r((y-lo)/e);
 if(hi!==null)y=y>=hi?hi:e===0||y<=hi-e?y:hi-e*r((hi-y)/e);
 return y;
}
function mixSamples(a,b,w,coherent=false){
 const r={...a};for(const key of MIX_FIELDS)r[key]=a[key]+(b[key]-a[key])*w;
 for(const key of ['floorValue','ceilingValue'])if(a[key]!==null)r[key]=a[key]+(b[key]-a[key])*w;
 // Blend actual constrained values and their references, then derive every adapter afresh.
 if(coherent){r.value=samplePolicy(r.unconstrainedValue,r);r.guideValue=samplePolicy(r.unconstrainedGuideValue,r);}
 else{if(r.floorValue!==null)r.value=Math.max(r.value,r.floorValue);
 if(r.ceilingValue!==null)r.value=Math.min(r.value,r.ceilingValue);}
 r.deviation=(r.value-r.base)/r.base;r.multiplier=1+r.deviation;r.activity=.5+.5*Math.tanh(r.deviation);
 r.positiveMultiplier=Math.exp(.55*Math.tanh(r.deviation));r.positiveValue=r.base*r.positiveMultiplier;
 r.guideDeviation=(r.guideValue-r.base)/r.base;r.unconstrainedDeviation=(r.unconstrainedValue-r.base)/r.base;
 r.unconstrainedGuideDeviation=(r.unconstrainedGuideValue-r.base)/r.base;r.constraintApplied=r.value!==r.unconstrainedValue;
 return Object.freeze(r);
}
const timelineStates=new WeakMap();
export const TAIL_ALGORITHM='vixen-tail-c2/2';
function timeline(leaf,rootConfig=leaf.config,tree=null,rootLeaf=leaf,last=null,checkpoint=null){
 let lineageCache;const coherent=leaf.algorithm===COHERENT_ALGORITHM;
 function route(x){
  if(!last||x>=last.entry.at+last.entry.handoffWidth)return {base:leaf,active:[]};
  const active=[];let base=rootLeaf;
  for(const event of historyBefore(tree,x)){
   if(x>=event.entry.at+event.entry.handoffWidth){base=event.leaf;break;}
   active.push(event);
  }
  return {base,active};
 }
 function evaluate(x,h){
  coordinate(x);if(x<(checkpoint?.from??1)||x>leaf.domain[1])throw new RangeError('x exceeds the canonical clock domain');
  const {base,active}=route(x);
  const sample=l=>h===undefined?l.sample(x):leafAt.get(l)(x,h);
  let p=sample(base);
  for(let i=active.length-1;i>=0;i--){const e=active[i],w=smooth5((x-e.entry.at)/e.entry.handoffWidth);p=mixSamples(p,sample(e.leaf),w,coherent);}
  return p;
 }
 function inspect(x){
  coordinate(x);if(x<(checkpoint?.from??1)||x>leaf.domain[1])throw new RangeError('x exceeds the canonical clock domain');
  const {base,active}=route(x);let rows=base.inspect(x),sourceSeed=base.config.seed;
  for(let i=active.length-1;i>=0;i--){
   const e=active[i],w=smooth5((x-e.entry.at)/e.entry.handoffWidth);
   rows=Object.freeze([...rows.map(r=>Object.freeze({...r,sourceSeed:r.sourceSeed??sourceSeed,contribution:r.contribution*(1-w),guideContribution:r.guideContribution*(1-w)})),...e.leaf.inspect(x).map(r=>Object.freeze({...r,sourceSeed:e.leaf.config.seed,contribution:r.contribution*w,guideContribution:r.guideContribution*w}))].sort((a,b)=>Math.abs(b.contribution)-Math.abs(a.contribution)));
   sourceSeed=e.leaf.config.seed;
  }
  return rows;
 }
 const normalizedFacade=Object.freeze({config:leaf.config,dips:leaf.dips,ridges:leaf.ridges,domain:leaf.domain,sample:x=>evaluate(x),at:(x,h)=>evaluate(x,vector(h))});
 function sampleNormalized(x,h){
  if(typeof x!=='number'||!Number.isFinite(x)||x<(checkpoint?.from??1)||x>2**40)throw new RangeError('Normalized coordinate outside its finite domain');
  return nativeNormalized(normalizedFacade,x,h);
 }
 function jetAt(x,h){
  coordinate(x);if(x<(checkpoint?.from??1))throw new RangeError('x precedes the available checkpoint');
  const coords=h===undefined?undefined:vector(h),selection=route(x);
  let out=leafJet(selection.base,x,coords);
  for(let i=selection.active.length-1;i>=0;i--){
   const event=selection.active[i],b=leafJet(event.leaf,x,coords),weight=jetSmooth5(jet((x-event.entry.at)/event.entry.handoffWidth,1/event.entry.handoffWidth,0));
   out=blendJets(out,b,weight,mixSamples(out.sample,b.sample,weight.value,coherent),coherent);
  }
  return out;
 }
 let queryKnots=null;
 function enclosure(lo,hi,h){
  coordinate(lo);coordinate(hi);if(lo<(checkpoint?.from??1)||hi>leaf.domain[1])throw new RangeError('Enclosure outside available domain');if(lo>hi)throw new RangeError('Enclosure bounds must be ordered');
  const coords=h===undefined?undefined:vector(h);
  if(queryKnots===null)queryKnots=[...new Set([leaf.config.openingEndpoint,...Array.from(historyEntries(tree)).flatMap(n=>[n.entry.at,n.entry.at+n.entry.handoffWidth])])].sort((a,b)=>a-b);
  let low=0,high=queryKnots.length;while(low<high){const m=(low+high)>>>1;if(queryKnots[m]<=lo)low=m+1;else high=m;}
  const cuts=[lo];for(let j=low;j<queryKnots.length&&queryKnots[j]<hi;j++)cuts.push(queryKnots[j]);if(hi!==lo)cuts.push(hi);
  let result=null;
  for(let j=0;j<Math.max(1,cuts.length-1);j++){
   const a=cuts[j],b=cuts[j+1]??a,{base,active}=route(a/2+b/2),memo=new Map();let out;
   if(b<=leaf.config.openingEndpoint)out=I.interval(0);
   else if(active.length===0)out=I.nativeDeviationEnclosure(base,a,b,coords);
   else{
    const native=c=>{if(!memo.has(c))memo.set(c,I.nativeConstraintState(c,a,b,coords));return memo.get(c);};
    let state=native(base);
    for(let k=active.length-1;k>=0;k--){const n=active[k],w=I.smooth5(I.divide(I.subtract(I.interval(a,b),I.interval(n.entry.at)),I.interval(n.entry.handoffWidth)));state=I.mixConstraintStates(state,native(n.leaf),w,coherent);}
    out=coherent?I.constraintDeviation(state):I.appliedDeviation(state);
   }
   result=result===null?out:I.hull(result,out);
  }
  return Object.freeze([result.lo,result.hi]);
 }
 function lineage(){return lineageCache??=Object.freeze([...historyEntries(tree)].map(e=>e.entry));}
 function snapshot(){const rows=Object.freeze(lineage().map(({at,operation,handoffWidth})=>Object.freeze({at,operation,handoffWidth})));return Object.freeze(checkpoint?{...checkpoint,lineage:rows}:{algorithm:leaf.algorithm,config:rootConfig,lineage:rows});}
 function fork(options){
  if(!options||typeof options!=='object'||Array.isArray(options)||Reflect.ownKeys(options).some(k=>!['at','operation','handoffWidth'].includes(k)))throw new TypeError('Fork options: at, operation, handoffWidth');
  const {at,operation}=options;coordinate(at);if(at<(checkpoint?.from??1)||at>leaf.domain[1])throw new RangeError('x exceeds the canonical clock domain');
  if(typeof operation!=='string'||operation.length===0)throw new TypeError('A nonempty operation string is required');seed32(operation);
  if(last&&at<last.entry.at)throw new RangeError('Nested forks cannot precede their parent fork');
  const handoffWidth=options.handoffWidth??Math.min(16,leaf.domain[1]-at);
  if(typeof handoffWidth!=='number'||!Number.isFinite(handoffWidth)||handoffWidth<=0||at+handoffWidth<=at||at+handoffWidth>leaf.domain[1])throw new RangeError('Handoff must have positive representable width inside the domain');
  const seed=forkSeed(leaf.config.seed,operation,at),entry=Object.freeze({at,operation,handoffWidth,seed}),future=createLeaf({...leaf.config,seed},leaf.algorithm),event=Object.freeze({entry,leaf:future});
  return timeline(future,rootConfig,historyAppend(tree,event),rootLeaf,event,checkpoint);
 }
 const api=Object.freeze({...leaf,algorithm:checkpoint?checkpoint.algorithm:leaf.algorithm,domain:checkpoint?Object.freeze([checkpoint.from,leaf.domain[1]]):leaf.domain,encloseDeviation:enclosure,nextCrossing:(threshold,from,horizon,options={})=>nextCrossing(api,threshold,from,horizon,queryControls(api,from,options)),normalizedDomain:Object.freeze([checkpoint?.from??1,2**40]),sampleNormalized:x=>sampleNormalized(x),atNormalized:(x,h)=>sampleNormalized(x,vector(h)),jet:jetAt,slope:(x,h)=>jetAt(x,h).slope,curvature:(x,h)=>jetAt(x,h).curvature,get lineage(){return lineage();},sample:x=>evaluate(x),at:(x,h)=>evaluate(x,vector(h)),location:x=>{coordinate(x);if(x<(checkpoint?.from??1))throw new RangeError('x precedes checkpoint');return leaf.location(x);},inspect,fork,snapshot,tail:from=>tailSnapshot(api,from),influence:(target,options)=>withInfluence(api,target,options)});
 registerRootCurve(api,{domain:api.domain,latest:last?.entry.at??1});timelineStates.set(api,{leaf,rootConfig,tree,rootLeaf,last,checkpoint});scopeCurveEnclosure(api,api.encloseDeviation);return decorateCurve(api);
}
function tailSnapshot(curve,from){
 const s=timelineStates.get(curve);coordinate(from);
 if(from<curve.domain[0]||from>curve.domain[1])throw new RangeError('Checkpoint must lie inside the available domain');
 const events=[...historyEntries(s.tree)];let cut=-1;
 for(let i=0;i<events.length;i++)if(events[i].entry.at+events[i].entry.handoffWidth<=from)cut=i;
 const folded=events.slice(0,cut+1).map(e=>e.entry),base=cut<0?s.rootLeaf:events[cut].leaf,prior=s.checkpoint?.witness??null;
 const witness=folded.length||prior===null?historyWitness(prior,folded):prior;
 return Object.freeze({algorithm:s.leaf.algorithm===COHERENT_ALGORITHM?COHERENT_TAIL_ALGORITHM:TAIL_ALGORITHM,from,config:s.rootConfig,seed:base.config.seed,leafSeed:seed32(base.config.seed),foldedCount:(s.checkpoint?.foldedCount??0)+folded.length,witness,lineage:Object.freeze(events.slice(cut+1).map(({entry:{at,operation,handoffWidth}})=>Object.freeze({at,operation,handoffWidth})))});
}
function restoreTail(snapshot){
 const keys=['algorithm','from','config','seed','leafSeed','foldedCount','witness','lineage'];
 if(!snapshot||typeof snapshot!=='object'||Array.isArray(snapshot)||Reflect.ownKeys(snapshot).some(k=>!keys.includes(k))||keys.some(k=>!Object.hasOwn(snapshot,k)))throw new TypeError('Invalid checkpoint fields');
 const algorithm=snapshot.algorithm===COHERENT_TAIL_ALGORITHM?COHERENT_ALGORITHM:ALGORITHM;
 coordinate(snapshot.from);const cfg=createVersion(snapshot.config,algorithm);
 if(snapshot.from>cfg.domain[1]||typeof snapshot.witness!=='string'||! /^[0-9a-f]{64}$/.test(snapshot.witness)||!Number.isSafeInteger(snapshot.foldedCount)||snapshot.foldedCount<0||!Number.isInteger(snapshot.leafSeed)||snapshot.leafSeed<0||snapshot.leafSeed>0xffffffff||seed32(snapshot.seed)!==snapshot.leafSeed||!Array.isArray(snapshot.lineage))throw new TypeError('Invalid checkpoint data');
 if(snapshot.foldedCount===0&&snapshot.seed!==cfg.config.seed)throw new TypeError('Unfolded checkpoint must preserve the typed root seed');
 if(snapshot.foldedCount>0&&typeof snapshot.seed!=='number')throw new TypeError('Folded checkpoint seed must be uint32');
 let current=createVersion({...snapshot.config,seed:snapshot.seed},algorithm);
 for(const entry of snapshot.lineage)current=current.fork(entry);
 const state=timelineStates.get(current),checkpoint=Object.freeze({...snapshot,config:cfg.config,lineage:undefined});
 return timeline(state.leaf,cfg.config,state.tree,state.rootLeaf,state.last,checkpoint);
}

function createVersion(options,algorithm){return timeline(createLeaf(options,algorithm));}
export function create(options={}){return createVersion(options,ALGORITHM);}
export function createCoherent(options={}){return createVersion(options,COHERENT_ALGORITHM);}
export function restore(snapshot){return restoreFrame(snapshot,restore);}
function restoreFrame(snapshot,read){
 if(snapshot?.algorithm===EXTENDED_COUPLING_ALGORITHM)return restoreExtendedCoupling(snapshot,read);
 if(snapshot&&snapshot.algorithm===EXTENDED_OPERATION_ALGORITHM)return restoreExtendedOperation(snapshot,read);
 if(snapshot&&(snapshot.algorithm===EXTENDED_ALGORITHM||snapshot.algorithm===EXTENDED_TAIL_ALGORITHM))return restoreExtended(snapshot);
 if(snapshot?.algorithm===GRAPH_ALGORITHM)return restoreCurveGraph(snapshot,restoreFrame);
 if(snapshot&&typeof snapshot==='object'&&Object.hasOwn(snapshot,'algorithm')&&snapshot.algorithm===OPERATION_ALGORITHM)return restoreOperation(snapshot,read);
 if(snapshot&&typeof snapshot==='object'&&Object.hasOwn(snapshot,'algorithm')&&[TAIL_ALGORITHM,COHERENT_TAIL_ALGORITHM].includes(snapshot.algorithm))return restoreTail(snapshot);
 if(snapshot&&typeof snapshot==='object'&&Object.hasOwn(snapshot,'algorithm')&&isInfluenceAlgorithm(snapshot.algorithm))return restoreInfluence(snapshot,read);
 if(!snapshot||typeof snapshot!=='object'||Array.isArray(snapshot)||!Object.hasOwn(snapshot,'algorithm')||!Object.hasOwn(snapshot,'config')||!snapshot.config||typeof snapshot.config!=='object'||Array.isArray(snapshot.config))throw new TypeError('Snapshot with configuration required');
 if(Reflect.ownKeys(snapshot).some(k=>typeof k!=='string'||!['algorithm','config','lineage'].includes(k)))throw new TypeError('Unknown snapshot field');
 if([ALGORITHM,COHERENT_ALGORITHM].includes(snapshot.algorithm)){
  const lineage=(Object.hasOwn(snapshot,'lineage')?snapshot.lineage:[])??[];if(!Array.isArray(lineage))throw new RangeError('Invalid branch lineage');
  let result=createVersion(snapshot.config,snapshot.algorithm);for(const entry of lineage)result=result.fork(entry);return result;
 }
 if(['vixen-hyperfield-c2/1','vixen-hyperfield-c2/2','vixen-hyperfield-c2/3'].includes(snapshot.algorithm)){
  if(snapshot.lineage!==undefined||['openingEndpoint','introVariation'].some(k=>Object.hasOwn(snapshot.config,k)))throw new TypeError('Old snapshots cannot contain timeline features');
  if(snapshot.algorithm!=='vixen-hyperfield-c2/3'&&['floor','ceilingMargin','boundarySoftness'].some(k=>Object.hasOwn(snapshot.config,k)))throw new TypeError('Old snapshots cannot contain constraints');
  if(snapshot.algorithm==='vixen-hyperfield-c2/1'&&Object.hasOwn(snapshot.config,'stabilizer'))throw new TypeError('Old snapshots cannot contain stabilizer');
  return create(snapshot.algorithm==='vixen-hyperfield-c2/3'?snapshot.config:{...snapshot.config,floor:'none',ceilingMargin:null});
 }
 throw new Error('Algorithm mismatch: use the matching archived sampler');
}

const completedCurves=new WeakMap();
function completeCurve(api){
 if(!api||!api.config||typeof api.sample!=='function')return api;
 if(completedCurves.has(api))return completedCurves.get(api);
 const descriptors=Object.getOwnPropertyDescriptors(api);
 let result;
 const normalized=x=>Object.freeze({...x,logBase:Math.log(x.base),signedLogValue:Object.freeze({sign:Math.sign(x.value)||0,logAbs:x.value===0?null:Math.log(Math.abs(x.value))}),floorRatio:x.floorValue===null?null:x.floorValue/x.base,ceilingRatio:x.ceilingValue===null?null:x.ceilingValue/x.base,boundaryWidthRatio:x.boundaryWidth/x.base});
 const methods={
  nextCrossing:(threshold,from,horizon,options={})=>nextCrossing(result,threshold,from,horizon,queryControls(result,from,options)),
  impulse:(at,magnitude,relaxation)=>{
   const options=at!==null&&typeof at==='object'?at:{at,magnitude,relaxation};
   return withImpulse(result,options,restore);
  },
  redistribute:(window,shape)=>withRedistribution(result,window,shape,restore),
  sampleRange:(x0,x1,count=129)=>{
   if(!Number.isInteger(count)||count<2||count>100001)throw new RangeError('Sample count must be an integer in [2,100001]');
   for(const x of [x0,x1])if(typeof x!=='number'||!Number.isFinite(x)||x<api.domain[0]||x>api.domain[1])throw new RangeError('Range endpoints must lie in the available domain');
   return Object.freeze(Array.from({length:count},(_,i)=>api.sample(i===count-1?x1:x0+(x1-x0)*i/(count-1))));
  }
 };
 if(typeof api.sampleNormalized!=='function'){methods.sampleNormalized=x=>normalized(api.sample(x));methods.atNormalized=(x,h)=>normalized(api.at(x,h));descriptors.normalizedDomain={value:api.domain,enumerable:true};}
 for(const name of ['encloseDeviation','enclose','enclosureProvider','enclosePoint'])if(descriptors[name]&&!ownsCurveEnclosure(api,descriptors[name].value))delete descriptors[name];
 // Copy descriptors rather than evaluating lazy lineage getters on every fork.
 for(const [name,value]of Object.entries(methods))descriptors[name]={value,enumerable:true};
 result=Object.freeze(Object.defineProperties({},descriptors));
 completedCurves.set(api,result);completedCurves.set(result,result);
 const state=timelineStates.get(api);if(state)timelineStates.set(result,state);
 return result;
}
registerCurveDecorator(completeCurve);
export {couple,perturbationJet,OPERATION_ALGORITHM,GRAPH_ALGORITHM};
