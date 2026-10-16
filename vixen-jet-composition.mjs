import {jet,jetAdd,jetSub,jetMul,jetDiv,jetTanh,jetExp,applyBoundaryJets} from './vixen-calculus.mjs';
const fieldsToMix=['x','value','base','score','guideScore','rawDeviation','guideValue','stabilizationWeight','gate','unconstrainedValue','unconstrainedGuideValue','boundaryWidth'];
const constant=number=>jet(number);
const field=(q,name)=>{const f=q.fields[name];return f?jet(f.value,f.d1===null?NaN:f.d1,f.d2===null?NaN:f.d2):jet(q.sample[name],NaN,NaN);};
export function finishJet(sample,fields,reason=null){
 const out={};for(const [key,value]of Object.entries(sample))if(typeof value==='number'){
  const q=fields[key];out[key]=Object.freeze({value,d1:q&&Number.isFinite(q.d1)?q.d1:null,d2:q&&Number.isFinite(q.d2)?q.d2:null});
 }
 const first=out.value?.d1!==null&&out.value?.d1!==undefined,second=out.value?.d2!==null&&out.value?.d2!==undefined,valid=first&&second;
 return Object.freeze({sample,slope:first?out.value.d1:null,curvature:second?out.value.d2:null,fields:Object.freeze(out),availability:Object.freeze({slope:first,curvature:second}),differentiability:Object.freeze({value:valid,sample:Object.values(out).every(f=>f.d1!==null&&f.d2!==null),rationale:valid?null:reason??'A contributing value derivative is unavailable.'})});
}
export function deriveAdapters(fields){
 const r={...fields},one=constant(1),half=constant(.5);
 r.deviation=jetDiv(jetSub(r.value,r.base),r.base);r.multiplier=jetAdd(one,r.deviation);
 r.activity=jetAdd(half,jetMul(half,jetTanh(r.deviation)));
 r.positiveMultiplier=jetExp(jetMul(constant(.55),jetTanh(r.deviation)));r.positiveValue=jetMul(r.base,r.positiveMultiplier);
 for(const [target,source]of [['guideDeviation','guideValue'],['unconstrainedDeviation','unconstrainedValue'],['unconstrainedGuideDeviation','unconstrainedGuideValue']])r[target]=jetDiv(jetSub(r[source],r.base),r.base);
 return r;
}
export function blendJets(a,b,weight,sample,coherent=false){
 const r={};for(const key of fieldsToMix)r[key]=jetAdd(field(a,key),jetMul(jetSub(field(b,key),field(a,key)),weight));
 for(const key of ['floorValue','ceilingValue'])if(sample[key]!==null)r[key]=jetAdd(field(a,key),jetMul(jetSub(field(b,key),field(a,key)),weight));
 if(coherent){
  const mapped=applyBoundaryJets(r.unconstrainedValue,r.floorValue??null,r.ceilingValue??null,r.boundaryWidth),guide=applyBoundaryJets(r.unconstrainedGuideValue,r.floorValue??null,r.ceilingValue??null,r.boundaryWidth);
  r.value=mapped.hard?jet(sample.value,NaN,NaN):mapped.value;r.guideValue=guide.hard?jet(sample.guideValue,NaN,NaN):guide.value;
 }else{if(r.floorValue&&r.value.value<r.floorValue.value)r.value=r.floorValue;
 if(r.ceilingValue&&r.value.value>r.ceilingValue.value)r.value=r.ceilingValue;}
 return finishJet(sample,deriveAdapters(r));
}
