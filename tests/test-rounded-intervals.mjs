import test from 'node:test';
import assert from 'node:assert/strict';
import {smooth5,boundaryRamp} from '../vixen.mjs';
import * as I from '../vixen-interval.mjs';
test('interval gates enclose rounded polynomial overshoot, not only ideal real range',()=>{
 for(const [sampler,enclose] of [[smooth5,I.smooth5],[boundaryRamp,I.boundaryRamp]]){
  const full=enclose(I.interval(0,1));
  for(let i=1;i<10000;i++){
   const x=1-i*Number.EPSILON,value=sampler(x),point=enclose(I.interval(x)),small=enclose(I.interval(x,1));
   for(const b of [full,point,small])assert.ok(b.lo<=value&&value<=b.hi,JSON.stringify({x,value,b}));
  }
 }
});
