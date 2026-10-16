import {jet,jetAdd,jetSub,jetMul,jetDiv,jetSmooth5,applyBoundaryJets} from './vixen-calculus.mjs';
import {guideJet} from './vixen-guide.mjs';
import {finishJet,deriveAdapters} from './vixen-jet-composition.mjs';
const C=jet,add=jetAdd,sub=jetSub,mul=jetMul,div=jetDiv;
export function influenceJet(parent,compiled,r,config,sample,x,scalarPolicy){
 const fields={};
 for(const [key,value]of Object.entries(parent.sample))if(typeof value==='number'){
  const q=parent.fields[key];fields[key]=C(value,q?.d1??NaN,q?.d2??NaN);
 }
 const p=parent.sample,B=fields.base;
 let W;
 if(x<=r.at||(r.windowed&&x>=r.releaseEnd))W=C(0);
 else if(x<r.at+r.handoffWidth)W=mul(C(r.amount),jetSmooth5(C((x-r.at)/r.handoffWidth,1/r.handoffWidth,0)));
 else if(!r.windowed||x<=r.until)W=C(r.amount);
 else W=mul(C(r.amount),sub(C(1),jetSmooth5(C((x-r.until)/r.releaseWidth,1/r.releaseWidth,0))));
 const G=guideJet(compiled,x),lo=p.floorValue===null?null:fields.floorValue,hi=p.ceilingValue===null?null:fields.ceilingValue;
 let E=fields.boundaryWidth;
 const extra=mul(mul(B,W),C(config.boundarySoftness));
 if(lo&&hi){
  const room=sub(div(sub(hi,lo),C(2)),E);
  if(room.value>0&&extra.value>0){
   const increment=extra.value<=room.value?div(extra,add(C(1),div(extra,room))):div(room,add(C(1),div(room,extra)));
   E=add(E,increment);
  }else if(room.value===0&&extra.value>0&&(room.d1!==0||room.d2!==0))E=C(E.value,NaN,NaN);
 }else if(lo||hi)E=add(E,extra);
 let U;
 if(r.mode==='add')U=add(fields.unconstrainedValue,r.reference==='value'?mul(W,G):mul(B,mul(W,G)));
 else if(W.value===1&&W.d1===0&&W.d2===0){
  U=r.reference==='value'?G:r.reference==='baseline'?mul(B,add(C(1),G)):add(fields.unconstrainedGuideValue,mul(B,G));
 }else{
  U=mul(sub(C(1),W),fields.unconstrainedValue);
  if(r.reference==='value')U=add(U,mul(W,G));
  else if(r.reference==='baseline')U=add(U,mul(B,mul(W,add(C(1),G))));
  else U=add(add(U,mul(W,fields.unconstrainedGuideValue)),mul(B,mul(W,G)));
 }
 const mapped=applyBoundaryJets(U,lo,hi,E),guided=applyBoundaryJets(fields.unconstrainedGuideValue,lo,hi,E);
 fields.boundaryWidth=E;fields.unconstrainedValue=U;
 fields.value=mapped.hard?C(sample.value,NaN,NaN):mapped.value;
 fields.guideValue=guided.hard?C(sample.guideValue,NaN,NaN):guided.value;
 let reason=mapped.hard?'A contributing hard constraint is not twice differentiable at this contact.':null;
 if(x===r.at||(r.windowed&&x===r.releaseEnd)){
  if(scalarPolicy(p.unconstrainedValue,p)!==p.value){fields.value=C(sample.value,NaN,NaN);reason='A legacy constrained-parent composition is discontinuous at this influence boundary.';}
  if(scalarPolicy(p.unconstrainedGuideValue,p)!==p.guideValue)fields.guideValue=C(sample.guideValue,NaN,NaN);
 }
 return finishJet(sample,deriveAdapters(fields),reason);
}
