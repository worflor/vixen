import {registerMultiCurve,registerUnaryCurve,evaluateCurve,snapshotCurve,latestWriteTime,curveDomain,curveOrigin,curveConfig} from './vixen-api.mjs';
import {extendedProxy,extendedPublic} from './vixen-extended-operations.mjs';
import {Wide} from './vixen-wide.mjs';
import {makeScaledSample} from './vixen-scaled-state.mjs';
export const EXTENDED_COUPLING_ALGORITHM='vixen-extended-coupling/1';
const absolute=['base','value','guideValue','positiveValue','unconstrainedValue','unconstrainedGuideValue'];
const wideFields=[...absolute,'floorValue','ceilingValue','boundaryWidth'];
const one=Wide.fromNumber(1);
function scalar(x){if(typeof x!=='number'||!Number.isFinite(x))throw new TypeError('Finite number required');return x;}
function vector(h){if((!Array.isArray(h)&&!ArrayBuffer.isView(h))||h.length!==16)throw new TypeError('Sixteen coordinates required');const out=Array.from(h);for(const v of out)if(Math.abs(scalar(v))>1e6)throw new RangeError('Coordinate outside latent domain');return out;}
function gate(x,a,b){if(x<=a||x>=b)return 0;const mid=a+(b-a)/2,t=x<=mid?(x-a)/(mid-a):(b-x)/(b-mid);return Math.max(0,Math.min(1,t*t*t*(10+t*(-15+6*t))));}
function state(p){const out={...p};for(const k of wideFields){const v=p['scaled'+k[0].toUpperCase()+k.slice(1)];out[k]=v===null?null:Wide.decode(v);}return out;}
function refuse(){throw new TypeError('Forking a coupled projection has ambiguous branch semantics');}
function raw(rows){for(const p of rows)for(const k of absolute)if(p[k]===null)throw new RangeError('Raw '+k+' unavailable; use sampleNormalized()');return rows;}
class View {
 constructor(group,index){this.domain=group.domain;this.config=curveConfig(group.sources[index]);this.algorithm=EXTENDED_COUPLING_ALGORITHM;
  registerUnaryCurve(this,group,{domain:this.domain,latest:latestWriteTime(group),origin:curveOrigin(group.sources[index]),interval:[-Infinity,Infinity],transform:rows=>rows[index],snapshotFrame:p=>({algorithm:EXTENDED_COUPLING_ALGORITHM,operation:'view',group:p[0],index}),rebuild:refuse,tailSource:from=>from>=group.window[1]?group.sources[index]:null});Object.freeze(this);
 }
}
class Group {
 constructor(curves,strength,window,restoring){
  if(!Array.isArray(curves)||curves.length<2||curves.length>32)throw new TypeError('Coupling requires 2..32 curves');
  this.sources=Object.freeze(curves.map(extendedProxy));this.strength=scalar(strength);
  if(strength<0||strength>1)throw new RangeError('Strength must be in [0,1]');
  if(!Array.isArray(window)||window.length!==2)throw new TypeError('Window requires two endpoints');
  const [a,b]=window.map(scalar);this.domain=Object.freeze([Math.max(...this.sources.map(c=>curveDomain(c)[0])),Math.min(...this.sources.map(c=>curveDomain(c)[1]))]);
  if(a<1||a>=b||b>this.domain[1]||(!restoring&&(a<this.domain[0]||this.sources.some(c=>a<latestWriteTime(c)))))throw new RangeError('Coupling window violates domain or chronology');
  if(strength!==0&&this.sources.some(c=>{const p=curveConfig(c);return p.floor!=='none'||p.ceilingMargin!==null;}))throw new RangeError('Coupling requires sources without nonlinear bounds');
  this.window=Object.freeze([a,b]);this.algorithm=EXTENDED_COUPLING_ALGORITHM;
  registerMultiCurve(this,this.sources,{domain:this.domain,latest:Math.max(a,...this.sources.map(latestWriteTime)),compute:(parents,x)=>this.combine(parents,x),snapshotFrame:p=>({algorithm:EXTENDED_COUPLING_ALGORITHM,operation:'group',curves:p,strength,window:this.window}),rebuild:refuse});
  this.curves=Object.freeze(this.sources.map((_,i)=>extendedPublic(new View(this,i))));Object.freeze(this);
 }
 check(x){scalar(x);if(x<this.domain[0]||x>this.domain[1])throw new RangeError('Coordinate outside shared domain');}
 combine(rows,x){const weight=this.strength*gate(x,...this.window);if(weight===0)return Object.freeze(rows.slice());
  const states=rows.map(state),ratios=states.map(p=>p.unconstrainedValue.div(p.base));let mean=Wide.fromNumber(0);for(const r of ratios)mean=mean.add(r);mean=mean.div(rows.length);
  return Object.freeze(states.map((p,i)=>{if(ratios[i].cmp(mean)===0)return rows[i];const u=p.unconstrainedValue.mul(1-weight).add(p.base.mul(mean).mul(weight));return makeScaledSample({...p,unconstrainedValue:u});}));
 }
 sampleNormalized(x){this.check(x);return evaluateCurve(this,x);}
 atNormalized(x,h){this.check(x);return evaluateCurve(this,x,vector(h),true);}
 sample(x){return raw(this.sampleNormalized(x));}
 at(x,h){return raw(this.atNormalized(x,h));}
 snapshot(){return snapshotCurve(this);}
 tail(from){this.check(from);return snapshotCurve(this,from);}
}
export function coupleExtended(curves,strength=1,window,restoring=false){if(typeof restoring!=='boolean')throw new TypeError('Invalid restore flag');return new Group(curves,strength,window,restoring);}
function fields(s,keys){if(!s||typeof s!=='object'||Array.isArray(s)||Reflect.ownKeys(s).some(k=>!keys.includes(k))||keys.some(k=>!Object.hasOwn(s,k)))throw new TypeError('Invalid extended coupling frame');}
export function restoreExtendedCoupling(s,read){
 if(s?.operation==='group'){fields(s,['algorithm','operation','curves','strength','window']);if(s.algorithm!==EXTENDED_COUPLING_ALGORITHM||!Array.isArray(s.curves))throw new TypeError('Invalid group frame');return coupleExtended(s.curves.map(read),s.strength,s.window,true);}
 fields(s,['algorithm','operation','group','index']);if(s.algorithm!==EXTENDED_COUPLING_ALGORITHM||s.operation!=='view'||!Number.isInteger(s.index))throw new TypeError('Invalid view frame');const group=read(s.group);if(!(group instanceof Group)||s.index<0||s.index>=group.curves.length)throw new TypeError('Invalid group projection');return group.curves[s.index];
}
