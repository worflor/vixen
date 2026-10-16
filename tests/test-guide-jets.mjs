import test from 'node:test';
import assert from 'node:assert/strict';
import {polynomialGuide,taylorGuide,sampledGuide,captureGuide,compileGuide,guideJet} from '../vixen-guide.mjs';
const near=(a,b)=>assert.ok(Math.abs(a-b)<=1e-12*Math.max(1,Math.abs(b)));
test('polynomial and Taylor guide jets are analytic with their authored units',()=>{
 const g=compileGuide(polynomialGuide([3,2,-.25,.5],{center:150,scale:10}));
 const j=guideJet(g,153),z=.3;assert.equal(j.value,g.evaluate(153));
 near(j.d1,(2-.5*z+1.5*z*z)/10);near(j.d2,(-.5+3*z)/100);
 const t=guideJet(taylorGuide([7,3,4],{center:150}),150);assert.deepEqual([t.value,t.d1,t.d2],[7,3,4]);
});
test('quintic segments and captured guides return the actual interpolant derivatives',()=>{
 const s=sampledGuide([{x:120,y:0,d1:0,d2:0},{x:160,y:64000,d1:4800,d2:240}]);
 for(const [x,y,d1,d2] of [[120,0,0,0],[135,3375,675,90],[160,64000,4800,240]]){
  const q=guideJet(s,x);assert.deepEqual([q.value,q.d1,q.d2],[y,d1,d2]);
 }
 const c=captureGuide(x=>x*x,{domain:[120,160],count:3,derivative:x=>2*x,curvature:()=>2});
 const q=guideJet(c,135);assert.deepEqual([q.value,q.d1,q.d2],[18225,270,2]);
 assert.throws(()=>guideJet(s,119),RangeError);
});
