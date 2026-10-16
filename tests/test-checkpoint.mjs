import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {create,restore,TAIL_ALGORITHM} from '../vixen.mjs';
import {sha256,historyWitness} from '../vixen-checkpoint.mjs';

test('browser-compatible SHA-256 agrees with the platform implementation',()=>{
 for(const s of ['', 'abc', '🦊\ud800', 'x'.repeat(55),'x'.repeat(56),'x'.repeat(64),'x'.repeat(100000)])assert.equal(sha256(s),createHash('sha256').update(s).digest('hex'));
 assert.match(historyWitness(null,[{at:200.125,operation:'🦊\ud800',handoffWidth:16}]),/^[a-f0-9]{64}$/);
});
test('checkpoints retain active overlaps, future events and bitwise samples',()=>{
 let f=create({seed:'tail:🦊',introVariation:1,floor:'backbone',ceilingMargin:.5});
 for(let i=0;i<150;i++)f=f.fork({at:200+i*2,operation:'step:'+i,handoffWidth:i%3?16:100});
 for(const from of [1,100,200,250.125,499,520,1000]){
  const spec=f.tail(from);assert.equal(spec.algorithm,TAIL_ALGORITHM);
  const g=restore(JSON.parse(JSON.stringify(spec)));
  assert.equal(g.domain[0],from);
  for(const x of [from,from+.001,from+2,from+30,2000]){
   assert.deepEqual(g.sample(x),f.sample(x));
   assert.deepEqual(g.at(x,Array(16).fill(.125)),f.at(x,Array(16).fill(.125)));
   assert.deepEqual(restore(JSON.parse(JSON.stringify(g.snapshot()))).sample(x),f.sample(x));
  }
  if(from>1){assert.throws(()=>g.sample(from-.001));assert.throws(()=>g.at(from-.001,Array(16).fill(0)));assert.throws(()=>g.inspect(from-.001));}
 }
 const compact=f.tail(1000);assert.equal(compact.lineage.length,0);assert.equal(compact.foldedCount,150);
 const g=restore(compact),later=g.fork({at:1100,operation:'new'}),full=f.fork({at:1100,operation:'new'});
 for(const x of [1000,1100,1108,1116,2000])assert.deepEqual(later.sample(x),full.sample(x));
 const folded=restore(later.tail(1200));assert.equal(folded.snapshot().foldedCount,151);assert.deepEqual(folded.sample(1300),full.sample(1300));
});
test('unfolded checkpoints preserve typed seeds and malformed checkpoints fail closed',()=>{
 for(const seed of ['0',0]){const f=create({seed}),g=restore(f.tail(150));assert.equal(g.config.seed,seed);assert.deepEqual(g.fork({at:200,operation:'x'}).sample(300),f.fork({at:200,operation:'x'}).sample(300));}
 const s=create().tail(100);
 for(const bad of [{...s,unknown:1},{...s,from:NaN},{...s,leafSeed:-1},{...s,witness:'bad'},{...s,foldedCount:-1},{...s,seed:0},{...s,lineage:[null]}])assert.throws(()=>restore(bad));
});
