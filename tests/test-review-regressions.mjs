import test from 'node:test';
import assert from 'node:assert/strict';
import {restoreOccupancy,calibrateOccupancy,validateCorrelationMatrix,nextCrossing} from '../vixen-analysis.mjs';
test('restored occupancy rejects unrepresentable control ratios',()=>{
 const recipe=calibrateOccupancy([1,2],{targetFraction:.5,outputThreshold:.5}).serialize();
 assert.throws(()=>restoreOccupancy({...recipe,slope:Number.MAX_VALUE,scale:Number.MIN_VALUE}));
 assert.throws(()=>restoreOccupancy({...recipe,slope:Number.MIN_VALUE,scale:Number.MAX_VALUE}));
});
test('correlation eligibility is exact, not a tolerance projection',()=>{
 assert.throws(()=>validateCorrelationMatrix([[1,1+1e-12],[1+1e-12,1]]));
 assert.throws(()=>validateCorrelationMatrix([[1,.5,.5],[.5,1,-.5-1e-12],[.5,-.5-1e-12,1]]));
 assert.doesNotThrow(()=>validateCorrelationMatrix([[1,.5,.5],[.5,1,-.5],[.5,-.5,1]]));
});
test('both explicit query budgets limit refinement',()=>{
 const r=nextCrossing({domain:[0,1],sample:x=>x*x+1},.5,0,1,{continuous:true,maxIterations:1,maxIntervals:1000,enclose:()=>[0,3]});
 assert.equal(r.status,'unresolved');assert.ok(r.enclosureChecks<=1);
});
