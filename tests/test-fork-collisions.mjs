import test from 'node:test';
import assert from 'node:assert/strict';
import {create,restore,seed32} from '../vixen.mjs';
test('known finite-hash collision preserves distinct decision records without claiming unique futures',()=>{
 assert.equal(seed32('costarring'),1582148253);assert.equal(seed32('liquid'),1582148253);
 const root=create({seed:'collision-test'}),a=root.fork({at:200,operation:'costarring'}),b=root.fork({at:200,operation:'liquid'});
 assert.equal(a.config.seed,2260731076);assert.equal(a.config.seed,b.config.seed);assert.notDeepEqual(a.snapshot(),b.snapshot());
 for(const x of [1,199,200,208,216,233]){assert.deepEqual(a.sample(x),b.sample(x));assert.deepEqual(restore(a.snapshot()).sample(x),a.sample(x));}
});
