import test from 'node:test';
import assert from 'node:assert/strict';
import {create,restore,ALGORITHM} from '../vixen.mjs';
const near=(a,b,t=1e-9)=>assert.ok(Math.abs(a-b)<=t*Math.max(1,Math.abs(a),Math.abs(b)),`${a} != ${b}`);
test('overlapping handoffs, inspection, depth limits and malformed lineages stay bounded',()=>{
 const root=create({seed:'overlap',openingEndpoint:75,introVariation:1,velocity:Array(16).fill(.05),floor:'backbone',ceilingMargin:.2});
 const parent=root.fork({at:120,operation:'a',handoffWidth:40}),child=parent.fork({at:130,operation:'b',handoffWidth:40});
 for(const x of [120,125,130])assert.deepEqual(child.sample(x),parent.sample(x));
 for(const x of [131,140,159,160,165,170]){const p=child.sample(x);near(p.value,child.at(x,child.location(x)).value);near(child.inspect(x).reduce((sum,r)=>sum+r.contribution,0),p.score);assert.ok(p.value>=p.floorValue&&p.value<=p.ceilingValue);near(p.value,p.base*p.multiplier);}
 const sources=new Set(child.inspect(140).map(row=>row.sourceSeed));assert.ok(sources.has(root.config.seed)&&sources.has(parent.config.seed)&&sources.has(child.config.seed));
 let deep=root;for(let i=0;i<32;i++)deep=deep.fork({at:200+i,operation:'depth-'+i});assert.throws(()=>deep.fork({at:233,operation:'too-deep'}));
 const replay=restore(JSON.parse(JSON.stringify(deep.snapshot())));for(const x of [1,200,211,220,232,240,300])assert.deepEqual(deep.sample(x),replay.sample(x));
 for(const lineage of [Array(33).fill({at:200,operation:'a'}),[null],[{at:200,operation:'a',unknown:1}],['a'],[{}]])assert.throws(()=>restore({algorithm:ALGORITHM,config:root.snapshot().config,lineage}));
 const cfg=Object.fromEntries(Object.entries(root.snapshot().config).filter(([k])=>!['openingEndpoint','introVariation'].includes(k)));assert.doesNotThrow(()=>restore({algorithm:'vixen-hyperfield-c2/3',config:cfg}));assert.throws(()=>restore({algorithm:'vixen-hyperfield-c2/3',config:root.config}));
});
