import test from 'node:test';
import assert from 'node:assert/strict';
import {create} from '../vixen.mjs';

test('native root provides a real enclosure instead of relying on spatial-slope metadata',()=>{
 const f=create({seed:'native-enclosure',velocity:Array(16).fill(.03)}).fork({at:120,operation:'a',handoffWidth:45}).fork({at:130,operation:'b',handoffWidth:30});
 for(const [a,b] of [[90,110],[119,121],[125,150],[145,166],[190,191]]){
  const box=f.encloseDeviation(a,b);
  for(let i=0;i<=20;i++){const x=a+(b-a)*i/20,d=f.sample(x).deviation;assert.ok(box[0]<=d&&d<=box[1],`${a}..${b}: ${d} outside ${box}`);}
 }
});

test('native first-passage can prove absence and isolate an actual excursion',()=>{
 const constant=create({contrast:0});assert.equal(constant.nextCrossing(.2,100,1000).status,'absent');
 const f=create({seed:'native-enclosure'});const r=f.nextCrossing(.2,100,250,{tolerance:1e-4,maxIntervals:4096});
 assert.equal(r.status,'found');assert.ok(r.interval[1]-r.interval[0]<=1e-4);
 assert.ok(r.endpointEnclosures[0][1]<.2&&r.endpointEnclosures[1][0]>.2 || r.endpointEnclosures[0][0]>.2&&r.endpointEnclosures[1][1]<.2);
});
test('derived queries never reuse an ancestor oracle as proof about a changed curve',()=>{
 const f=create({contrast:0}).impulse({at:120,magnitude:.5,attack:10,relaxation:80});
 assert.ok(f.sample(125).deviation>.2);
 assert.notEqual(f.nextCrossing(.2,110,150).status,'absent');
});



test('a custom evaluator does not inherit a native continuity claim',()=>{
 const controls={evaluate:x=>x<100.5?-1:1,enclose:(a,b)=>b<100.5?[-1,-1]:a>=100.5?[1,1]:[-1,1],tolerance:1e-4};
 assert.notEqual(create().nextCrossing(0,100,101,controls).status,'found');
});
