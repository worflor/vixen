import test from 'node:test';
import assert from 'node:assert/strict';
import {create,polynomialGuide,couple,perturbationJet} from '../vixen.mjs';

test('unrepresentable raw influence values fail explicitly',()=>{
 for(const mode of ['blend','add'])for(const reference of ['baseline','backbone']){
  const c=create().influence(polynomialGuide([1e100]),{at:100,amount:1,mode,reference});
  assert.throws(()=>c.sample(50000),RangeError);
 }
});
test('a weighted target does not overflow before a representable contribution is formed',()=>{
 const p=create(),s=p.sample(50000);
 const c=p.influence(polynomialGuide([1e250]),{at:100,amount:1e-250,reference:'baseline'});
 const v=c.sample(50000);assert.ok(Number.isFinite(v.value));
 assert.ok(Math.abs((v.unconstrainedValue-s.unconstrainedValue)/s.base-1)<1e-12);
});
test('boundary-width harmonic increments remain finite at the far scalar endpoint',()=>{
 const c=create({floor:'baseline',ceilingMargin:.2,boundarySoftness:.5}).influence(polynomialGuide([.3]),{at:100,reference:'baseline'});
 for(const v of Object.values(c.sample(50000)))if(typeof v==='number')assert.ok(Number.isFinite(v));
});
test('coupling equal extreme finite deviations remains an exact identity',()=>{
 const curves=Array.from({length:3},()=>create().impulse({at:1,attack:.0002,relaxation:.0002,magnitude:Number.MAX_VALUE}));
 const samples=curves.map(c=>c.sample(1.0001));
 assert.ok(samples.every(s=>Number.isFinite(s.deviation)));
 assert.deepEqual(couple(curves,1,[1,2]).sample(1.0001),samples);
});
test('zero and representable narrow impulse jets never produce NaN',()=>{
 assert.deepEqual(perturbationJet({type:'impulse',at:0,magnitude:0,attack:1e-200,relaxation:1},5e-201),{value:0,first:0,second:0});
 const j=perturbationJet({type:'impulse',at:0,magnitude:1e-300,attack:1e-200,relaxation:1},2.5e-201);
 assert.ok(Object.values(j).every(Number.isFinite));assert.ok(j.first>0&&j.second>0);
});

test('an influence never narrows an extreme finite parent into a false deviation bound',()=>{
 const p=create().impulse({at:1,magnitude:1e300,attack:.0002,relaxation:.0002});
 const c=p.influence(polynomialGuide([0]),{at:1,amount:0});
 const d=c.sample(1.0001).deviation;
 assert.ok(c.bounds.deviation[0]<=d && d<=c.bounds.deviation[1]);
});
