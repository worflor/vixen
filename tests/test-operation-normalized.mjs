import test from 'node:test';
import assert from 'node:assert/strict';
import {createCoherent,couple,restore} from '../vixen.mjs';
test('finite operation and coupling normalized facades expose current samples, not stubs',()=>{
 const root=createCoherent(),operation=root.impulse(120,.1,20),view=couple([root,createCoherent({seed:2})],.4,[120,200]).curves[0];
 for(const curve of [operation,view,restore(operation.snapshot())]){
  for(const x of [1,130,180,220]){const p=curve.sample(x),n=curve.sampleNormalized(x);for(const [key,value] of Object.entries(p))assert.deepEqual(n[key],value);assert.equal(n.logBase,Math.log(p.base));assert.equal(curve.atNormalized(x,Array(16).fill(0)).value,curve.at(x,Array(16).fill(0)).value);}
  assert.deepEqual(curve.normalizedDomain,curve.domain);assert.equal(curve.derivative(150),curve.slope(150));assert.equal(curve.secondDerivative(150),curve.curvature(150));
 }
});
