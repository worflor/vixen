import test from 'node:test';
import assert from 'node:assert/strict';
import {create} from '../vixen.mjs';
test('unrestricted spatial sensitivity metadata agrees with its analytical composition',()=>{
 for(const stabilizer of [0,.3,1])for(const strength of [0,.5,2])for(const contrast of [0,3,16]){
  const f=create({seed:'sensitivity',stabilizer,strength,contrast});
  const w=stabilizer**3*(10+stabilizer*(-15+6*stabilizer));
  const expected=strength*((1-w)*33*contrast/4+w*15/256);
  assert.ok(Math.abs(f.bounds.unconstrainedSpatialSlope-expected)<1e-12);
  const h=Array(16).fill(.1),epsilon=.00001,gradient=[];
  for(let axis=0;axis<16;axis++){const a=h.slice(),b=h.slice();a[axis]-=epsilon;b[axis]+=epsilon;gradient.push((f.at(233,b).unconstrainedDeviation-f.at(233,a).unconstrainedDeviation)/(2*epsilon));}
  assert.ok(Math.hypot(...gradient)<=expected+1e-7);
 }
});
