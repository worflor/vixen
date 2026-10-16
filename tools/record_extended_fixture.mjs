import fs from 'node:fs';
import {createExtended,MAX_DOMAIN_END} from '../vixen.mjs';
const cases=[];
for(const [index,options] of [{seed:1},{seed:'far-😀',floor:'backbone',ceilingMargin:.7,stabilizer:.3},{seed:42,openingEndpoint:50000,floor:'baseline',strength:2},{seed:'short',openingEndpoint:2,floor:'zero'}].entries()){
 const curve=createExtended(options).fork({at:1e9,operation:'far',handoffWidth:500}).fork({at:1e9+50,operation:'overlap',handoffWidth:800});
 const h=Array.from({length:16},(_,i)=>(i%3-1)*.25),samples=[];
 for(const x of [1,150,1e7,1e9+100,MAX_DOMAIN_END-1,MAX_DOMAIN_END]){
  samples.push({x,expected:curve.sampleNormalized(x)});samples.push({x,h,expected:curve.atNormalized(x,h)});
 }
 cases.push({snapshot:curve.snapshot(),samples,checkpoints:[1e9+100,1e9+1000].map(from=>({snapshot:curve.tail(from),x:1e9+2000,expected:curve.sampleNormalized(1e9+2000)}))});
}
fs.mkdirSync(new URL('../tests/fixtures/v6/',import.meta.url),{recursive:true});
fs.writeFileSync(new URL('../tests/fixtures/v6/native.json',import.meta.url),JSON.stringify({format:'vixen-extended-native-fixture/1',reference:'JS vixen-hyperfield-c2/6; frozen cross-port conformance, not an independent mathematical oracle',cases},null,2)+'\n');
console.log('Recorded '+cases.length+' native extended recipes and '+cases.reduce((n,c)=>n+c.samples.length+c.checkpoints.length,0)+' samples');
