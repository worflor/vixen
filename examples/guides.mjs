import {create,polynomialGuide,taylorGuide,captureGuide,restore} from '../vixen.mjs';
const root=create({seed:'guided-world'});
const line=root.influence(polynomialGuide([3,2]),{at:160,amount:1});
const series=root.influence(taylorGuide([1,1,1,1],{center:160,domain:[1,500]}),{at:160,amount:.7});
const guide=captureGuide(x=>80*Math.sin(x/23)+.3*x,{domain:[100,500],count:129});
const future=root.influence(guide,{at:120,amount:.7}).fork({at:180,operation:'take-key'});
const replay=restore(JSON.parse(JSON.stringify(future.snapshot())));
console.log(JSON.stringify({line:line.sample(200).value,series:series.sample(200).value,guided:future.sample(200).value,replay:replay.sample(200).value},null,2));
