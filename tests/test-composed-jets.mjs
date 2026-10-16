import test from 'node:test';
import assert from 'node:assert/strict';
import {create,polynomialGuide,couple,restore} from '../vixen.mjs';
const near=(a,b)=>assert.ok(Math.abs(a-b)<=1e-10*Math.max(1,Math.abs(b)),`${a} != ${b}`);
const weights=x=>x<=120||x>=180?[0,0,0]:x<130?[.3,.1125,0]:x<=170?[.6,0,0]:[.3,-.1125,0];
test('additive guide jets use the product rule through rise, hold and release',()=>{
 const p=create({seed:'guide-jet'}),g=polynomialGuide([.2,.01],{center:150});
 const c=p.influence(g,{at:120,handoffWidth:10,amount:.6,until:170,releaseWidth:10,mode:'add'});
 for(const x of [120,125,150,175,180]){
  const q=p.jet(x),b=q.sample.base,b1=q.fields.base.d1,b2=q.fields.base.d2,[w,w1,w2]=weights(x),v=.2+.01*(x-150);
  const r=c.jet(x);assert.deepEqual(r.sample,c.sample(x));
  near(r.slope,q.slope+b1*w*v+b*(w1*v+w*.01));
  near(r.curvature,q.curvature+b2*w*v+2*b1*(w1*v+w*.01)+b*(w2*v+2*w1*.01));
 }
});
test('a fully weighted value guide has its own derivatives',()=>{
 const c=create().influence(polynomialGuide([2,.3,.02],{center:150}),{at:120,handoffWidth:10,amount:1});
 const q=c.jet(150);assert.equal(q.slope,.3);assert.equal(q.curvature,.04);
});
test('causal operator jets preserve every primal sample and compose on guides',()=>{
 const p=create().influence(polynomialGuide([.1]),{at:110,handoffWidth:5,amount:.2,reference:'baseline'});
 const c=p.impulse({at:120,magnitude:.2,attack:5,relaxation:10}).redistribute([140,160],[1]);
 for(const x of [119,122.5,130,139,150,160]){
  const q=c.jet(x);assert.deepEqual(q.sample,c.sample(x));assert.ok(Number.isFinite(q.slope));assert.ok(Number.isFinite(q.curvature));
 }
});

test('coupled jets differentiate through equality, not a pointwise identity shortcut',()=>{
 const a=create().influence(polynomialGuide([0,1],{center:150}),{at:120,handoffWidth:10,amount:1,reference:'baseline'});
 const b=create().influence(polynomialGuide([0,-1],{center:150}),{at:120,handoffWidth:10,amount:1,reference:'baseline'});
 const c=couple([a,b],1,[140,160]).curves[0],q=c.jet(150),base=create().jet(150);
 assert.deepEqual(q.sample,c.sample(150));near(q.slope,base.fields.base.d1);near(q.curvature,base.fields.base.d2);
});
test('a known legacy constrained seam is reported unavailable, never as a smooth derivative',()=>{
 const p=create({seed:1,floor:'baseline',boundarySoftness:1}).fork({at:120,operation:'overlap',handoffWidth:80});
 const c=p.influence(polynomialGuide([.1]),{at:150,handoffWidth:10,amount:.5,reference:'baseline'}),q=c.jet(150);
 assert.equal(q.slope,null);assert.equal(q.curvature,null);assert.match(q.differentiability.rationale,/discontinuous/);
 assert.ok(Number.isFinite(q.fields.unconstrainedValue.d1));
});

test('composed slopes remain available when only curvature overflows',()=>{
 const e=Number.EPSILON,c=create({floor:'none'}).impulse({at:1,magnitude:1e280,attack:8*e,relaxation:1}),q=c.jet(1+2*e);
 assert.equal(q.availability.slope,true);assert.equal(q.availability.curvature,false);assert.ok(Number.isFinite(q.slope));assert.equal(q.curvature,null);
});
test('explicit-coordinate composed jets preserve the actual fixed-path sample',()=>{
 const c=create({velocity:[.07,...Array(15).fill(0)]}).influence(polynomialGuide([.1,.003],{center:150}),{at:120,handoffWidth:10,reference:'baseline'}),h=Array(16).fill(.3);
 assert.deepEqual(c.jet(150,h).sample,c.at(150,h));assert.equal(c.jet(150,h).availability.curvature,true);
});
test('nonzero deep analytic graphs and their replay remain stack safe',()=>{
 let c=create();const g=polynomialGuide([.1]);for(let i=0;i<1200;i++)c=c.influence(g,{at:120,handoffWidth:10,amount:.03,reference:'baseline'});
 const q=c.jet(150);assert.deepEqual(q.sample,c.sample(150));assert.ok(Number.isFinite(q.slope));assert.ok(Number.isFinite(q.curvature));assert.deepEqual(restore(JSON.parse(JSON.stringify(c.snapshot()))).jet(150),q);
});
