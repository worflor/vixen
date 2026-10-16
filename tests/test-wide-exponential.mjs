import {readFileSync} from 'node:fs';
import test from 'node:test';
import assert from 'node:assert/strict';
import {wideExp} from '../vixen-exponential.mjs';
import {Wide} from '../vixen-wide.mjs';
test('reduced exponential agrees with representable native results and retains huge exponents',()=>{
 for(const x of [-700,-100,-1,-.1,0,.1,1,100,700]){const got=wideExp(x).toNumber(),want=Math.exp(x);assert.ok(Math.abs(got-want)<=8*Number.EPSILON*want);}
 assert.equal(wideExp(0).toNumber(),1);assert.equal(wideExp(Number.MIN_VALUE).toNumber(),1);
 const huge=wideExp(1e308),tiny=wideExp(-1e308);assert.equal(huge.toNumber(),null);assert.equal(tiny.toNumber(),null);assert.equal(huge.sign,1);assert.equal(tiny.sign,1);
 assert.equal(Wide.decode(huge.toJSON()).cmp(huge),0);assert.ok(BigInt(huge.toJSON().exponent2)>10n**307n);
 for(const x of [Infinity,-Infinity,NaN,'1',true])assert.throws(()=>wideExp(x));
});

test('reduced exponential matches frozen independent 420-digit reduction across binary64 arguments',()=>{
 const data=JSON.parse(readFileSync(new URL('./fixtures/scaled-binary64-1/exponential.json',import.meta.url),'utf8'));assert.equal(data.cases.length,21);
 for(const c of data.cases){const actual=wideExp(c.x),e=BigInt(c.e),m=BigInt(c.m),gap=actual.e-e;assert.ok(gap>=-1n&&gap<=1n);const a=actual.m<<(gap>0n?gap:0n),b=m<<(gap<0n?-gap:0n),distance=a>b?a-b:b-a;assert.ok(distance<=8n,`${c.x}: ${distance}`);}
});
