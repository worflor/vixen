import test from 'node:test';
import assert from 'node:assert/strict';
import {create,restore,ALGORITHM} from '../vixen.mjs';
test('replay envelope rejects unknown fields across every supported algorithm',()=>{
 for(const algorithm of [ALGORITHM,'vixen-hyperfield-c2/1','vixen-hyperfield-c2/2','vixen-hyperfield-c2/3']){
  assert.throws(()=>restore({algorithm,config:{seed:'x'},surprise:1}));
  assert.doesNotThrow(()=>restore({algorithm,config:{seed:'x'}}));
 }
});
test('replay envelope requires own fields and rejects arrays',()=>{
 assert.throws(()=>restore(Object.create({algorithm:ALGORITHM,config:{seed:'proto'}})));
 const array=[];array.algorithm=ALGORITHM;array.config={seed:'array'};assert.throws(()=>restore(array));
 const valid=Object.assign(Object.create(null),{algorithm:ALGORITHM,config:{seed:'plain'}});assert.equal(restore(valid).config.seed,'plain');
});
