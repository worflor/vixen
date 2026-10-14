import {performance} from 'node:perf_hooks';
import {pathToFileURL} from 'node:url';
const moduleURL=process.env.VIXEN_MODULE?pathToFileURL(process.env.VIXEN_MODULE).href:new URL('../vixen.mjs',import.meta.url).href;
const {create}=await import(moduleURL);
const n=Number(process.env.VIXEN_BENCH_SAMPLES||20000),repeats=7;
const root=create({seed:'benchmark',introVariation:1,stabilizer:.3});
const fork=root.fork({at:200,operation:'benchmark',handoffWidth:100});
const h=Array(16).fill(.1);
const cases={'leaf.sample':x=>root.sample(x),'leaf.at':x=>root.at(x,h),'branch.sample':x=>fork.sample(x),'branch.at':x=>fork.at(x,h)};
const results={};let checksum=0;
for(const [name,fn] of Object.entries(cases)){
 for(let i=0;i<1000;i++)checksum+=fn(201+(i%997)/10).activity;
 const times=[];
 for(let r=0;r<repeats;r++){const start=performance.now();for(let i=0;i<n;i++)checksum+=fn(201+(i%997)/10).activity;times.push(performance.now()-start);}
 times.sort((a,b)=>a-b);results[name]={samples:n,repeats,median_ms:times[Math.floor(repeats/2)],min_ms:times[0],max_ms:times.at(-1)};
}
console.log(JSON.stringify({runtime:process.version,results,checksum},null,2));
