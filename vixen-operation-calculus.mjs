import {jet,jetAdd,jetSub,jetMul,jetSmooth5,applyBoundaryJets} from './vixen-calculus.mjs';
import {exactMean} from './vixen-float.mjs';
import {finishJet,deriveAdapters} from './vixen-jet-composition.mjs';
export function operatorJet(parent,delta,sample){
 const fields={};
 for(const [key,value]of Object.entries(parent.sample))if(typeof value==='number'){
  const q=parent.fields[key];fields[key]=jet(value,q?.d1??NaN,q?.d2??NaN);
 }
 const p=parent.sample,B=fields.base,D=jet(...delta);
 const U=jetMul(B,jetAdd(jet(1),jetAdd(fields.unconstrainedDeviation,D)));
 const lo=p.floorValue===null?null:fields.floorValue,hi=p.ceilingValue===null?null:fields.ceilingValue;
 const mapped=applyBoundaryJets(U,lo,hi,fields.boundaryWidth);
 fields.unconstrainedValue=U;fields.value=mapped.hard?jet(sample.value,NaN,NaN):mapped.value;
 let reason=mapped.hard?'A contributing hard constraint is not twice differentiable at this contact.':null;
 if(delta[0]===0&&(lo||hi)){
  const limit=applyBoundaryJets(jet(p.unconstrainedValue),lo?jet(p.floorValue):null,hi?jet(p.ceilingValue):null,jet(p.boundaryWidth)).value.value;
  if(limit!==p.value){fields.value=jet(sample.value,NaN,NaN);reason='A legacy constrained-parent composition is discontinuous at this operator zero.';}
 }
 return finishJet(sample,deriveAdapters(fields),reason);
}

export function coupledJets(parents,samples,x,strength,from,until){
 let W;
 const middle=from+(until-from)/2;
 if(x<=from||x>=until||strength===0)return Object.freeze(parents);
 if(x<=middle)W=jetMul(jet(strength),jetSmooth5(jet((x-from)/(middle-from),1/(middle-from),0)));
 else W=jetMul(jet(strength),jetSmooth5(jet((until-x)/(until-middle),-1/(until-middle),0)));
 const parts=parents.map(p=>p.fields.deviation);
 const mean=key=>parts.every(q=>q&&Number.isFinite(q[key]))?exactMean(parts.map(q=>q[key])):NaN;
 const M=jet(exactMean(parents.map(p=>p.sample.deviation)),mean('d1'),mean('d2'));
 return Object.freeze(parents.map((parent,index)=>{
  const fields={};for(const [key,value]of Object.entries(parent.sample))if(typeof value==='number'){
   const q=parent.fields[key];fields[key]=jet(value,q?.d1??NaN,q?.d2??NaN);
  }
  const D=jetAdd(jetMul(jetSub(jet(1),W),fields.deviation),jetMul(W,M));
  fields.value=fields.unconstrainedValue=jetMul(fields.base,jetAdd(jet(1),D));
  return finishJet(samples[index],deriveAdapters(fields));
 }));
}
