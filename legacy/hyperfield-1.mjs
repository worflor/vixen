/** Vixen: continuous seeded 16-D landscape. Dependency-free, random-access.
 * Save algorithm + full config. Float curves are not cross-libm bitwise lockstep.
 */
export const ALGORITHM='vixen-hyperfield-c2/1';
export const DIMENSIONS=16,FEATURES=48;
export const DEFAULTS=Object.freeze({seed:'vixen',strength:1,ramp:16,tempo:.8,contrast:10,latent:Object.freeze(Array(16).fill(0)),velocity:Object.freeze(Array(16).fill(0))});
export function smooth5(t){if(t<=0)return 0;if(t>=1)return 1;return t*t*t*(10+t*(-15+6*t));}
export function bump(t){if(Math.abs(t)>=1)return 0;const v=1-t*t;return v*v*v;}
export function seed32(seed){if(typeof seed==='number'){if(!Number.isInteger(seed)||seed<0||seed>0xffffffff)throw new RangeError('Numeric seed must be uint32');return seed>>>0;}if(typeof seed!=='string')throw new TypeError('Seed must be text or uint32');if(seed.length>1024)throw new RangeError('Text seed limit: 1024 UTF-16 code units');let h=2166136261;for(const b of new TextEncoder().encode(seed))h=Math.imul(h^b,16777619)>>>0;return h;}
export function hash32(seed,index,channel){let h=(seed^Math.imul(index>>>0,0x9e3779b9)^Math.imul(channel>>>0,0x85ebca6b))>>>0;h=Math.imul(h^(h>>>16),0x7feb352d)>>>0;h=Math.imul(h^(h>>>15),0x846ca68b)>>>0;return (h^(h>>>16))>>>0;}
const unit=(s,k,c)=>hash32(s,k,c)/4294967296;
const dot=(a,b)=>{let n=0;for(let j=0;j<16;j++)n+=a[j]*b[j];return n;};
function normal(a){const n=Math.sqrt(dot(a,a));if(n===0)return Array.from({length:16},(_,i)=>i===0?1:0);return a.map(v=>v/n);}
function vector(a,limit=1000000){if(!Array.isArray(a)&&!ArrayBuffer.isView(a))throw new TypeError('A vector must contain 16 numbers');if(a.length!==16)throw new RangeError('A vector must contain exactly 16 coordinates');const v=Array.from(a);if(v.some(n=>typeof n!=='number'||!Number.isFinite(n)||Math.abs(n)>limit))throw new RangeError(`Coordinates must be finite in [-${limit},${limit}]`);return v;}
function coordinate(x){if(typeof x!=='number'||!Number.isFinite(x)||x<1||x>50000)throw new RangeError('x must be finite in [1,50000]');}
export function baseline(x){return x*Math.exp(x/100)-25*Math.exp(-((x-42)**2)/32)-38*Math.exp(-((x-71)**2)/28);}
export function navigationBasis(index=0){if(!Number.isInteger(index)||index<0||index>100000)throw new RangeError('Plane index must be an integer in [0,100000]');let u,v;if(index===0){u=Array.from({length:16},(_,j)=>j===0?1:0);v=Array.from({length:16},(_,j)=>j===1?1:0);}else{const seed=seed32('vixen-navigation');u=normal(Array.from({length:16},(_,j)=>2*unit(seed,index,100+j)-1));const raw=Array.from({length:16},(_,j)=>2*unit(seed,index,200+j)-1),proj=dot(raw,u);v=raw.map((n,j)=>n-proj*u[j]);if(dot(v,v)<1e-20){const k=u.reduce((best,n,j)=>Math.abs(n)<Math.abs(u[best])?j:best,0);v=Array.from({length:16},(_,j)=>(j===k?1:0)-u[k]*u[j]);}v=normal(v);}return Object.freeze({u:Object.freeze(u),v:Object.freeze(v)});}
export function create(options={}){
 if(!options||typeof options!=='object'||Array.isArray(options))throw new TypeError('Config must be an object');for(const key of Object.keys(options))if(!Object.hasOwn(DEFAULTS,key))throw new TypeError('Unknown config key: '+key);
 const c={...DEFAULTS,...options};const seed=seed32(c.seed);for(const [key,lo,hi] of [['strength',0,2],['ramp',1,64],['tempo',.25,4],['contrast',0,16]])if(typeof c[key]!=='number'||!Number.isFinite(c[key])||c[key]<lo||c[key]>hi)throw new RangeError(`${key} must lie in [${lo},${hi}]`);
 c.latent=Object.freeze(vector(c.latent,128));c.velocity=Object.freeze(vector(c.velocity,4));const config=Object.freeze(c);
 const ridges=[];for(let i=0;i<FEATURES;i++){const direction=Object.freeze(normal(Array.from({length:16},(_,j)=>2*unit(seed,i,20+j)-1))),scale=(4+76*unit(seed,i,1))*c.tempo,basePhase=unit(seed,i,2)*1024,phase=basePhase+dot(direction,c.latent),slope=1/scale+dot(direction,c.velocity)/100;ridges.push(Object.freeze({index:i,direction,scale,basePhase,phase,slope,channel:i+200}));}
 function noise(p,channel){const k=Math.floor(p),t=p-k,a=2*unit(seed,k,channel)-1,b=2*unit(seed,k+1,channel)-1;return a+(b-a)*smooth5(t);}
 function result(x,score){const gate=smooth5((x-100)/c.ramp),deviation=c.strength*gate*2.2*Math.tanh(c.contrast*score),base=baseline(x),positiveMultiplier=Math.exp(.55*Math.tanh(deviation));return Object.freeze({x,value:base+base*deviation,base,score,deviation,multiplier:1+deviation,activity:.5+.5*Math.tanh(deviation),positiveMultiplier,positiveValue:base*positiveMultiplier,gate});}
 function sample(x){coordinate(x);let score=0;if(x>100){const u=x-100;for(const r of ridges)score+=noise(r.phase+u*r.slope,r.channel)/FEATURES;}return result(x,score);}
 function at(x,h){coordinate(x);const latent=vector(h);let score=0;if(x>100){const u=x-100;for(const r of ridges)score+=noise(r.basePhase+dot(r.direction,latent)+u/r.scale,r.channel)/FEATURES;}return result(x,score);}
 function location(x){coordinate(x);const t=Math.max(0,x-100)/100;return Object.freeze(c.latent.map((h,i)=>h+t*c.velocity[i]));}
 function inspect(x){coordinate(x);if(x<=100)return Object.freeze([]);const u=x-100;return Object.freeze(ridges.map(r=>{const p=r.phase+u*r.slope;return Object.freeze({index:r.index,scale:r.scale,coordinate:p,cell:Math.floor(p),contribution:noise(p,r.channel)/FEATURES});}).sort((a,b)=>Math.abs(b.contribution)-Math.abs(a.contribution)));}
 const bounds=Object.freeze({score:Object.freeze([-1,1]),deviation:Object.freeze([-2.2*c.strength,2.2*c.strength]),multiplier:Object.freeze([1-2.2*c.strength,1+2.2*c.strength]),activity:Object.freeze([0,1]),positiveMultiplier:Object.freeze([Math.exp(-.55),Math.exp(.55)])});
 return Object.freeze({algorithm:ALGORITHM,config,bounds,sample,at,location,inspect,ridges:()=>Object.freeze(ridges.slice())});
}
