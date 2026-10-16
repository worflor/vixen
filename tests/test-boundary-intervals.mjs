import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const text=readFileSync(new URL('../vixen-interval.mjs',import.meta.url),'utf8').replace('function floorMap(','export function floorMap(').replace('function ceilMap(','export function ceilMap(');
const I=await import('data:text/javascript;base64,'+Buffer.from(text).toString('base64'));
const ramp=t=>t<=0?0:t>=1?t:t*t*t*(6+t*(-8+3*t));
const floor=(y,l,e)=>y<=l?l:e===0||y>=l+e?y:l+e*ramp((y-l)/e);
test('boundary interval maps include interior extrema in a moving bound',()=>{
 const f=I.floorMap(I.interval(.5),I.interval(-.2,.2),I.interval(1)),c=I.ceilMap(I.interval(-.5),I.interval(-.2,.2),I.interval(1));
 for(let i=0;i<=400;i++){
  const l=-.2+i/1000,v=floor(.5,l,1),u=-floor(.5,-l,1);
  assert.ok(f.lo<=v&&v<=f.hi,`${JSON.stringify(f)} excludes ${v}`);
  assert.ok(c.lo<=u&&u<=c.hi,`${JSON.stringify(c)} excludes ${u}`);
 }
});
test('boundary interval maps cover hard-to-soft width intervals',()=>{
 const f=I.floorMap(I.interval(.2,.7),I.interval(-.2,.2),I.interval(0,1));
 for(const y of [.2,.5,.7])for(const l of [-.2,.17,.2])for(const e of [0,.001,.5,1]){
  const v=floor(y,l,e);assert.ok(f.lo<=v&&v<=f.hi);
 }
});
