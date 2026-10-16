import test from 'node:test';
import assert from 'node:assert/strict';
import * as V from '../vixen.mjs';
import {leafJet,nativeNormalized} from '../vixen-calculus.mjs';
const leaf=c=>({config:c.config,dips:c.dips,ridges:c.ridges.bind(c),sample:c.sample.bind(c),at:c.at.bind(c),domain:c.domain});
const near=(a,b,t=2e-5)=>assert.ok(Math.abs(a-b)<=t*Math.max(1,Math.abs(a),Math.abs(b)),`${a} != ${b}`);
test('leaf jets differentiate the real moving sampler and preserve its primal sample',()=>{
 const c=leaf(V.create({seed:'calculus',introVariation:.6,stabilizer:.35,openingEndpoint:80,velocity:Array(16).fill(.08)}));
 const x=180,h=.01,p=leafJet(c,x),s=(c.sample(x+h).value-c.sample(x-h).value)/(2*h),k=(c.sample(x+h).value-2*c.sample(x).value+c.sample(x-h).value)/(h*h);
 assert.deepEqual(p.sample,c.sample(x));near(p.slope,s,2e-4);near(p.curvature,k,3e-3);assert.equal(p.differentiability.value,true);assert.ok(p.fields.score);
});
test('explicit coordinates hold the path fixed and are validated',()=>{
 const c=leaf(V.create({seed:9,openingEndpoint:140})),h=Array(16).fill(.4),x=230,eps=.01,p=leafJet(c,x,h),d=(c.at(x+eps,h).value-c.at(x-eps,h).value)/(2*eps);
 near(p.slope,d,2e-4);assert.throws(()=>leafJet(c,x,[1,2]));assert.throws(()=>leafJet(V.create().fork({at:120,operation:'branch'}),130));
});
test('opening score branch and a hard constraint are reported explicitly',()=>{
 const c=leaf(V.create({seed:'edge',openingEndpoint:100})),p=leafJet(c,100);assert.equal(p.fields.score.d1,null);assert.equal(p.differentiability.sample,false);assert.equal(p.differentiability.value,true);
 const hard=leaf(V.create({floor:'baseline',boundarySoftness:0})),q=leafJet(hard,100);assert.ok(Number.isFinite(q.slope));assert.ok(Number.isFinite(q.curvature));assert.equal(q.differentiability.value,true);near(q.slope,2*Math.E+(25/16)*58*Math.exp(-(58**2)/32)+(19/7)*29*Math.exp(-(29**2)/28),1e-12);
});
test('normalized records preserve real samples and signed zero and negative multipliers',()=>{
 let found;for(let seed=0;seed<12&&!found;seed++){const c=leaf(V.create({seed,floor:'zero',strength:2,contrast:16,boundarySoftness:0}));for(let x=120;x<900;x+=.5){const z=nativeNormalized(c,x);if(z.value===0){found={c,x,z};break;}}}
 assert.ok(found,'seeded sampler should exercise the zero floor');assert.equal(found.z.multiplier,0);assert.deepEqual(found.z.signedLogValue,{sign:0,logAbs:null});
 const free=leaf(V.create({seed:found.c.config.seed,strength:2,contrast:16})),neg=nativeNormalized(free,found.x);assert.ok(neg.multiplier<0);assert.equal(neg.signedLogValue.sign,-1);
});
test('large-domain baseline and seeded lattice remain finite in the normalized representation',()=>{
 const c=leaf(V.create({seed:'far',openingEndpoint:50000,introVariation:1})),z=nativeNormalized(c,2**40);
 assert.equal(z.x,2**40);assert.ok(Number.isFinite(z.logBase));assert.ok(Number.isFinite(z.score));assert.equal(z.base,null);assert.equal(z.value,null);assert.ok(Number.isFinite(z.signedLogValue.logAbs));
 const p=nativeNormalized(c,50001),t=1+(50001-1)*99/(50000-1),core=Math.log(t)+t/100;assert.ok(p.logBase<core);
});

test('normalized authored continuation is independent of opening-only variation after release',()=>{
 const x=50000.001;
 const a=leaf(V.create({openingEndpoint:50000,introVariation:0})),b=leaf(V.create({openingEndpoint:50000,introVariation:1}));
 assert.equal(nativeNormalized(a,x).logBase,nativeNormalized(b,x).logBase);
});

test('normalized history and active blends retain their actual scalar samples',()=>{
 const c=V.create({seed:'history'}).fork({at:120,handoffWidth:20,operation:'later'}),z=c.sampleNormalized(110),p=c.sample(110);
 near(z.multiplier,p.multiplier,1e-12);near(z.score,p.score,1e-12);
 const a=c.sampleNormalized(130),b=c.sample(130);near(a.multiplier,b.multiplier,1e-12);near(a.score,b.score,1e-12);assert.equal(a.value,b.value);
});
