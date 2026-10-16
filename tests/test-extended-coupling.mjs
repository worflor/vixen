import test from 'node:test';
import assert from 'node:assert/strict';
import {createExtended,extendedPolynomialGuide,coupleExtended,restore,Wide} from '../vixen.mjs';
test('extended coupling preserves scaled values, history, replay and projection boundaries',()=>{
 const a=createExtended({seed:'coupling-a'}),b=createExtended({seed:'coupling-b'}),group=coupleExtended([a,b],.6,[1e9,1e9+100]);
 for(const x of [1e9-1,1e9,1e9+30,1e9+100]){
  const out=group.sampleNormalized(x),saved=restore(JSON.parse(JSON.stringify(group.snapshot()))).sampleNormalized(x);
  assert.deepEqual(saved,out);
  if(x<=1e9||x>=1e9+100)assert.deepEqual(out,[a.sampleNormalized(x),b.sampleNormalized(x)]);
  for(const p of out)assert.equal(Wide.decode(p.scaledBase).sign,1);
 }
 const g=extendedPolynomialGuide([1]);
 const tiny=a.influence(g,{at:120,amount:1,reference:'value'});
 assert.equal(coupleExtended([tiny,tiny],1,[1e9,1e9+100]).sampleNormalized(1e9+50)[0].value,1);
 assert.throws(()=>group.curves[0].fork({at:1e9+120,operation:'ambiguous'}),/ambiguous/);
 assert.throws(()=>group.curves[0].impulse({at:1e9+120,magnitude:.1,relaxation:10}).fork({at:1e9+150,operation:'ambiguous'}),/ambiguous/);
 const tail=restore(group.curves[0].tail(1e9+110));assert.deepEqual(tail.sampleNormalized(1e9+130),a.sampleNormalized(1e9+130));
});
