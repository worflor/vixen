import test from 'node:test';
import assert from 'node:assert/strict';
import {createCoherent} from '../vixen.mjs';
import {createExtended,restoreExtended,MAX_DOMAIN_END} from '../vixen-extended.mjs';

const samePrimal=(a,b)=>{for(const [k,v] of Object.entries(b))assert.deepEqual(a[k],v,k);};
test('finite samples retain coherent5 primal fields and explicit moving coordinates',()=>{
  const legacy=createCoherent({seed:'native-test'}),extended=createExtended({seed:'native-test'});
  samePrimal(extended.sample(250),legacy.sample(250));
  const h=Array.from({length:16},(_,i)=>(i-7)/20);
  samePrimal(extended.at(250,h),legacy.at(250,h));
  assert.deepEqual(extended.normalizedDomain,[1,MAX_DOMAIN_END]);
});
test('far normalized samples retain finite scaled state when raw values overflow',()=>{
  const c=createExtended(),p=c.sampleNormalized(1e6);
  assert.equal(p.base,null); assert.equal(p.value,null); assert.ok(p.scaledBase); assert.ok(p.scaledValue);
  assert.ok(Number.isFinite(p.signedLogValue.logAbs));
  assert.throws(()=>c.sample(1e6),/Raw base is unavailable/);
  const h=Array(16).fill(0);assert.equal(c.atNormalized(1e6,h).x,1e6);
});
test('far overlapping forks, historical reads, JSON replay, and active and settled tails',()=>{
  const root=createExtended({seed:'fork-test'});
  const a=root.fork({at:1e6,operation:'a',handoffWidth:100});
  const b=a.fork({at:1e6+20,operation:'b',handoffWidth:100});
  const old=a.sampleNormalized(1e6+30),now=b.sampleNormalized(1e6+50);
  assert.deepEqual(a.sampleNormalized(1e6+30),old);
  const replay=restoreExtended(JSON.parse(JSON.stringify(b.snapshot())));
  assert.deepEqual(replay.sampleNormalized(1e6+50),now);
  const active=restoreExtended(JSON.parse(JSON.stringify(b.tail(1e6+10))));
  assert.deepEqual(active.sampleNormalized(1e6+50),now);
  const activeFuture=b.fork({at:1e6+150,operation:'future'});
  const replayFuture=active.fork({at:1e6+150,operation:'future'});
  assert.deepEqual(replayFuture.sampleNormalized(1e6+160),activeFuture.sampleNormalized(1e6+160));
  const settled=restoreExtended(JSON.parse(JSON.stringify(b.tail(1e6+200))));
  assert.deepEqual(settled.sampleNormalized(1e6+210),b.sampleNormalized(1e6+210));
  const settledFuture=b.fork({at:1e6+210,operation:'settled-future'});
  const restoredFuture=settled.fork({at:1e6+210,operation:'settled-future'});
  assert.deepEqual(restoredFuture.sampleNormalized(1e6+220),settledFuture.sampleNormalized(1e6+220));
});
test('deep fork histories evaluate iteratively without stack growth',()=>{
  let c=createExtended();
  for(let i=0;i<5000;i++)c=c.fork({at:1e6,operation:`deep-${i}`,handoffWidth:16});
  assert.equal(c.lineage.length,5000);
  assert.equal(c.sampleNormalized(1e6+1).x,1e6+1);
  assert.equal(restoreExtended(JSON.parse(JSON.stringify(c.snapshot()))).lineage.length,5000);
});

test("extended checkpoint coordinates are typed and short explicit domains are honored",()=>{
 const cp=createExtended().tail(120);for(const from of [NaN,Infinity,"120",true,undefined])assert.throws(()=>restoreExtended({...cp,from}));
 const short=createExtended({domainEnd:1000});assert.deepEqual(short.domain,[1,1000]);assert.throws(()=>short.sampleNormalized(1001));
});

test('extended checkpoints refuse an unportable folded counter',()=>{
 const cp={...createExtended().tail(120),seed:1,leafSeed:1,foldedCount:Number.MAX_SAFE_INTEGER};
 const c=restoreExtended(cp).fork({at:130,operation:'counter',handoffWidth:1});
 assert.throws(()=>c.tail(140),/count|safe|represent/i);
});
