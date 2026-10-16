import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {gunzipSync} from 'node:zlib';
import {create,restore,ALGORITHM,seed32,hash32,navigationBasis} from '../vixen.mjs';
const fixture=name=>JSON.parse(gunzipSync(readFileSync(new URL(`./fixtures/v1.4.0/${name}.json.gz`,import.meta.url))));
function compare(actual,expected,path='root',tolerance=1e-10){
 if(typeof expected==='number'){
  assert.equal(typeof actual,'number',path);assert.ok(Number.isFinite(actual)&&Number.isFinite(expected),path);
  assert.ok(Math.abs(actual-expected)<=tolerance*Math.max(1,Math.abs(actual),Math.abs(expected)),`${path}: ${actual} != ${expected}`);return;
 }
 if(expected===null||typeof expected!=='object'){assert.equal(actual,expected,path);return;}
 assert.equal(Array.isArray(actual),Array.isArray(expected),path);
 assert.deepEqual(Object.keys(actual).sort(),Object.keys(expected).sort(),`${path} fields`);
 for(const key of Object.keys(expected))compare(actual[key],expected[key],`${path}.${key}`,tolerance);
}
test('immutable 1.4 field corpus: samples, typed seeds, uint32 hashes and navigation',()=>{
 const doc=fixture('cross-port');assert.equal(doc.cases.length,2400);
 assert.equal(doc.vixen.commit,'3f6a6683f54380516181f3a8f62958ae628957d0');
 for(const [i,c] of doc.cases.entries()){
  const f=restore({algorithm:ALGORITHM,config:c.config,lineage:c.lineage});
  compare(c.h?f.at(c.x,c.h):f.sample(c.x),c.expected.sample,`case ${i}`);
  assert.equal(f.config.seed,c.expected.seed,`seed ${i}`);
  assert.equal(hash32(seed32(c.config.seed),i,7),c.expected.hash,`hash ${i}`);
  assert.equal(f.bounds.unconstrainedSpatialSlope,c.expected.sensitivity,`sensitivity ${i}`);
  compare(navigationBasis(i%20),c.expected.plane,`navigation ${i}`,1e-12);
 }
});
test('immutable 1.4 influence corpus: all fields, guides, forks and constraints',()=>{
 const doc=fixture('influence-parity');assert.equal(doc.cases.length,1760);
 for(const [i,c] of doc.cases.entries()){
  const f=restore(c.snapshot);compare(c.h?f.at(c.x,c.h):f.sample(c.x),c.expected,`guided ${i}`);
 }
});
