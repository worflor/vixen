import test from 'node:test';
import assert from 'node:assert/strict';
let module;try{module=await import('../vixen.mjs')}catch{}
test('kernel exists and quintic gate obeys boundary constraints',()=>{
 assert.ok(module,'Vixen reusable kernel is missing');
 const {smooth5}=module;assert.equal(smooth5(0),0);assert.equal(smooth5(1),1);assert.equal(smooth5(.5),.5);assert.equal(smooth5(-1),0);assert.equal(smooth5(2),1);
});
test('compact bump has exact centre, boundaries and exterior',()=>{
 assert.ok(module,'Vixen reusable kernel is missing');const {bump}=module;
 assert.equal(bump(0),1);assert.equal(bump(.5),27/64);assert.equal(bump(1),0);assert.equal(bump(-1),0);assert.equal(bump(2),0);
});
