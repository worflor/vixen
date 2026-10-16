import test from 'node:test';
import assert from 'node:assert/strict';
import {create,polynomialGuide,restore,couple} from '../vixen.mjs';

test('thousands of identity influences evaluate without recursive stack growth',()=>{
 const base=create(),guide=polynomialGuide([1],{domain:[1,50000]});let f=base;
 for(let i=0;i<6000;i++)f=f.influence(guide,{at:120,amount:0});
 assert.deepEqual(f.sample(200),base.sample(200));
});

test('deep mixed composition snapshots, JSON replay and forks are stack safe',()=>{
  let c=create({seed:'mixed-replay'});const p=c,g=polynomialGuide([.1]);
  for(let i=0;i<1200;i++)c=c.influence(g,{at:120,amount:0}).impulse(120,0,10);
  const snapshot=c.snapshot();assert.equal(snapshot.algorithm,'vixen-graph/1');
  const replay=restore(JSON.parse(JSON.stringify(snapshot)));
  for(const x of [50,130,200])assert.deepEqual(replay.sample(x),p.sample(x));
  const f=c.fork({at:200,operation:'deep',handoffWidth:4});
  const expected=p.fork({at:200,operation:'deep',handoffWidth:4});
  for(const x of [130,201,250])assert.deepEqual(f.sample(x),expected.sample(x));
  const tail=restore(JSON.parse(JSON.stringify(c.tail(200))));
  assert.deepEqual(tail.sample(250),c.sample(250));
});

test('deep coupled projections use a shared replay DAG and retain tail transparency',()=>{
 const p=create({seed:'group-a'}),q=create({seed:'group-b'});let c=p;
 const count=600;
 for(let i=0;i<count;i++)c=couple([c,q],.5,[120,200]).curves[0];
 const snap=c.snapshot();assert.equal(snap.algorithm,'vixen-graph/1');
 assert.ok(snap.nodes.length<3*count+3);
 const replay=restore(JSON.parse(JSON.stringify(snap)));
 assert.deepEqual(replay.sample(150),c.sample(150));
 const tail=restore(JSON.parse(JSON.stringify(c.tail(250))));
 assert.deepEqual(tail.sample(300),p.sample(300));
});

test('generic fork rebuilding preserves event chronology and effective-domain handoffs',()=>{
 const g=polynomialGuide([.1]),c=create().influence(g,{at:200}).impulse(240,.1,10);
 assert.throws(()=>c.fork({at:150,operation:'back',handoffWidth:4}),RangeError);
 const short=create().influence(polynomialGuide([.1],{domain:[1,180]}),{at:120}).impulse(140,.1,10);
 const f=short.fork({at:175,operation:'edge'});
 assert.equal(f.lineage.at(-1).handoffWidth,5);
 assert.throws(()=>short.fork({at:181,operation:'outside'}),RangeError);
});
