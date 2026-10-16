import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {restore} from '../vixen.mjs';
const data=JSON.parse(readFileSync(new URL('./fixtures/v5/composed-graphs.json',import.meta.url),'utf8'));
test('frozen mixed/shared/unary-fork graph snapshots and checkpoints replay exactly',()=>{
 for(const c of data.cases){
  const entries=[[restore(c.snapshot),1],...c.tails.map(t=>[restore(t.snapshot),t.from])];
  for(const [curve,from] of entries)for(let i=0;i<c.coordinates.length;i++)if(c.coordinates[i]>=from)assert.deepEqual(curve.sample(c.coordinates[i]),c.samples[i]);
 }
});
