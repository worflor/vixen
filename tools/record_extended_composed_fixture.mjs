import fs from 'node:fs';
import {createExtended,extendedPolynomialGuide,withExtendedInfluence,withExtendedImpulse,withExtendedRedistribution,coupleExtended} from '../vixen.mjs';
const a=createExtended({seed:'wide-a'}),b=createExtended({seed:'wide-b'}),c=extendedPolynomialGuide([.1,-.02,.004],{center:1e9,scale:100});
const influence=withExtendedInfluence(a,c,{at:1e9,amount:.4,handoffWidth:50,reference:'baseline',until:1e9+600,releaseWidth:100});
const additive=withExtendedInfluence(a,c,{at:1e9,amount:.3,handoffWidth:50,reference:'backbone',mode:'add'});
const impulse=withExtendedImpulse(influence,{at:1e9+5,magnitude:.4,attack:20,relaxation:200});
const redistribution=withExtendedRedistribution(additive,{from:1e9+10,until:1e9+800,coefficients:[1,-2,.25],magnitude:1});
const group=coupleExtended([impulse,redistribution,b],.7,[1e9+20,1e9+500]);
let deep=a;for(let i=0;i<48;i++)deep=withExtendedInfluence(deep,c,{at:1e9,amount:.002,handoffWidth:50,reference:'baseline'});
const unaryFork=withExtendedImpulse(a,{at:120,magnitude:.2,attack:5,relaxation:60}).fork({at:150,operation:'fork-under-unary',handoffWidth:30});
const curves=[influence,additive,impulse,redistribution,...group.curves,deep,unaryFork],cases=[];
for(const curve of curves){const samples=[];for(const x of [130,1e9,1e9+31,1e9+260,1e9+650,1e9+900,2**40])samples.push({x,expected:curve.sampleNormalized(x)});cases.push({snapshot:curve.snapshot(),samples,checkpoints:[1e9+30,1e9+900].map(from=>({snapshot:curve.tail(from),x:1e9+1100,expected:curve.sampleNormalized(1e9+1100)}))});}
fs.writeFileSync(new URL('../tests/fixtures/v6/composed.json',import.meta.url),JSON.stringify({format:'vixen-extended-composed-fixture/1',reference:'Frozen JS cross-port conformance, not an independent mathematical oracle',cases},null,2)+'\n');
console.log('Recorded '+cases.length+' composed extended recipes and '+cases.reduce((n,c)=>n+c.samples.length+c.checkpoints.length,0)+' observations');
