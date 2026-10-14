import assert from 'node:assert/strict';
import fs from 'node:fs';
import {create,restore,hash32} from '../vixen.mjs';
let rng=90210,rngDraws=0,roots=0,forks=0;
const random=()=>((rng=hash32(rng,rngDraws++,719))/4294967296);
const near=(a,b)=>assert.ok(Math.abs(a-b)<=1e-9*Math.max(1,Math.abs(a),Math.abs(b)),`${a} != ${b}`);
const neighbor=(x,up)=>{const d=new DataView(new ArrayBuffer(8));d.setFloat64(0,x);d.setBigUint64(0,d.getBigUint64(0)+(up?1n:-1n));return d.getFloat64(0);};
let samples=0;
for(let i=0;i<250;i++){
 const endpoint=[2,50,100,200,50000,2+49998*random()][i%6];
 const root=create({seed:i%2?'seed-'+i:i,openingEndpoint:endpoint,introVariation:random(),stabilizer:random(),strength:2*random(),contrast:16*random(),tempo:.25+3.75*random(),ramp:1+63*random(),floor:['none','zero','baseline','backbone'][i%4],ceilingMargin:i%3?16*random():null,boundarySoftness:i%5?random():0,latent:Array.from({length:16},()=>256*random()-128),velocity:Array.from({length:16},()=>8*random()-4)});
 roots++;
 const end=root.domain[1],at=1+(end-1)*.25,width=Math.min(20,(end-at)/4);
 const first=root.fork({at,operation:'first-'+i,handoffWidth:width});
 const child=first.fork({at:at+width/2,operation:'second-'+i,handoffWidth:width});forks+=2;
 const points=[1,end,endpoint,neighbor(endpoint,false),neighbor(endpoint,true),at,at+width/4,at+width/2,at+width,at+width*1.5,...Array.from({length:64},()=>1+(end-1)*random())].filter(x=>x>=1&&x<=end);
 const replay=restore(JSON.parse(JSON.stringify(child.snapshot())));
 for(const x of points){
  const p=child.sample(x);samples++;
  for(const value of Object.values(p))if(typeof value==='number')assert.ok(Number.isFinite(value),JSON.stringify({i,x,p}));
  assert.ok(p.base>0&&p.activity>=0&&p.activity<=1&&p.positiveMultiplier>0);
  if(p.floorValue!==null)assert.ok(p.value>=p.floorValue);
  if(p.ceilingValue!==null)assert.ok(p.value<=p.ceilingValue);
  near(p.value,p.base*p.multiplier);near(p.activity,.5+.5*Math.tanh(p.deviation));
  near(p.value,child.at(x,child.location(x)).value);
  assert.deepEqual(p,replay.sample(x));
  if(x<=at+width/2)assert.deepEqual(p,first.sample(x));
  if(x<=at)assert.deepEqual(p,root.sample(x));
 }
}
const report={seed:90210,roots,forks,samples,rngDraws,status:'passed'};
console.log(JSON.stringify(report,null,2));
if(process.env.VIXEN_FUZZ_REPORT)fs.writeFileSync(process.env.VIXEN_FUZZ_REPORT,JSON.stringify(report,null,2));
