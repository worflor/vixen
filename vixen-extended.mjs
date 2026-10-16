import {createCoherent, seed32, hash32} from './vixen.mjs';
import {nativeNormalized} from './vixen-calculus.mjs';
import {Wide} from './vixen-wide.mjs';
import {wideExp} from './vixen-exponential.mjs';
import {historyWitness} from './vixen-checkpoint.mjs';
import {append as historyAppend, before as historyBefore, entries as historyEntries} from './vixen-timeline.mjs';
import {applyWidePolicy, makeScaledSample} from './vixen-scaled-state.mjs';
import {withExtendedInfluence,withExtendedImpulse,withExtendedRedistribution} from './vixen-extended-operations.mjs';

export const EXTENDED_ALGORITHM = 'vixen-hyperfield-c2/6';
export const EXTENDED_TAIL_ALGORITHM = 'vixen-tail-c2/4';
export const MAX_DOMAIN_END = 2 ** 40;
const MIX_FIELDS = Object.freeze(['x','value','base','score','guideScore','rawDeviation','guideValue','stabilizationWeight','gate','unconstrainedValue','unconstrainedGuideValue','boundaryWidth']);
const ABS_FIELDS = Object.freeze(['base','value','guideValue','positiveValue','unconstrainedValue','unconstrainedGuideValue']);
const apiStates = new WeakMap();
const smooth5 = t => t <= 0 ? 0 : t >= 1 ? 1 : t*t*t*(10+t*(-15+6*t));
const ramp = t => t <= 0 ? 0 : t >= 1 ? t : t*t*t*(6+t*(-8+3*t));
const coordinate = (x,end) => {
  if (typeof x !== 'number' || !Number.isFinite(x) || x < 1 || x > end) throw new RangeError(`x must be finite in [1,${end}]`);
};
function vector(v) {
  if ((!Array.isArray(v) && !ArrayBuffer.isView(v)) || v.length !== 16) throw new RangeError('Coordinates must contain exactly 16 numbers');
  const out = Array.from(v);
  if (out.some(n => typeof n !== 'number' || !Number.isFinite(n) || Math.abs(n) > 1e6)) throw new RangeError('Coordinates must be finite in [-1000000,1000000]');
  return out;
}
function exactObject(value, keys, label) {
  if (!value || typeof value !== 'object' || Array.isArray(value) || Reflect.ownKeys(value).some(k => typeof k !== 'string' || !keys.includes(k)) || keys.some(k => !Object.hasOwn(value,k))) throw new TypeError(`Invalid ${label} fields`);
}
function configJSON(config) { return config; }
function sourceFor(config) {
  const curve = createCoherent(config);
  const facade = Object.freeze({config: curve.config, dips: curve.dips, ridges: () => curve.ridges(), domain: curve.domain, sample: x => curve.sample(x), at: (x,h) => curve.at(x,h)});
  return Object.freeze({curve, facade, config: curve.config, domain: curve.domain});
}
function forkSeed(parentSeed, operation, at) {
  const bits = new DataView(new ArrayBuffer(8)); bits.setFloat64(0, at, false);
  return hash32(hash32(seed32(parentSeed), typeof parentSeed === 'string' ? 1 : 2, seed32(operation)), bits.getUint32(0,false), bits.getUint32(4,false));
}
function rawState(sample) {
  const w = key => sample[key] === null ? null : Wide.fromNumber(sample[key]);
  return {...sample, base:w('base'), value:w('value'), guideValue:w('guideValue'), positiveValue:w('positiveValue'), unconstrainedValue:w('unconstrainedValue'), unconstrainedGuideValue:w('unconstrainedGuideValue'), floorValue:w('floorValue'), ceilingValue:w('ceilingValue'), boundaryWidth:w('boundaryWidth')};
}
function wideLeaf(source, x, h) {
  const p = nativeNormalized(source.facade, x, h);
  const c = source.config, endpoint = c.openingEndpoint;
  const clock = endpoint === 100 ? x : 1 + (x-1)*99/(endpoint-1);
  const base = Wide.fromNumber(clock).mul(wideExp(clock/100));
  const floorValue = p.floorRatio === null ? null : base.mul(Wide.fromNumber(p.floorRatio));
  const ceilingValue = p.ceilingRatio === null ? null : base.mul(Wide.fromNumber(p.ceilingRatio));
  const boundaryWidth = base.mul(Wide.fromNumber(p.boundaryWidthRatio));
  const unconstrainedValue = base.add(base.mul(Wide.fromNumber(p.unconstrainedDeviation)));
  const unconstrainedGuideValue = base.add(base.mul(Wide.fromNumber(p.unconstrainedGuideDeviation)));
  return applyWidePolicy({...p, base, floorValue, ceilingValue, boundaryWidth, unconstrainedValue, unconstrainedGuideValue,
    value: unconstrainedValue, guideValue: unconstrainedGuideValue, positiveValue: base.mul(Wide.fromNumber(p.positiveMultiplier))});
}
function finiteRampMap(y,lo,hi,width) {
  if (lo !== null && hi !== null && hi <= lo) return lo;
  if (lo !== null) y = y <= lo ? lo : width === 0 || y >= lo+width ? y : lo+width*ramp((y-lo)/width);
  if (hi !== null) y = y >= hi ? hi : width === 0 || y <= hi-width ? y : hi-width*ramp((hi-y)/width);
  return y;
}
function mixFinite(a,b,w) {
  const r = {...a};
  for (const key of MIX_FIELDS) r[key] = a[key] + (b[key]-a[key])*w;
  for (const key of ['floorValue','ceilingValue']) if (a[key] !== null) r[key] = a[key] + (b[key]-a[key])*w;
  r.value = finiteRampMap(r.unconstrainedValue,r.floorValue,r.ceilingValue,r.boundaryWidth);
  r.guideValue = finiteRampMap(r.unconstrainedGuideValue,r.floorValue,r.ceilingValue,r.boundaryWidth);
  r.deviation=(r.value-r.base)/r.base; r.multiplier=1+r.deviation; r.activity=.5+.5*Math.tanh(r.deviation);
  r.positiveMultiplier=Math.exp(.55*Math.tanh(r.deviation)); r.positiveValue=r.base*r.positiveMultiplier;
  r.guideDeviation=(r.guideValue-r.base)/r.base; r.unconstrainedDeviation=(r.unconstrainedValue-r.base)/r.base;
  r.unconstrainedGuideDeviation=(r.unconstrainedGuideValue-r.base)/r.base; r.constraintApplied=r.value!==r.unconstrainedValue;
  return r;
}
function blendWide(a,b,w) { return a.add(b.sub(a).mul(Wide.fromNumber(w))); }
function mixWide(a,b,w) {
  const r = {...a};
  for (const key of ['base','unconstrainedValue','unconstrainedGuideValue','boundaryWidth']) r[key]=blendWide(a[key],b[key],w);
  for (const key of ['floorValue','ceilingValue']) {
    if ((a[key] === null) !== (b[key] === null)) throw new TypeError('Fork boundary shape changed');
    r[key] = a[key] === null ? null : blendWide(a[key],b[key],w);
  }
  for (const key of ['x','score','guideScore','rawDeviation','stabilizationWeight','gate']) r[key]=a[key]+(b[key]-a[key])*w;
  r.value=r.unconstrainedValue; r.guideValue=r.unconstrainedGuideValue;
  return applyWidePolicy(r);
}
function legacyEnd(state) { return state.rootLeaf.domain[1]; }
function route(state,x) {
  if (!state.last || x >= state.last.entry.at+state.last.entry.handoffWidth) return {base:state.latest,active:[]};
  const active=[]; let base=state.rootLeaf;
  for (const event of historyBefore(state.tree,x)) {
    if (x >= event.entry.at+event.entry.handoffWidth) { base=event.source; break; }
    active.push(event);
  }
  return {base,active};
}
function evaluate(state,x,h) {
  const selected=route(state,x);
  if (x <= legacyEnd(state)) {
    let p=h === undefined ? selected.base.curve.sample(x) : selected.base.curve.at(x,h);
    for (let i=selected.active.length-1;i>=0;i--) {
      const event=selected.active[i], q=h === undefined ? event.source.curve.sample(x) : event.source.curve.at(x,h);
      p=mixFinite(p,q,smooth5((x-event.entry.at)/event.entry.handoffWidth));
    }
    const out={...makeScaledSample(rawState(p)),...p};
    for(const key of [...ABS_FIELDS,'floorValue','ceilingValue','boundaryWidth'])out[`scaled${key[0].toUpperCase()}${key.slice(1)}`]=p[key]===null?null:Wide.fromNumber(p[key]).toJSON();
    out.logBase=Math.log(p.base);
    out.signedLogValue=Object.freeze({sign:Math.sign(p.value),logAbs:p.value===0?null:Math.log(Math.abs(p.value))});
    return Object.freeze(out);
  }
  let p=wideLeaf(selected.base,x,h);
  for (let i=selected.active.length-1;i>=0;i--) {
    const event=selected.active[i], q=wideLeaf(event.source,x,h);
    p=mixWide(p,q,smooth5((x-event.entry.at)/event.entry.handoffWidth));
  }
  return makeScaledSample(p);
}
function rows(state) { return [...historyEntries(state.tree)]; }
function snapshotLineage(state) { return Object.freeze(rows(state).map(({entry})=>Object.freeze({at:entry.at,operation:entry.operation,handoffWidth:entry.handoffWidth}))); }
function checkAvailable(state,x) { coordinate(x,state.domainEnd); if (x < (state.checkpoint?.from ?? 1)) throw new RangeError('x precedes the available checkpoint'); }
function apiSnapshot(state) {
  const lineage=snapshotLineage(state);
  if (state.checkpoint) return Object.freeze({...state.checkpoint,lineage});
  return Object.freeze({algorithm:EXTENDED_ALGORITHM,config:configJSON(state.rootConfig),domainEnd:state.domainEnd,lineage});
}
function makeAPI(state) {
  const lower=state.checkpoint?.from ?? 1;
  const api={
    algorithm:state.checkpoint ? EXTENDED_TAIL_ALGORITHM : EXTENDED_ALGORITHM,
    config:state.latest.config,
    domain:Object.freeze([lower,state.domainEnd]),
    normalizedDomain:Object.freeze([lower,state.domainEnd]),
    availableFrom:lower,
    sample:x=>{checkAvailable(state,x);const p=evaluate(state,x);for(const key of ABS_FIELDS)if(p[key]===null)throw new RangeError(`Raw ${key} is unavailable; use sampleNormalized()`);return p;},
    at:(x,h)=>{checkAvailable(state,x);const p=evaluate(state,x,vector(h));for(const key of ABS_FIELDS)if(p[key]===null)throw new RangeError(`Raw ${key} is unavailable; use atNormalized()`);return p;},
    sampleNormalized:x=>{checkAvailable(state,x);return evaluate(state,x);},
    atNormalized:(x,h)=>{checkAvailable(state,x);return evaluate(state,x,vector(h));},
    location:x=>{
      checkAvailable(state,x);
      const c=state.latest.config;
      if(x<=state.latest.domain[1])return state.latest.curve.location(x);
      const clock=c.openingEndpoint===100?x:1+(x-1)*99/(c.openingEndpoint-1),t=Math.max(0,clock-100)/100;
      return Object.freeze(c.latent.map((v,i)=>v+t*c.velocity[i]));
    },
    inspect:x=>inspectTimeline(state,x),
    ridges:()=>state.latest.curve.ridges(),
    get dips(){return state.latest.curve.dips;},
    influence:(target,options={})=>withExtendedInfluence(api,target,options),
    impulse:recipe=>withExtendedImpulse(api,recipe),
    redistribute:(window,spec)=>withExtendedRedistribution(api,{from:window[0],until:window[1],...(Array.isArray(spec)?{coefficients:spec}:spec)}),
    fork:options=>forkTimeline(state,options),
    snapshot:()=>apiSnapshot(state),
    tail:from=>tailSnapshot(state,from),
    get lineage(){return Object.freeze(rows(state).map(event=>event.entry));}
  };
  Object.freeze(api); apiStates.set(api,state); return api;
}
function inspectLeaf(source,x) {
  if(x<=source.config.openingEndpoint)return [];
  if(x<=source.domain[1])return source.curve.inspect(x);
  const c=source.config,clock=c.openingEndpoint===100?x:1+(x-1)*99/(c.openingEndpoint-1),u=clock-100,seed=seed32(c.seed);
  const noise=(p,ch)=>{const k=Math.floor(p),t=p-k,a=2*hash32(seed,k,ch)/4294967296-1,b=2*hash32(seed,k+1,ch)/4294967296-1;return a+(b-a)*smooth5(t);};
  return source.curve.ridges().map(r=>{const p=r.phase+u*r.slope;return {index:r.index,scale:r.scale,coordinate:p,cell:Math.floor(p),contribution:noise(p,r.channel)/48,guideContribution:noise(r.guidePhase+u*r.guideSlope,r.channel)/48};}).sort((a,b)=>Math.abs(b.contribution)-Math.abs(a.contribution));
}
function inspectTimeline(state,x) {
  checkAvailable(state,x); const {base,active}=route(state,x); let out=inspectLeaf(base,x),sourceSeed=base.config.seed;
  for(let i=active.length-1;i>=0;i--){const e=active[i],w=smooth5((x-e.entry.at)/e.entry.handoffWidth);out=[...out.map(r=>({...r,sourceSeed:r.sourceSeed??sourceSeed,contribution:r.contribution*(1-w),guideContribution:r.guideContribution*(1-w)})),...inspectLeaf(e.source,x).map(r=>({...r,sourceSeed:e.source.config.seed,contribution:r.contribution*w,guideContribution:r.guideContribution*w}))].sort((a,b)=>Math.abs(b.contribution)-Math.abs(a.contribution));sourceSeed=e.source.config.seed;}
  return Object.freeze(out.map(Object.freeze));
}
function forkTimeline(state,options) {
  if(!options||typeof options!=='object'||Array.isArray(options)||Reflect.ownKeys(options).some(k=>!['at','operation','handoffWidth'].includes(k))||!Object.hasOwn(options,'at')||!Object.hasOwn(options,'operation'))throw new TypeError('Fork options: at, operation, handoffWidth');
  const {at,operation}=options; checkAvailable(state,at);
  if(typeof operation!=='string'||operation.length===0)throw new TypeError('A nonempty operation string is required'); seed32(operation);
  if(state.last&&at<state.last.entry.at)throw new RangeError('Nested forks cannot precede their parent fork');
  const handoffWidth=options.handoffWidth??Math.min(16,state.domainEnd-at);
  if(typeof handoffWidth!=='number'||!Number.isFinite(handoffWidth)||handoffWidth<=0||at+handoffWidth<=at||at+handoffWidth>state.domainEnd)throw new RangeError('Handoff must have positive representable width inside the domain');
  const seed=forkSeed(state.latest.config.seed,operation,at),entry=Object.freeze({at,operation,handoffWidth,seed});
  const source=sourceFor({...state.latest.config,seed}),event=Object.freeze({entry,source});
  return makeAPI({...state,latest:source,tree:historyAppend(state.tree,event),last:event});
}
function tailSnapshot(state,from) {
  checkAvailable(state,from);
  const all=rows(state);let cut=-1;
  for(let i=0;i<all.length;i++)if(all[i].entry.at+all[i].entry.handoffWidth<=from)cut=i;
  const folded=all.slice(0,cut+1).map(e=>e.entry),base=cut<0?state.rootLeaf:all[cut].source,prior=state.checkpoint?.witness??null;
  const witness=folded.length||prior===null?historyWitness(prior,folded):prior;
  const foldedCount=(state.checkpoint?.foldedCount??0)+folded.length;
  if(!Number.isSafeInteger(foldedCount))throw new RangeError("Folded count exceeds the safe replay representation");
  return Object.freeze({algorithm:EXTENDED_TAIL_ALGORITHM,from,config:state.rootConfig,domainEnd:state.domainEnd,seed:base.config.seed,leafSeed:seed32(base.config.seed),foldedCount,witness,lineage:Object.freeze(all.slice(cut+1).map(({entry})=>Object.freeze({at:entry.at,operation:entry.operation,handoffWidth:entry.handoffWidth})))});
}
function rootState(config,domainEnd,checkpoint=null,rootConfig=null) {
  const source=sourceFor(config);
  if(typeof domainEnd!=='number'||!Number.isFinite(domainEnd)||domainEnd<source.config.openingEndpoint||domainEnd>MAX_DOMAIN_END)throw new RangeError('domainEnd must include the opening endpoint and be at most 2^40');
  return {rootLeaf:source,latest:source,rootConfig:rootConfig??source.config,domainEnd,tree:null,last:null,checkpoint};
}
export function createExtended(options={}) {
  if(!options||typeof options!=='object'||Array.isArray(options)||Reflect.ownKeys(options).some(k=>typeof k!=='string'))throw new TypeError('Config must be an object');
  const config={...options};const domainEnd=Object.hasOwn(config,'domainEnd')?config.domainEnd:MAX_DOMAIN_END;delete config.domainEnd;
  return makeAPI(rootState(config,domainEnd));
}
function restoreRoot(snapshot) {
  exactObject(snapshot,['algorithm','config','domainEnd','lineage'],'extended snapshot');
  if(snapshot.algorithm!==EXTENDED_ALGORITHM||!Array.isArray(snapshot.lineage))throw new TypeError('Invalid extended snapshot');
  let api=createExtended({...snapshot.config,domainEnd:snapshot.domainEnd});
  for(const entry of snapshot.lineage) { exactObject(entry,['at','operation','handoffWidth'],'lineage entry'); api=api.fork(entry); }
  return api;
}
function restoreTail(snapshot) {
  exactObject(snapshot,['algorithm','from','config','domainEnd','seed','leafSeed','foldedCount','witness','lineage'],'extended checkpoint');
  if(snapshot.algorithm!==EXTENDED_TAIL_ALGORITHM||!Array.isArray(snapshot.lineage)||typeof snapshot.witness!=='string'||!/^[0-9a-f]{64}$/.test(snapshot.witness)||!Number.isSafeInteger(snapshot.foldedCount)||snapshot.foldedCount<0||!Number.isInteger(snapshot.leafSeed)||snapshot.leafSeed<0||snapshot.leafSeed>0xffffffff||seed32(snapshot.seed)!==snapshot.leafSeed)throw new TypeError('Invalid extended checkpoint data');
  let base=createExtended({...snapshot.config,seed:snapshot.seed,domainEnd:snapshot.domainEnd});
  coordinate(snapshot.from,snapshot.domainEnd);
  const originalConfig=sourceFor(snapshot.config).config;
  if(snapshot.foldedCount===0&&(typeof snapshot.seed!==typeof originalConfig.seed||snapshot.seed!==originalConfig.seed))throw new TypeError('Unfolded checkpoint must preserve the typed root seed');
  if(snapshot.foldedCount>0&&typeof snapshot.seed!=='number')throw new TypeError('Folded checkpoint seed must be uint32');
  for(const entry of snapshot.lineage){exactObject(entry,['at','operation','handoffWidth'],'checkpoint lineage entry');base=base.fork(entry);}
  const cp=Object.freeze({algorithm:EXTENDED_TAIL_ALGORITHM,from:snapshot.from,config:originalConfig,domainEnd:snapshot.domainEnd,seed:snapshot.seed,leafSeed:snapshot.leafSeed,foldedCount:snapshot.foldedCount,witness:snapshot.witness});
  const s=apiStates.get(base);
  return makeAPI({...s,rootConfig:originalConfig,checkpoint:cp});
}
export function restoreExtended(snapshot) {
  if(!snapshot||typeof snapshot!=='object'||Array.isArray(snapshot))throw new TypeError('Snapshot object required');
  if(snapshot.algorithm===EXTENDED_ALGORITHM)return restoreRoot(snapshot);
  if(snapshot.algorithm===EXTENDED_TAIL_ALGORITHM)return restoreTail(snapshot);
  throw new TypeError('restoreExtended accepts only vixen-hyperfield-c2/6 and vixen-tail-c2/4 snapshots');
}
