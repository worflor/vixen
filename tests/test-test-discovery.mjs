import test from 'node:test';
import assert from 'node:assert/strict';
import {basename} from 'node:path';
import {discoverTests} from '../tools/run-js-tests.mjs';

test('the test entry point discovers new root regression suites automatically',()=>{
 const names=discoverTests().map(p=>basename(p));
 for(const required of ['test-additive.mjs','test-numeric-extremes.mjs','test-deep-composition.mjs','test-test-discovery.mjs'])assert.ok(names.includes(required));
 assert.equal(new Set(names).size,names.length);
 assert.deepEqual(names,[...names].sort());
});
