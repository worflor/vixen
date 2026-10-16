import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {restore} from '../vixen.mjs';
const fixture=JSON.parse(readFileSync(new URL('./fixtures/v1.4.0/legacy-timeline-constraints.json',import.meta.url)));
test('frozen legacy4 constrained timelines retain every historical sample and active tail',()=>{
 assert.equal(fixture.cases.length,6);
 for(const row of fixture.cases){
  const data=row.javascript,c=restore(data.snapshot),tail=restore(data.tail);
  assert.deepEqual(c.snapshot(),data.snapshot);
  fixture.x.forEach((x,i)=>{assert.deepEqual(c.sample(x),data.samples[i]);if(x>=150)assert.deepEqual(tail.sample(x),data.samples[i]);});
 }
});
