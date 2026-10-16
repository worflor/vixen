import assert from 'node:assert/strict';
import {create} from '../vixen.mjs';
import {interval,add,subtract,multiply,divide,hull,smooth5,boundaryRamp,tanh,exp,nativeDeviationEnclosure} from '../vixen-interval.mjs';

const contains=(q,x)=>q.lo<=x&&x<=q.hi;
assert.deepEqual(add(interval(1,2),interval(3,4)).lo<=4,true);
assert.deepEqual(add(interval(1,2),interval(3,4)).hi>=6,true);
assert.ok(contains(subtract(interval(1,2),interval(3,4)), -2));
assert.ok(contains(multiply(interval(-2,3),interval(-4,5)), -12));
assert.ok(contains(multiply(interval(0),interval(1e-300)),0));
assert.ok(contains(divide(interval(1),interval(3)),1/3));
assert.deepEqual(hull(interval(-1,0),interval(2,3)),interval(-1,3));
assert.throws(()=>divide(interval(1),interval(-1,1)),RangeError);
assert.throws(()=>multiply(interval(Number.MAX_VALUE),interval(2)),RangeError);
assert.ok(smooth5(interval(-2,2)).lo<=0&&smooth5(interval(-2,2)).hi>=1);
assert.ok(contains(smooth5(interval(.5)),.5));
assert.ok(contains(boundaryRamp(interval(1.5)),1.5));
assert.ok(contains(tanh(interval(-1000,1000)),0));
assert.ok(tanh(interval(1000)).lo<=1&&tanh(interval(1000)).hi===1);
assert.ok(exp(interval(-10000)).lo===0&&exp(interval(-10000)).hi<=Number.MIN_VALUE);
assert.throws(()=>exp(interval(1000)),RangeError);

function facade(curve){return {config:curve.config,dips:curve.dips,domain:curve.domain,sample:curve.sample.bind(curve),at:curve.at.bind(curve),ridges:curve.ridges.bind(curve)};}
for(const floor of ['none','zero','baseline','backbone']){
  const curve=create({seed:'interval-seeded-dense',openingEndpoint:74,introVariation:.6,floor,ceilingMargin:floor==='none'?null:1.25,boundarySoftness:.37,stabilizer:.41,strength:1.2,contrast:7.5});
  const leaf=facade(curve),h=Array.from({length:16},(_,i)=>(i-7)*.17);
  for(let i=0;i<=96;i++){
    const x=74+i*.43;
    const e=nativeDeviationEnclosure(leaf,x,x);
    assert.ok(contains(e,curve.sample(x).deviation),`${floor} latent sample at ${x}: ${JSON.stringify(e)}`);
    assert.ok(contains(nativeDeviationEnclosure(leaf,x,x,h),curve.at(x,h).deviation),`${floor} explicit sample at ${x}`);
    assert.ok(e.hi-e.lo<1e-7,`point enclosure should be useful at ${x}: ${JSON.stringify(e)}`);
    const threshold=curve.sample(x).deviation;
    assert.ok(e.lo<=threshold&&threshold<=e.hi,'point threshold tangency');
  }
  assert.ok(contains(nativeDeviationEnclosure(leaf,73,74),0));
  assert.throws(()=>nativeDeviationEnclosure(leaf,0,1),RangeError);
}
const openCurve=create({seed:23,openingEndpoint:20});
assert.deepEqual(nativeDeviationEnclosure(facade(openCurve),19,20),interval(0));
assert.ok(contains(nativeDeviationEnclosure(facade(openCurve),19,21),0));
assert.throws(()=>nativeDeviationEnclosure(facade(openCurve),20,21,[1,2]),TypeError);
