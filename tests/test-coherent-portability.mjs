import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {restore} from '../vixen.mjs';
const data=JSON.parse(readFileSync(new URL('./fixtures/v5/coherent-timelines.json',import.meta.url),'utf8'));
test('coherent frozen cases preserve every field and exact active/folded tail witness',()=>{
 let samples=0,tails=0;assert.equal(data.cases.length,8);
 for(const item of data.cases){const c=restore(item.snapshot);
  for(const expected of item.samples){assert.deepEqual(c.sample(expected.x),expected);samples++;}
  for(const tail of item.tails){assert.deepEqual(c.tail(tail.from),tail);assert.deepEqual(restore(tail).sample(tail.from+10),c.sample(tail.from+10));tails++;}
 }
 assert.equal(samples,88);assert.equal(tails,16);
});
