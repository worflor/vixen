import test from 'node:test';
import assert from 'node:assert/strict';
import {create,restore} from '../vixen.mjs';
import {append,before,entries} from '../vixen-timeline.mjs';

test('persistent index shares history and searches strict event prefixes',()=>{
 let tree=null; const old=[];
 for(let i=0;i<10000;i++){if(i%1000===0)old.push([tree,i]);tree=append(tree,{entry:{at:100+Math.floor(i/2)},id:i});}
 assert.equal(tree.size,10000);assert.ok(tree.height<30);
 for(const [root,n] of old)assert.equal([...entries(root)].length,n);
 assert.equal(before(tree,101).next().value.id,1);
 assert.equal(before(tree,100).next().done,true);
 assert.equal(before(tree,10000).next().value.id,9999);
});
test('five thousand forks preserve random-access history without a recursive depth limit',()=>{
 const root=create({seed:'deep-index'});let f=root;const saved=[];
 for(let i=0;i<5000;i++){
   const at=200+i;f=f.fork({at,operation:'event:'+i,handoffWidth:.25});
   if(i%500===0)saved.push([at+.5,f.sample(at+.5)]);
 }
 assert.equal(f.lineage.length,5000);
 for(const [x,p] of saved)assert.deepEqual(f.sample(x),p);
 assert.deepEqual(f.sample(100),root.sample(100));
 const replay=restore(JSON.parse(JSON.stringify(f.snapshot())));
 for(const x of [100,200,200.125,999.125,3000.125,5199.125,5300])assert.deepEqual(f.sample(x),replay.sample(x));
});
test('one thousand overlapping handoffs do not overflow the call stack',()=>{
 let f=create({seed:'overlap-index'});
 for(let i=0;i<1000;i++)f=f.fork({at:200,operation:'overlap:'+i,handoffWidth:10});
 assert.ok(Number.isFinite(f.sample(205).value));
 assert.deepEqual(f.sample(200),create({seed:'overlap-index'}).sample(200));
});
