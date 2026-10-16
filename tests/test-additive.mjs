import test from 'node:test';
import assert from 'node:assert/strict';
import {create,polynomialGuide,restore} from '../vixen.mjs';

const near=(a,b)=>assert.ok(Math.abs(a-b)<=1e-12*Math.max(1,Math.abs(a),Math.abs(b)),`${a} != ${b}`);
test('additive influence adds weighted deviation without erasing the parent',()=>{
  const p=create({seed:'additive'}),g=polynomialGuide([.2]);
  const c=p.influence(g,{at:120,amount:.6,handoffWidth:10,until:170,releaseWidth:10,mode:'add'});
  for(const [x,w] of [[120,0],[125,.3],[130,.6],[150,.6],[175,.3],[180,0],[300,0]]){
    const a=p.sample(x),b=c.sample(x);
    if(w===0)assert.deepEqual(b,a);
    else near(b.unconstrainedValue,a.unconstrainedValue+a.base*(w*.2));
  }
  assert.equal(c.snapshot().mode,'add');
  assert.equal(c.snapshot().reference,'baseline');
  const replay=restore(JSON.parse(JSON.stringify(c.snapshot())));
  for(const x of [120,125,150,175,180,300])assert.deepEqual(replay.sample(x),c.sample(x));
});

test('overlapping additive guides sum; forks change the course, not the increment',()=>{
  const p=create({seed:'overlap'}),g=polynomialGuide([.2]);
  const a=p.influence(g,{at:120,amount:.6,handoffWidth:10,until:170,releaseWidth:10,mode:'add'});
  const b=a.influence(polynomialGuide([-.3]),{at:140,amount:.4,handoffWidth:10,until:190,releaseWidth:10,mode:'add'});
  for(const x of [150,160])near(b.sample(x).unconstrainedValue,p.sample(x).unconstrainedValue+p.sample(x).base*(.6*.2+.4*(-.3)));
  const fork={at:150,operation:'new-course',handoffWidth:4};
  const changed=a.fork(fork),native=p.fork(fork);
  for(const x of [149,150,152,156,175,180,300]){
    const w=a.influenceAt(x).weight,s=native.sample(x);
    near(changed.sample(x).unconstrainedValue,s.unconstrainedValue+s.base*(w*.2));
  }
  const tail=restore(a.tail(150));
  for(const x of [150,175,180,300])assert.deepEqual(tail.sample(x),a.sample(x));
});
test('zero additions are exact identities, including boundary metadata',()=>{
  for(const floor of ['none','zero','baseline']){
    const p=create({floor}),a=p.influence(polynomialGuide([0]),{at:120,mode:'add'});
    for(const x of [1,120,125,140,500])assert.deepEqual(a.sample(x),p.sample(x));
  }
});
test('additive units, diagnostics and strict replay version are explicit',()=>{
  const p=create(),g=polynomialGuide([.2]),options={at:120,handoffWidth:10,amount:.5,mode:'add'};
  const a=p.influence(g,options),backbone=p.influence(g,{...options,reference:'backbone'});
  assert.deepEqual(a.sample(150),backbone.sample(150));
  near(a.influenceAt(150).increment,p.sample(150).base*.1);
  assert.equal(a.influenceAt(150).targetValue,null);
  assert.equal(a.influenceAt(150).followingError,null);
  const raw=p.influence(polynomialGuide([10]),{...options,reference:'value'});
  near(raw.sample(150).unconstrainedValue,p.sample(150).unconstrainedValue+5);
  const s=JSON.parse(JSON.stringify(a.snapshot()));
  for(const bad of [{...s,mode:'blend'},{...s,mode:null},{...s,algorithm:'vixen-influence-c2/2'}])assert.throws(()=>restore(bad));
  for(const mode of [null,undefined,{},0,'ADD','subtract'])assert.throws(()=>p.influence(g,{...options,mode}));
  assert.deepEqual(p.influence(g,{at:120,mode:'blend'}).snapshot(),p.influence(g,{at:120}).snapshot());
});

test('a nonconstant guide crossing zero does not skip the boundary transform',()=>{
  const p=create({seed:2,floor:'baseline',boundarySoftness:1});
  const c=p.influence(polynomialGuide([-150,1]),{at:120,handoffWidth:10,amount:.5,mode:'add'});
  const s=c.sample(150);
  for(const x of [150-1e-8,150+1e-8])assert.ok(Math.abs(c.sample(x).value-s.value)/s.base<1e-6);
});
