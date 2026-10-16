import test from 'node:test';
import assert from 'node:assert/strict';
import * as V from '../vixen.mjs';
const ramp=t=>t<=0?0:t>=1?t:t*t*t*(6+t*(-8+3*t));
function policy(y,p){
 const e=p.boundaryWidth,lo=p.floorValue,hi=p.ceilingValue;
 if(lo!==null&&hi!==null&&hi<=lo)return lo;
 if(lo!==null)y=y<=lo?lo:e===0||y>=lo+e?y:lo+e*ramp((y-lo)/e);
 if(hi!==null)y=y>=hi?hi:e===0||y<=hi-e?y:hi-e*ramp((hi-y)/e);
 return y;
}
const build=()=> (V.createCoherent??V.create)({seed:1,floor:'baseline',boundarySoftness:.1}).fork({at:120,handoffWidth:80,operation:'branch'});
test('coherent native forks close the constraint state before descendants use it',()=>{
 const c=build();
 for(const x of [125,140,150,160,175,190]){
  const p=c.sample(x);assert.equal(p.value,policy(p.unconstrainedValue,p));assert.equal(p.guideValue,policy(p.unconstrainedGuideValue,p));
 }
 assert.equal(c.algorithm,V.COHERENT_ALGORITHM);assert.notEqual(c.algorithm,'vixen-hyperfield-c2/4');
});
test('finite influence activation and release do not reproject an inconsistent fork parent',()=>{
 const parent=build(),c=parent.influence(V.polynomialGuide([.3]),{at:150,amount:1,handoffWidth:10,reference:'baseline',until:180,releaseWidth:10});
 for(const edge of [150,190]){
  const p=c.sample(edge),left=c.sample(edge-1e-7),right=c.sample(edge+1e-7);
  assert.ok(Math.abs(right.value-left.value)/Math.max(1,Math.abs(p.value))<1e-6);
  assert.deepEqual(p,parent.sample(edge));
 }
 const saved=c.snapshot(),r=V.restore(saved);for(const x of [120,149,150,155,185,190,220])assert.deepEqual(r.sample(x),c.sample(x));
});

test('coherent jets, active checkpoints and forks preserve their mathematical version',()=>{
 const c=build(),q=c.jet(150),h=1e-4,finite=(c.sample(150+h).value-c.sample(150-h).value)/(2*h);
 assert.deepEqual(q.sample,c.sample(150));assert.ok(Math.abs(q.slope-finite)<1e-5*Math.max(1,Math.abs(finite)));
 const saved=c.tail(150);assert.equal(saved.algorithm,V.COHERENT_TAIL_ALGORITHM);const r=V.restore(saved);
 for(const x of [150,160,180,200,220]){assert.deepEqual(r.sample(x),c.sample(x));assert.deepEqual(r.jet(x),c.jet(x));}
 const next=r.fork({at:160,operation:'tail/next',handoffWidth:10});assert.equal(next.snapshot().algorithm,V.COHERENT_TAIL_ALGORITHM);
 const enclosure=c.encloseDeviation(149,151);assert.ok(enclosure[0]<=c.sample(150).deviation&&c.sample(150).deviation<=enclosure[1]);
});

test('coherent enclosures describe the actual constrained timeline, not applied-value ancestors',()=>{
 for(const floor of ['none','zero','baseline','backbone']){
  const c=V.createCoherent({seed:'coherent/interval',floor,ceilingMargin:floor==='none'?null:.4}).fork({at:120,operation:'overlap/a',handoffWidth:80}).fork({at:140,operation:'overlap/b',handoffWidth:40});
  for(const [a,b]of[[99,101],[119,121],[149.9,150.1],[180,200],[220,220]]){
   const q=c.encloseDeviation(a,b);for(let i=0;i<=12;i++){const x=a+(b-a)*i/12,d=c.sample(x).deviation;assert.ok(q[0]<=d&&d<=q[1],`${floor} ${x}: ${d} not in [${q[0]},${q[1]}]`);}
  }
 }
});
