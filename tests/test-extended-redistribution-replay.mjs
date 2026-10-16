import test from 'node:test';
import assert from 'node:assert/strict';
import {createExtended,withExtendedRedistribution,restore} from '../vixen.mjs';
test('extended redistribution recipes replay and retired checkpoints collapse',()=>{
 const root=createExtended({seed:29}),curve=withExtendedRedistribution(root,{from:120,until:220,coefficients:[1,-2,.25],magnitude:.2});
 assert.deepEqual(restore(JSON.parse(JSON.stringify(curve.snapshot()))).sampleNormalized(167),curve.sampleNormalized(167));
 assert.deepEqual(restore(JSON.parse(JSON.stringify(curve.tail(160)))).sampleNormalized(167),curve.sampleNormalized(167));
 assert.equal(curve.tail(230).algorithm,'vixen-tail-c2/4');
});
