import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {Wide,WIDE_FORMAT} from '../vixen-wide.mjs';
const data=JSON.parse(readFileSync(new URL('./fixtures/scaled-binary64-1/rounding.json',import.meta.url),'utf8'));
test('scaled arithmetic matches every frozen independent Fraction rounding case',()=>{
 assert.equal(data.format,WIDE_FORMAT);assert.equal(data.cases.length,768);const scale=BigInt(data.commonScale),bits=new DataView(new ArrayBuffer(8));
 for(const c of data.cases){const a=Wide.fromNumber(c.a).scale(scale),b=Wide.fromNumber(c.b).scale(scale);let r=a[c.op](b);
  if(c.op==='add')r=r.scale(-scale);else if(c.op==='mul')r=r.scale(-2n*scale);
  const n=r.toNumber();assert.notEqual(n,null);bits.setFloat64(0,n,false);assert.equal(bits.getBigUint64(0,false).toString(16).padStart(16,'0'),c.expectedBits);
 }
});
test('scaled decoding refuses malformed exponent tokens without normalization',()=>{
 for(const exponent2 of ['-0','+1','01','1\n',' 1','1 ','١'])assert.throws(()=>Wide.decode({format:WIDE_FORMAT,sign:1,significand:1,exponent2}));
});
