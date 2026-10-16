import test from 'node:test';
import assert from 'node:assert/strict';
import {Wide} from '../vixen-wide.mjs';

test('extended exponents stay explicit and operations retain binary64 significands',()=>{
 const a=Wide.fromNumber(1.5).scale(1000000000000n),b=Wide.fromNumber(2).scale(1000000000000n);
 assert.equal(a.add(b).scale(-1000000000000n).toNumber(),3.5);
 assert.equal(a.mul(b).scale(-2000000000000n).toNumber(),3);
 assert.equal(a.div(b).toNumber(),.75);
 assert.equal(a.add(a.neg()).toNumber(),0);
 assert.equal(a.toNumber(),null);
 assert.equal(Wide.decode(JSON.parse(JSON.stringify(a))).cmp(a),0);
});
test('power-of-two lower spacing, subnormal ties and underflow are handled explicitly',()=>{
 assert.equal(Wide.fromNumber(1).add(Wide.fromNumber(-.375).scale(-52n)).toNumber(),1-2**-53);
 assert.equal(Wide.fromNumber(3*Number.MIN_VALUE).scale(-1n).toNumber(),2*Number.MIN_VALUE);
 assert.equal(Wide.fromNumber(Number.MIN_VALUE).scale(-1n).toNumber(),null);
});
test('scaled JSON is canonical, finite-significand and immutable',()=>{
 const a=Wide.fromNumber(-2).scale(1n<<200n),s=a.toJSON();assert.equal(s.sign,-1);assert.equal(s.significand,1);
 assert.equal(Wide.decode(s).cmp(a),0);assert.throws(()=>{a.m=0n;},TypeError);
 for(const exponent2 of ['01','-0','+1','1e3','1\n','1\r','1\u2028'])assert.throws(()=>Wide.decode({...s,exponent2}));
 assert.throws(()=>Wide.fromNumber(Infinity));
});

test('addition keeps the gap-54 lower-neighbour decision below powers of two',()=>{
 const a=new Wide(1n<<52n),lower=new Wide((1n<<53n)-1n,-1n),small=new Wide(-((1n<<52n)+1n),-54n);
 assert.deepEqual(a.add(small).toJSON(),lower.toJSON());
 assert.deepEqual(a.neg().add(small.neg()).toJSON(),lower.neg().toJSON());
 assert.deepEqual(a.add(new Wide(-1n,-2n)).toJSON(),a.toJSON());
 assert.deepEqual(a.add(new Wide(-((1n<<53n)-1n),-55n)).toJSON(),a.toJSON());
});
