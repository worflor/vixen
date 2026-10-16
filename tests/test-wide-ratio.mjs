import test from 'node:test';
import assert from 'node:assert/strict';
import {Wide} from '../vixen-wide.mjs';
test('exact rational construction rounds only once into the scaled 53-bit format',()=>{
 for(const [n,d,expected]of [[3n,2n,1.5],[-3n,2n,-1.5],[3n,-2n,-1.5],[1n,3n,1/3],[0n,7n,0]])assert.equal(Wide.fromRatio(n,d).toNumber(),expected);
 const even=(1n<<53n)+1n;assert.equal(Wide.fromRatio(even,2n).toNumber(),2**52);
 const shifted=Wide.fromRatio(3n,2n,1000000000000n);assert.equal(shifted.toJSON().exponent2,'1000000000000');assert.equal(shifted.toJSON().significand,1.5);
 assert.throws(()=>Wide.fromRatio(1n,0n));assert.throws(()=>Wide.fromRatio(1,2n));assert.throws(()=>Wide.fromRatio(1n,2n,1.5));
});
