/** Vixen: seeded, random-access controlled disorder. No state, no dependencies.
 * Numeric domain 1 <= x <= 50000; seed hash exact uint32; curves binary64.
 * Save ALGORITHM plus config for replay. Not cryptographic and not bitwise lockstep across libm engines.
 */
export const ALGORITHM='vixen-counter-c2/1';
export const DEFAULTS=Object.freeze({seed:'vixen',variation:.35,strength:1,ramp:16,spacing:12});
export function smooth5(t){if(t<=0)return 0;if(t>=1)return 1;return t*t*t*(10+t*(-15+6*t));}
export function bump(t){if(Math.abs(t)>=1)return 0;const v=1-t*t;return v*v*v;}
export function seed32(seed){
 if(typeof seed==='number'){if(!Number.isInteger(seed)||seed<0||seed>0xffffffff)throw new RangeError('Numeric seed must be uint32');return seed>>>0;}
 if(typeof seed!=='string')throw new TypeError('Seed must be text or uint32');
 if(seed.length>1024)throw new RangeError('Text seed limit is 1024 UTF-16 code units');
 let h=2166136261;for(const b of new TextEncoder().encode(seed)){h=Math.imul(h^b,16777619)>>>0;}return h;
}
export function hash32(seed,index,channel){
 let h=(seed^Math.imul(index>>>0,0x9e3779b9)^Math.imul(channel>>>0,0x85ebca6b))>>>0;
 h=Math.imul(h^(h>>>16),0x7feb352d)>>>0;
 h=Math.imul(h^(h>>>15),0x846ca68b)>>>0;
 return (h^(h>>>16))>>>0;
}
const unit=(s,k,c)=>hash32(s,k,c)/4294967296;
const mix=(a,b,v)=>a+(b-a)*v;
export function baseline(x){return x*Math.exp(x/100)-25*Math.exp(-((x-42)**2)/32)-38*Math.exp(-((x-71)**2)/28);}
function coordinate(x){if(typeof x!=='number'||!Number.isFinite(x)||x<1||x>50000)throw new RangeError('x must be finite in [1,50000]');}
export function create(options={}){
 if(!options||typeof options!=='object'||Array.isArray(options))throw new TypeError('Config must be an object');
 for(const k of Object.keys(options))if(!Object.hasOwn(DEFAULTS,k))throw new TypeError('Unknown config key: '+k);
 const config=Object.freeze({...DEFAULTS,...options});const seed=seed32(config.seed);
 for(const [key,lo,hi] of [['variation',0,1],['strength',0,2],['ramp',1,64],['spacing',6,24]]){
  const v=config[key];if(typeof v!=='number'||!Number.isFinite(v)||v<lo||v>hi)throw new RangeError(`${key} must lie in [${lo},${hi}]`);
 }
 const rnd=(k,c)=>mix(unit(0,k,c),unit(seed,k,c),config.variation);
 function noise(u,period,channel){const p=u/period,k=Math.floor(p),t=p-k,a=2*rnd(k,channel)-1,b=2*rnd(k+1,channel)-1;return mix(a,b,smooth5(t));}
 function amplitude(s,k){return unit(s,k,6)<.43?-(1.55+.8*unit(s,k,7)):.7+1.1*unit(s,k,7);}
 function event(k){
  if(!Number.isInteger(k)||k<0||k>100000)throw new RangeError('Event index must be an integer in [0,100000]');
  return Object.freeze({index:k,center:config.spacing*(k+.5+.2*(2*rnd(k,4)-1)),width:config.spacing*(.14+.11*rnd(k,5)),amplitude:mix(amplitude(0,k),amplitude(seed,k),config.variation)});
 }
 const bounds=Object.freeze({deviation:Object.freeze([-2.61*config.strength,2.06*config.strength]),multiplier:Object.freeze([1-2.61*config.strength,1+2.06*config.strength]),activity:Object.freeze([0,1]),positiveMultiplier:Object.freeze([Math.exp(-.55),Math.exp(.55)])});
 function sample(x){
  coordinate(x);const u=x-100,g=smooth5(u/config.ramp);let texture=0,pulse=0,active=null;
  if(u>0&&config.strength>0){texture=.16*noise(u,18,1)+.10*noise(u,43,2);const k=Math.floor(u/config.spacing);
   // Only current cell can contain a pulse: support is strictly inside its own cell.
   const e=event(k),z=(u-e.center)/e.width;pulse=e.amplitude*bump(z);if(Math.abs(z)<1)active=e.index;
  }
  const deviation=config.strength*g*(texture+pulse),base=baseline(x),multiplier=1+deviation;
  // Smooth positive adapter; neutral at zero, finite for every permitted deviation.
  const positiveMultiplier=Math.exp(.55*Math.tanh(deviation));
  const value=base+base*deviation;
  return Object.freeze({x,value,base,deviation,multiplier,activity:.5+.5*Math.tanh(deviation),positiveMultiplier,positiveValue:base*positiveMultiplier,gate:g,texture,pulse,event:active});
 }
 // Samples are values, not consumable RNG draws. Calling event/sample in any order changes nothing.
 return Object.freeze({algorithm:ALGORITHM,config,bounds,event,sample});
}
