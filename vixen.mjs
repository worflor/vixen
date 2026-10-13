/** Vixen: continuous seeded 16-D landscape. Dependency-free, random-access.
 * Save algorithm + full config. Float curves are not cross-libm bitwise lockstep.
 */
export const ALGORITHM='vixen-hyperfield-c2/4';
export const DIMENSIONS=16,FEATURES=48;
export const DEFAULTS=Object.freeze({seed:'vixen',openingEndpoint:100,introVariation:0,floor:'none',ceilingMargin:null,boundarySoftness:.1,stabilizer:0,strength:1,ramp:16,tempo:.8,contrast:10,latent:Object.freeze(Array(16).fill(0)),velocity:Object.freeze(Array(16).fill(0))});
export function smooth5(t){if(t<=0)return 0;if(t>=1)return 1;return t*t*t*(10+t*(-15+6*t));}
export function boundaryRamp(t){if(typeof t!=='number'||!Number.isFinite(t))throw new RangeError('Finite boundary coordinate required');return t<=0?0:t>=1?t:t*t*t*(6+t*(-8+3*t));}
export function bump(t){if(Math.abs(t)>=1)return 0;const v=1-t*t;return v*v*v;}
export function seed32(seed){if(typeof seed==='number'){if(!Number.isInteger(seed)||seed<0||seed>0xffffffff)throw new RangeError('Numeric seed must be uint32');return seed>>>0;}if(typeof seed!=='string')throw new TypeError('Seed must be text or uint32');if(seed.length>1024)throw new RangeError('Text seed limit: 1024 UTF-16 code units');let h=2166136261;for(const b of new TextEncoder().encode(seed))h=Math.imul(h^b,16777619)>>>0;return h;}
export function hash32(seed,index,channel){let h=(seed^Math.imul(index>>>0,0x9e3779b9)^Math.imul(channel>>>0,0x85ebca6b))>>>0;h=Math.imul(h^(h>>>16),0x7feb352d)>>>0;h=Math.imul(h^(h>>>15),0x846ca68b)>>>0;return (h^(h>>>16))>>>0;}
const unit=(s,k,c)=>hash32(s,k,c)/4294967296;
const dot=(a,b)=>{let n=0;for(let j=0;j<16;j++)n+=a[j]*b[j];return n;};
function normal(a){const n=Math.sqrt(dot(a,a));if(n===0)return Array.from({length:16},(_,i)=>i===0?1:0);return a.map(v=>v/n);}
function vector(a,limit=1000000){if(!Array.isArray(a)&&!ArrayBuffer.isView(a))throw new TypeError('A vector must contain 16 numbers');if(a.length!==16)throw new RangeError('A vector must contain exactly 16 coordinates');const v=Array.from(a);if(v.some(n=>typeof n!=='number'||!Number.isFinite(n)||Math.abs(n)>limit))throw new RangeError(`Coordinates must be finite in [-${limit},${limit}]`);return v;}
function coordinate(x){if(typeof x!=='number'||!Number.isFinite(x)||x<1||x>50000)throw new RangeError('x must be finite in [1,50000]');}
export function baseline(x){return x*Math.exp(x/100)-25*Math.exp(-((x-42)**2)/32)-38*Math.exp(-((x-71)**2)/28);}
export function navigationBasis(index=0){if(!Number.isInteger(index)||index<0||index>100000)throw new RangeError('Plane index must be an integer in [0,100000]');let u,v;if(index===0){u=Array.from({length:16},(_,j)=>j===0?1:0);v=Array.from({length:16},(_,j)=>j===1?1:0);}else{const seed=seed32('vixen-navigation');u=normal(Array.from({length:16},(_,j)=>2*unit(seed,index,100+j)-1));const raw=Array.from({length:16},(_,j)=>2*unit(seed,index,200+j)-1),proj=dot(raw,u);v=raw.map((n,j)=>n-proj*u[j]);if(dot(v,v)<1e-20){const k=u.reduce((best,n,j)=>Math.abs(n)<Math.abs(u[best])?j:best,0);v=Array.from({length:16},(_,j)=>(j===k?1:0)-u[k]*u[j]);}v=normal(v);}return Object.freeze({u:Object.freeze(u),v:Object.freeze(v)});}
function createLeaf(options={}){
 if(!options||typeof options!=='object'||Array.isArray(options))throw new TypeError('Config must be an object');for(const key of Object.keys(options))if(!Object.hasOwn(DEFAULTS,key))throw new TypeError('Unknown config key: '+key);
 const c={...DEFAULTS,...options};if(!['none','zero','baseline','backbone'].includes(c.floor))throw new RangeError('Invalid floor');if(c.ceilingMargin!==null&&(typeof c.ceilingMargin!=='number'||!Number.isFinite(c.ceilingMargin)||c.ceilingMargin<0||c.ceilingMargin>16))throw new RangeError('ceilingMargin must be null or in [0,16]');const seed=seed32(c.seed);for(const [key,lo,hi] of [['openingEndpoint',2,50000],['introVariation',0,1],['boundarySoftness',0,1],['stabilizer',0,1],['strength',0,2],['ramp',1,64],['tempo',.25,4],['contrast',0,16]])if(typeof c[key]!=='number'||!Number.isFinite(c[key])||c[key]<lo||c[key]>hi)throw new RangeError(`${key} must lie in [${lo},${hi}]`);
 c.latent=Object.freeze(vector(c.latent,128));c.velocity=Object.freeze(vector(c.velocity,4));const config=Object.freeze(c);
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
 const gate=smooth5((x-c.openingEndpoint)/c.ramp),rawDeviation=c.strength*gate*2.2*Math.tanh(c.contrast*score),unconstrainedGuideDeviation=c.strength*gate*.25*Math.tanh(2*guideScore),w=smooth5(c.stabilizer),u=w===0?rawDeviation:w===1?unconstrainedGuideDeviation:rawDeviation+(unconstrainedGuideDeviation-rawDeviation)*w,base=authored(clock(x)),unconstrainedValue=base+base*u,unconstrainedGuideValue=base+base*unconstrainedGuideDeviation,lim=limits(base,unconstrainedGuideValue,gate),value=apply(unconstrainedValue,lim),guideValue=apply(unconstrainedGuideValue,lim),deviation=c.floor==='none'&&c.ceilingMargin===null?u:(value-base)/base,guideDeviation=c.floor==='none'&&c.ceilingMargin===null?unconstrainedGuideDeviation:(guideValue-base)/base,positiveMultiplier=Math.exp(.55*Math.tanh(deviation));
 return Object.freeze({x,value,base,score,guideScore,rawDeviation,guideDeviation,guideValue,stabilizationWeight:w,deviation,multiplier:1+deviation,activity:.5+.5*Math.tanh(deviation),positiveMultiplier,positiveValue:base*positiveMultiplier,gate,unconstrainedValue,unconstrainedDeviation:u,unconstrainedGuideValue,unconstrainedGuideDeviation,...lim,constraintApplied:value!==unconstrainedValue});
 }
 function sample(x){check(x);let score=0,guideScore=0;if(x>c.openingEndpoint){const u=clock(x)-100;for(const r of ridges){score+=noise(r.phase+u*r.slope,r.channel)/FEATURES;guideScore+=noise(r.guidePhase+u*r.guideSlope,r.channel)/FEATURES;}}return result(x,score,guideScore);}
 function at(x,h){check(x);const latent=vector(h);let score=0,guideScore=0;if(x>c.openingEndpoint){const u=clock(x)-100;for(const r of ridges){const projection=dot(r.direction,latent);score+=noise(r.basePhase+projection+u/r.scale,r.channel)/FEATURES;guideScore+=noise(r.basePhase+projection/32+u/(32*r.scale),r.channel)/FEATURES;}}return result(x,score,guideScore);}
 function location(x){check(x);const t=Math.max(0,clock(x)-100)/100;return Object.freeze(c.latent.map((h,i)=>h+t*c.velocity[i]));}
 function inspect(x){check(x);if(x<=c.openingEndpoint)return Object.freeze([]);const u=clock(x)-100;return Object.freeze(ridges.map(r=>{const p=r.phase+u*r.slope;return Object.freeze({index:r.index,scale:r.scale,coordinate:p,cell:Math.floor(p),contribution:noise(p,r.channel)/FEATURES,guideContribution:noise(r.guidePhase+u*r.guideSlope,r.channel)/FEATURES});}).sort((a,b)=>Math.abs(b.contribution)-Math.abs(a.contribution)));}
 const envelope=(2.2*(1-smooth5(c.stabilizer))+.25*smooth5(c.stabilizer))*c.strength;
 const lower=c.floor==='none'?-envelope:c.floor==='zero'?-1:c.floor==='baseline'?0:-.25*c.strength;
 const upper=c.ceilingMargin===null?envelope:(c.floor==='baseline'?0:.25*c.strength)+c.ceilingMargin;
 const bounds=Object.freeze({score:Object.freeze([-1,1]),deviation:Object.freeze([lower,upper]),multiplier:Object.freeze([1+lower,1+upper]),activity:Object.freeze([0,1]),positiveMultiplier:Object.freeze([Math.exp(-.55),Math.exp(.55)])});
 return Object.freeze({algorithm:ALGORITHM,config,bounds,domain,dips,sample,at,location,inspect,ridges:()=>Object.freeze(ridges.slice())});
}

function forkSeed(parentSeed,operation,at){
 const bits=new DataView(new ArrayBuffer(8));bits.setFloat64(0,at,false);
 return hash32(hash32(seed32(parentSeed),typeof parentSeed==='string'?1:2,seed32(operation)),bits.getUint32(0,false),bits.getUint32(4,false));
}
function mixSamples(a,b,w){
 const r={};for(const key of Object.keys(a)){const av=a[key],bv=b[key];r[key]=typeof av==='number'&&typeof bv==='number'?av+(bv-av)*w:av;}
 // Blend actual constrained values and their references, then derive every adapter afresh.
 if(r.floorValue!==null)r.value=Math.max(r.value,r.floorValue);
 if(r.ceilingValue!==null)r.value=Math.min(r.value,r.ceilingValue);
 r.deviation=(r.value-r.base)/r.base;r.multiplier=1+r.deviation;r.activity=.5+.5*Math.tanh(r.deviation);
 r.positiveMultiplier=Math.exp(.55*Math.tanh(r.deviation));r.positiveValue=r.base*r.positiveMultiplier;
 r.guideDeviation=(r.guideValue-r.base)/r.base;r.unconstrainedDeviation=(r.unconstrainedValue-r.base)/r.base;
 r.unconstrainedGuideDeviation=(r.unconstrainedGuideValue-r.base)/r.base;r.constraintApplied=r.value!==r.unconstrainedValue;
 return Object.freeze(r);
}
function timeline(leaf,rootConfig=leaf.config,lineage=Object.freeze([]),parent=null,node=null){
 function evaluate(x,h){
  if(!node||x>=node.at+node.handoffWidth)return h===undefined?leaf.sample(x):leaf.at(x,h);
  if(x<=node.at)return h===undefined?parent.sample(x):parent.at(x,h);
  const w=smooth5((x-node.at)/node.handoffWidth),a=h===undefined?parent.sample(x):parent.at(x,h),b=h===undefined?leaf.sample(x):leaf.at(x,h);
  return mixSamples(a,b,w);
 }
 function inspect(x){
  if(!node||x>=node.at+node.handoffWidth)return leaf.inspect(x);
  if(x<=node.at)return parent.inspect(x);
  const w=smooth5((x-node.at)/node.handoffWidth);
  return Object.freeze([...parent.inspect(x).map(r=>Object.freeze({...r,sourceSeed:r.sourceSeed??parent.config.seed,contribution:r.contribution*(1-w),guideContribution:r.guideContribution*(1-w)})),...leaf.inspect(x).map(r=>Object.freeze({...r,sourceSeed:leaf.config.seed,contribution:r.contribution*w,guideContribution:r.guideContribution*w}))].sort((a,b)=>Math.abs(b.contribution)-Math.abs(a.contribution)));
 }
 function snapshot(){return Object.freeze({algorithm:ALGORITHM,config:rootConfig,lineage:Object.freeze(lineage.map(({at,operation,handoffWidth})=>Object.freeze({at,operation,handoffWidth})))});}
 function fork(options){
  if(!options||typeof options!=='object'||Array.isArray(options)||Object.keys(options).some(k=>!['at','operation','handoffWidth'].includes(k)))throw new TypeError('Fork options: at, operation, handoffWidth');
  const {at,operation}=options;leaf.sample(at);
  if(typeof operation!=='string'||operation.length===0)throw new TypeError('A nonempty operation string is required');seed32(operation);
  if(lineage.length>=32)throw new RangeError('Maximum branch depth: 32');
  if(node&&at<node.at)throw new RangeError('Nested forks cannot precede their parent fork');
  const handoffWidth=options.handoffWidth??Math.min(16,leaf.domain[1]-at);
  if(typeof handoffWidth!=='number'||!Number.isFinite(handoffWidth)||handoffWidth<=0||at+handoffWidth<=at||at+handoffWidth>leaf.domain[1])throw new RangeError('Handoff must have positive representable width inside the domain');
  const seed=forkSeed(leaf.config.seed,operation,at),entry=Object.freeze({at,operation,handoffWidth,seed}),future=createLeaf({...leaf.config,seed});
  return timeline(future,rootConfig,Object.freeze([...lineage,entry]),api,entry);
 }
 const api=Object.freeze({...leaf,lineage,sample:x=>evaluate(x),at:(x,h)=>{vector(h);return evaluate(x,h);},inspect,fork,snapshot});return api;
}
export function create(options={}){return timeline(createLeaf(options));}
export function restore(snapshot){
 if(!snapshot||typeof snapshot!=='object'||!snapshot.config||typeof snapshot.config!=='object'||Array.isArray(snapshot.config))throw new TypeError('Snapshot with configuration required');
 if(snapshot.algorithm===ALGORITHM){
  if(Object.keys(snapshot).some(k=>!['algorithm','config','lineage'].includes(k)))throw new TypeError('Unknown snapshot field');
  const lineage=snapshot.lineage??[];if(!Array.isArray(lineage)||lineage.length>32)throw new RangeError('Invalid branch lineage');
  let result=create(snapshot.config);for(const entry of lineage)result=result.fork(entry);return result;
 }
 if(['vixen-hyperfield-c2/1','vixen-hyperfield-c2/2','vixen-hyperfield-c2/3'].includes(snapshot.algorithm)){
  if(snapshot.lineage!==undefined||['openingEndpoint','introVariation'].some(k=>Object.hasOwn(snapshot.config,k)))throw new TypeError('Old snapshots cannot contain timeline features');
  if(snapshot.algorithm!=='vixen-hyperfield-c2/3'&&['floor','ceilingMargin','boundarySoftness'].some(k=>Object.hasOwn(snapshot.config,k)))throw new TypeError('Old snapshots cannot contain constraints');
  if(snapshot.algorithm==='vixen-hyperfield-c2/1'&&Object.hasOwn(snapshot.config,'stabilizer'))throw new TypeError('Old snapshots cannot contain stabilizer');
  return create(snapshot.algorithm==='vixen-hyperfield-c2/3'?snapshot.config:{...snapshot.config,floor:'none',ceilingMargin:null});
 }
 throw new Error('Algorithm mismatch: use the matching archived sampler');
}
