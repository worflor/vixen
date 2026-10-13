import fs from 'node:fs';import {create as hyper} from '../vixen.mjs';import {create as legacy} from '../legacy/vixen.mjs';
const seeds=['vixen',...Array.from({length:24},(_,i)=>'comparison-'+i)],starts=[180,2300,6000],n=2048,step=.5,models={};
for(const [name,factory] of [['legacy-default',s=>legacy({seed:s,variation:.35})],['legacy-full-seed',s=>legacy({seed:s,variation:1})],['hyperfield',s=>hyper({seed:s})]]){
 models[name]=starts.map(start=>seeds.map(seed=>{const f=factory(seed);return Array.from({length:n},(_,i)=>f.sample(start+i*step).deviation);}));
}
const sensitivity=[];for(const seed of ['vixen','bench',42]){const f=hyper({seed});for(const h of [Array(16).fill(0),Array.from({length:16},(_,i)=>Math.sin(i)*2)]){const xs=Array.from({length:200},(_,i)=>180+i*3.73),base=xs.map(x=>f.at(x,h).deviation),rows=[];for(let j=0;j<16;j++){const a=h.slice(),b=h.slice();a[j]+=.0001;b[j]-=.0001;rows.push(xs.map(x=>(f.at(x,a).deviation-f.at(x,b).deviation)/.0002));}sensitivity.push({seed,latent:h,rows});}}
fs.writeFileSync('diagnostic-data.json',JSON.stringify({seeds,starts,n,step,models,sensitivity,hyperDefaults:hyper().config}));console.log('Wrote spectral and sensitivity data for '+seeds.length+' seeds, '+starts.length+' windows');
