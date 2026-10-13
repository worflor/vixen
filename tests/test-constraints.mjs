import test from 'node:test';
import assert from 'node:assert/strict';
import * as V from '../vixen.mjs';
import {create as old} from '../legacy/hyperfield-2.mjs';
import {create,baseline} from '../vixen.mjs';

test('zero floor forbids negatives without changing the authored opening',()=>{
 const c=create({seed:'vixen',floor:'zero'});
 for(let x=1;x<=600;x+=.25){const p=c.sample(x);assert.ok(p.value>=0);if(x<=100)assert.equal(p.value,baseline(x));assert.equal(p.activity,.5+.5*Math.tanh(p.deviation));}
});

test('trajectory floors respect their reference at every stabilization and softness',()=>{
 for(const floor of ['baseline','backbone'])for(const boundarySoftness of [0,.1,1])for(const stabilizer of [0,.5,1]){
  const c=create({floor,boundarySoftness,stabilizer});
  for(let x=1;x<=600;x+=.5){const p=c.sample(x),lo=floor==='baseline'?p.base:p.unconstrainedGuideValue;assert.ok(p.value>=lo);assert.equal(p.floorValue,lo);if(x<=100)assert.equal(p.value,baseline(x));if(stabilizer===1)assert.equal(p.value,p.guideValue);}
 }
});

test('floors and ceilings form a feasible band including zero-width collapse',()=>{
 for(const floor of ['none','zero','baseline','backbone'])for(const ceilingMargin of [0,.01,.5])for(const boundarySoftness of [0,.1,1])for(const strength of [0,2]){
  const c=create({floor,ceilingMargin,boundarySoftness,strength,stabilizer:.5});
  for(let x=1;x<=600;x+=2){const p=c.sample(x);assert.ok(p.value<=p.ceilingValue);if(p.floorValue!==null)assert.ok(p.value>=p.floorValue);if(x<=100||strength===0)assert.equal(p.value,baseline(x));if(ceilingMargin===0&&['baseline','backbone'].includes(floor))assert.equal(p.value,p.floorValue);}
 }
});

test('previous snapshots retain exact unrestricted samples',()=>{
 const cfg={seed:'vixen',stabilizer:.73};const a=old(cfg),b=V.restore({algorithm:a.algorithm,config:a.config});
 for(const x of [1,42,100,100.0001,144,300,4000,50000])assert.equal(a.sample(x).value,b.sample(x).value);
 assert.throws(()=>V.restore({algorithm:a.algorithm,config:{...a.config,floor:'zero'}}));
});
test('boundary ramp is a monotone C2 join, not an ordinary clamp',()=>{
 assert.equal(typeof V.boundaryRamp,'function');const q=V.boundaryRamp,h=1e-4;
 assert.equal(q(-1),0);assert.equal(q(2),2);assert.ok(q(.5)>0&&q(.5)<.5);
 for(const x of [0,1]){const d=(q(x+h)-q(x-h))/(2*h),dd=(q(x+h)-2*q(x)+q(x-h))/(h*h);assert.ok(Math.abs(d-x)<1e-5);assert.ok(Math.abs(dd)<.02);}
});

test('constraint domains, conservative bounds, zero strength and coordinate equivalence',()=>{
 for(const bad of [{floor:'oops'},{boundarySoftness:-.1},{boundarySoftness:Infinity},{ceilingMargin:-1},{ceilingMargin:NaN},{ceilingMargin:true}])assert.throws(()=>create(bad));
 for(const floor of ['none','zero','baseline','backbone'])for(const ceilingMargin of [null,0,.01,8]){
  const cfg={floor,ceilingMargin,boundarySoftness:1,velocity:Array.from({length:16},(_,i)=>Math.sin(i)*.15)},c=create(cfg),z=create({...cfg,strength:0});
  for(const x of [1,42,100,100.0001,144.25,233,300,4000,50000]){
   const p=c.sample(x),a=c.at(x,c.location(x));assert.ok(Math.abs(p.value-a.value)/Math.max(1,Math.abs(p.value),Math.abs(a.value))<1e-10);
   assert.ok(p.deviation>=c.bounds.deviation[0]-1e-12&&p.deviation<=c.bounds.deviation[1]+1e-12);
   assert.equal(z.sample(x).value,baseline(x));
  }
 }
});
