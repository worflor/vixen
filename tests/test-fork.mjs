import test from 'node:test';
import assert from 'node:assert/strict';
import {create,restore,ALGORITHM} from '../vixen.mjs';
const near=(a,b,t=1e-10)=>assert.ok(Math.abs(a-b)<=t*Math.max(1,Math.abs(a),Math.abs(b)),`${a} != ${b}`);
test('fork preserves exact parent history at every coordinate and nested fork inherits its actual parent',()=>{
 const root=create({seed:'vixen',introVariation:1,velocity:Array(16).fill(.1)}),a=root.fork({at:150,operation:'open-door'}),b=a.fork({at:250,operation:'take-key'});
 assert.ok(Object.isFrozen(a));assert.notEqual(a,root);assert.deepEqual(a.location(300),root.location(300));
 for(const x of [1,42,100,150]){assert.deepEqual(a.sample(x),root.sample(x));assert.deepEqual(a.at(x,Array(16).fill(2)),root.at(x,Array(16).fill(2)));}
 for(const x of [1,150,200,250])assert.deepEqual(b.sample(x),a.sample(x));
 assert.notEqual(a.sample(240).value,root.sample(240).value);
 for(const x of [151,158,166,200,250,270])near(b.sample(x).value,b.at(x,b.location(x)).value);
});
test('operation-derived futures replay full lineage, preserve typed seeds, and validate untrusted snapshots',()=>{
 const root=create({seed:'vixen'}),a=root.fork({at:200,operation:'branch'}),same=root.fork({at:200,operation:'branch'}),other=root.fork({at:200,operation:'different'});
 assert.deepEqual(a.snapshot(),same.snapshot());assert.notEqual(a.config.seed,other.config.seed);
 assert.notEqual(create({seed:'0'}).fork({at:200,operation:'x'}).config.seed,create({seed:0}).fork({at:200,operation:'x'}).config.seed);
 const b=a.fork({at:300.125,operation:'🦊'}),replay=restore(JSON.parse(JSON.stringify(b.snapshot())));
 for(const x of [1,42,200,210,250,300.125,310,400])assert.deepEqual(b.sample(x),replay.sample(x));
 for(const opts of [{at:0,operation:'x'},{at:50000,operation:'x'},{at:200,operation:''},{at:200,operation:3},{at:200,operation:'x',handoffWidth:0},{at:200,operation:'x',surprise:1}])assert.throws(()=>root.fork(opts));
 assert.throws(()=>a.fork({at:199,operation:'rewind'}));
 const bad=b.snapshot();assert.throws(()=>restore({...bad,lineage:Array(33).fill({at:300,operation:'x',handoffWidth:16})}));
 assert.throws(()=>restore({...bad,lineage:[{at:200,operation:'x',handoffWidth:16,unknown:true}]}));
 const cyclic={algorithm:ALGORITHM,config:{}};cyclic.config.seed=cyclic;assert.throws(()=>restore(cyclic));
});
test('quintic fork matches value slope curvature at both ends and constraints/adapters remain coherent',()=>{
 for(const floor of ['none','zero','baseline','backbone']){const p=create({floor,ceilingMargin:.5,boundarySoftness:.3}),f=p.fork({at:200,operation:'door',handoffWidth:20});
  for(let x=190;x<=240;x+=.25){const r=f.sample(x);if(r.floorValue!==null)assert.ok(r.value>=r.floorValue);if(r.ceilingValue!==null)assert.ok(r.value<=r.ceilingValue);near(r.activity,.5+.5*Math.tanh(r.deviation));near(r.value,r.base*(1+r.deviation));}
  const target=create(f.config),h=.001;
  for(const [x,reference] of [[200,p],[220,target]]){
   assert.deepEqual(f.sample(x),reference.sample(x));
   const derivative=g=>(g.sample(x+h).value-g.sample(x-h).value)/(2*h);
   const curvature=g=>(g.sample(x+h).value-2*g.sample(x).value+g.sample(x-h).value)/(h*h);
   near(derivative(f),derivative(reference),1e-5);near(curvature(f),curvature(reference),1e-3);
  }
 }
});
