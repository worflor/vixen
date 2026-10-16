import test from 'node:test';
import assert from 'node:assert/strict';
import {jet,jetLog,jetTanh,jetMul,jetDiv} from '../vixen-calculus.mjs';
import {finishJet} from '../vixen-jet-composition.mjs';
const close=(a,b)=>assert.ok(Math.abs(a-b)<=1e-12*Math.max(Math.abs(b),Number.MIN_VALUE));
test('logarithmic jets divide before products can overflow',()=>{
 const q=jetLog(jet(1e200,1e200,1e200));assert.equal(q.d1,1);assert.equal(q.d2,0);
});
test('tanh derivatives do not vanish when the rounded value saturates',()=>{
 for(const x of [20,200]){
  const e=Math.exp(-2*x),p=4*e/(1+e)**2,q=jetTanh(jet(x,1e200,0));
  close(q.d1,p*1e200);
  if(x===200)close(q.d2,(-2*Math.tanh(x)*p*1e200)*1e200);
 }
});
test('jet product curvature can cancel exactly after overflowing separate terms',()=>{
 const q=jetMul(jet(1,2**500,-(2**1001)),jet(2**100,2**600,0));assert.equal(q.d2,0);
});

test('log and quotient jets recover representable curvature after intermediate overflow',()=>{
 assert.equal(jetLog(jet(2**(-1000),2**(-400),2**200)).d2,0);
 const q=jetDiv(jet(1,2**523+2**500,2**1023),jet(1,2**500,-(2**1023)));
 assert.equal(q.d2,0);
});

test('a finite slope is not hidden by an unrepresentable curvature',()=>{
 const out=finishJet({value:1},{value:jet(1,2,Infinity)});
 assert.equal(out.slope,2);assert.equal(out.curvature,null);assert.equal(out.fields.value.d1,2);
});
