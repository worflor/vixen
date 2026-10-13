import test from 'node:test';
import assert from 'node:assert/strict';
import * as V from '../vixen.mjs';
import * as V3 from '../legacy/v3-archive/vixen.mjs';
const near=(a,b,t=1e-11)=>assert.ok(Math.abs(a-b)<=t*Math.max(1,Math.abs(a),Math.abs(b)),`${a} != ${b}`);
test('opening endpoint stretches the authored progression clock; defaults replay v3 exactly',()=>{
 const old=V3.create({seed:'clock'}),current=V.create({seed:'clock',openingEndpoint:100,introVariation:0});
 for(const x of [1,42,71,100,101,144,500,50000])assert.deepEqual(current.sample(x),old.sample(x));
 for(const endpoint of [50,200,1000]){const f=V.create({seed:'clock',openingEndpoint:endpoint});for(const t of [1,42,71,100]){const x=1+(t-1)*(endpoint-1)/99;near(f.sample(x).value,V.baseline(t));}assert.equal(f.sample(endpoint).gate,0);assert.equal(f.sample(endpoint+f.config.ramp).gate,1);}
});
test('translated progression has a finite declared domain and coordinate agreement',()=>{
 const f=V.create({openingEndpoint:50,velocity:Array(16).fill(.1)});
 assert.equal(f.domain[0],1);assert.ok(f.domain[1]<50000);assert.ok(Number.isFinite(f.sample(f.domain[1]).value));assert.throws(()=>f.sample(f.domain[1]+1));
 for(const x of [1,50,100,200])near(f.sample(x).value,f.at(x,f.location(x)).value);
 for(const x of [0,1,NaN,Infinity,'100',50001])assert.throws(()=>V.create({openingEndpoint:x}));
});
test('seeded dips are opt-in, positive and return C2 to the authored continuation',()=>{
 const a=V.create({seed:'one',introVariation:1}),b=V.create({seed:'two',introVariation:1});
 assert.notEqual(a.sample(42).value,b.sample(42).value);
 const old=V3.create({seed:'one'});
 for(let x=1;x<=100;x+=.25)assert.ok(a.sample(x).value>0);
 for(const x of [100,101,144,500])assert.deepEqual(a.sample(x),old.sample(x));
 for(const v of [-.1,1.1,NaN,'0.5',true])assert.throws(()=>V.create({introVariation:v}));
 const half=V.create({seed:'one',introVariation:.5}),fixed=V.create({seed:'one'});for(let i=0;i<2;i++){near(half.dips[i].center,(fixed.dips[i].center+a.dips[i].center)/2);near(half.dips[i].depth,(fixed.dips[i].depth+a.dips[i].depth)/2);near(half.dips[i].width**2,(fixed.dips[i].width**2+a.dips[i].width**2)/2);}
 for(let seed=0;seed<20;seed++)for(const introVariation of [.25,.5,1]){const c=V.create({seed,introVariation}),ys=Array.from({length:496},(_,i)=>c.sample(1+i/5).value);assert.equal(ys.slice(1,-1).filter((y,i)=>y<ys[i]&&y<ys[i+2]).length,2);}
 const h=.001;const slope=f=>(f.sample(100+h).value-f.sample(100-h).value)/(2*h);near(slope(a),slope(old),1e-7);
});
