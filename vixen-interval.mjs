const MIN_VALUE=Number.MIN_VALUE,MAX_VALUE=Number.MAX_VALUE;
const bitsView=new DataView(new ArrayBuffer(8));
const MAX_BITS=0x7fefffffffffffffn;
function nextUp(x){if(x===Infinity)return x;if(x===-Infinity)return -MAX_VALUE;if(x===0)return MIN_VALUE;bitsView.setFloat64(0,x,false);let b=bitsView.getBigUint64(0,false);b+=x>0?1n:-1n;bitsView.setBigUint64(0,b,false);return bitsView.getFloat64(0,false);}
function nextDown(x){if(x===-Infinity)return x;if(x===Infinity)return MAX_VALUE;if(x===0)return -MIN_VALUE;bitsView.setFloat64(0,x,false);let b=bitsView.getBigUint64(0,false);b+=x>0?-1n:1n;bitsView.setBigUint64(0,b,false);return bitsView.getFloat64(0,false);}
function finiteNumber(x){return typeof x==='number'&&Number.isFinite(x);}
export function interval(lo,hi=lo){if(!finiteNumber(lo)||!finiteNumber(hi)||lo>hi)throw new RangeError('A finite ordered interval is required');return Object.freeze({lo,hi});}
function iv(x){if(!x||!finiteNumber(x.lo)||!finiteNumber(x.hi)||x.lo>x.hi)throw new TypeError('Expected an interval');return x;}
function bounds(lo,hi){if(!finiteNumber(lo)||!finiteNumber(hi))throw new RangeError('Unresolved: no finite binary64 enclosure');return interval(lo,hi);}
function down(x){if(!finiteNumber(x))throw new RangeError('Unresolved: arithmetic overflow');const y=nextDown(x);if(!finiteNumber(y))throw new RangeError('Unresolved: no finite lower enclosure');return y;}
function up(x){if(!finiteNumber(x))throw new RangeError('Unresolved: arithmetic overflow');const y=nextUp(x);if(!finiteNumber(y))throw new RangeError('Unresolved: no finite upper enclosure');return y;}
export function add(a,b){iv(a);iv(b);if(a.lo===0&&a.hi===0)return b;if(b.lo===0&&b.hi===0)return a;let lo=down(a.lo+b.lo),hi=up(a.hi+b.hi);if(a.lo>=0&&b.lo>=0)lo=Math.max(0,lo);if(a.hi<=0&&b.hi<=0)hi=Math.min(0,hi);return bounds(lo,hi);}
export function subtract(a,b){iv(a);iv(b);if(b.lo===0&&b.hi===0)return a;let lo=down(a.lo-b.hi),hi=up(a.hi-b.lo);if(a.lo>=b.hi)lo=Math.max(0,lo);if(a.hi<=b.lo)hi=Math.min(0,hi);return bounds(lo,hi);}
export function multiply(a,b){iv(a);iv(b);if((a.lo===0&&a.hi===0)||(b.lo===0&&b.hi===0))return interval(0);if(a.lo===1&&a.hi===1)return b;if(b.lo===1&&b.hi===1)return a;if(a.lo===-1&&a.hi===-1)return interval(-b.hi,-b.lo);if(b.lo===-1&&b.hi===-1)return interval(-a.hi,-a.lo);const p=[a.lo*b.lo,a.lo*b.hi,a.hi*b.lo,a.hi*b.hi];if(p.some(v=>!finiteNumber(v)))throw new RangeError('Unresolved: multiplication overflow');let lo=down(Math.min(...p)),hi=up(Math.max(...p));if((a.lo>=0&&b.lo>=0)||(a.hi<=0&&b.hi<=0))lo=Math.max(0,lo);if((a.lo>=0&&b.hi<=0)||(a.hi<=0&&b.lo>=0))hi=Math.min(0,hi);return bounds(lo,hi);}
export function divide(a,b){iv(a);iv(b);if(b.lo<=0&&b.hi>=0)throw new RangeError('Division interval contains zero');if(a.lo===0&&a.hi===0)return interval(0);if(b.lo===1&&b.hi===1)return a;const p=[a.lo/b.lo,a.lo/b.hi,a.hi/b.lo,a.hi/b.hi];if(p.some(v=>!finiteNumber(v)))throw new RangeError('Unresolved: division overflow');let lo=down(Math.min(...p)),hi=up(Math.max(...p));if((a.lo>=0&&b.lo>0)||(a.hi<=0&&b.hi<0))lo=Math.max(0,lo);if((a.lo>=0&&b.hi<0)||(a.hi<=0&&b.lo>0))hi=Math.min(0,hi);return bounds(lo,hi);}
export function hull(a,b){iv(a);iv(b);return interval(Math.min(a.lo,b.lo),Math.max(a.hi,b.hi));}
function smoothPoint(x){if(x<=0)return interval(0);if(x>=1)return interval(1);const t=interval(x),t2=multiply(t,t),t3=multiply(t2,t),inner=add(interval(10),multiply(t,add(interval(-15),multiply(interval(6),t))));const r=multiply(t3,inner);return interval(Math.max(0,r.lo),r.hi);}
const POLYNOMIAL_ROUND_ERROR=512*Number.EPSILON;
export function smooth5(a){iv(a);if(a.hi<=0)return interval(0);if(a.lo>=1)return interval(1);if(a.lo===a.hi)return smoothPoint(a.lo);return interval(Math.max(0,down(smoothPoint(a.lo).lo-POLYNOMIAL_ROUND_ERROR)),up(smoothPoint(a.hi).hi+POLYNOMIAL_ROUND_ERROR));}
function rampPoint(x){if(x<=0)return interval(0);if(x>=1)return interval(x);const t=interval(x),t2=multiply(t,t),t3=multiply(t2,t),inner=add(interval(6),multiply(t,add(interval(-8),multiply(interval(3),t))));return multiply(t3,inner);}
export function boundaryRamp(a){iv(a);if(a.hi<=0)return interval(0);if(a.lo>=1)return a;if(a.lo===a.hi)return rampPoint(a.lo);return interval(Math.max(0,down(rampPoint(a.lo).lo-POLYNOMIAL_ROUND_ERROR)),up(rampPoint(a.hi).hi+POLYNOMIAL_ROUND_ERROR));}
function rat(n,d=1n){if(d===0n)throw new RangeError('Zero rational denominator');if(d<0n){n=-n;d=-d;}const g=gcd(n<0n?-n:n,d);return {n:n/g,d:d/g};}
function gcd(a,b){while(b){const t=a%b;a=b;b=t;}return a||1n;}
function ratAdd(a,b){return rat(a.n*b.d+b.n*a.d,a.d*b.d);}
function ratMul(a,b){return rat(a.n*b.n,a.d*b.d);}
function ratDivInt(a,k){return rat(a.n,a.d*BigInt(k));}
function floatRat(x){bitsView.setFloat64(0,x,false);const b=bitsView.getBigUint64(0,false),eb=Number((b>>52n)&0x7ffn),f=b&0xfffffffffffffn;let n=eb===0?f:(1n<<52n)+f,e=eb===0?-1074:eb-1023-52;if(e>=0)return rat(n<<BigInt(e));return rat(n,1n<<BigInt(-e));}
function numberAtBits(b){bitsView.setBigUint64(0,b,false);return bitsView.getFloat64(0,false);}
function compareRatToBits(a,bits){const f=numberAtBits(bits),r=floatRat(f);const d=a.n*r.d-r.n*a.d;return d<0n?-1:d>0n?1:0;}
function rationalBounds(a){let left=0n,right=MAX_BITS,floorBits=0n;while(left<=right){const mid=(left+right)>>1n;if(compareRatToBits(a,mid)>=0){floorBits=mid;left=mid+1n;}else right=mid-1n;}const lo=numberAtBits(floorBits);if(compareRatToBits(a,floorBits)===0)return interval(lo);const hi=numberAtBits(floorBits+1n);return interval(lo,hi);}
const EXP_FIXED=1n<<192n;
let EXP_FACTORIAL=1n;for(let k=2n;k<=65n;k++)EXP_FACTORIAL*=k;
const ceilDiv=(n,d)=>(n+d-1n)/d;
function reducedExpBase(a,n){
 const input=floatRat(a),den=input.d*(1n<<BigInt(n)),numerator=input.n*EXP_FIXED,zLo=numerator/den,zHi=ceilDiv(numerator,den);
 let termLo=EXP_FIXED,termHi=EXP_FIXED,sumLo=EXP_FIXED,sumHi=EXP_FIXED;
 for(let k=1n;k<=64n;k++){const d=EXP_FIXED*k;termLo=termLo*zLo/d;termHi=ceilDiv(termHi*zHi,d);sumLo+=termLo;sumHi+=termHi;}
 sumHi+=ceilDiv(3n*EXP_FIXED,EXP_FACTORIAL);
 return interval(rationalBounds({n:sumLo,d:EXP_FIXED}).lo,rationalBounds({n:sumHi,d:EXP_FIXED}).hi);
}
function squarePositive(q){const r=multiply(q,q);return interval(Math.max(0,r.lo),r.hi);}
function expPoint(x){if(x===0)return interval(1);const negative=x<0,a=Math.abs(x);let z=a,n=0;while(z>1){z*=0.5;n++;}let q=reducedExpBase(a,n);if(negative)q=divide(interval(1),q);for(let i=0;i<n;i++)q=squarePositive(q);return interval(Math.max(0,q.lo),q.hi);}
export function exp(a){iv(a);return interval(expPoint(a.lo).lo,expPoint(a.hi).hi);}
function tanhPoint(x){if(x===0)return interval(0);if(x>MAX_VALUE/2)return interval(nextDown(1),1);if(x< -MAX_VALUE/2)return interval(-1,nextUp(-1));const q=expPoint(-2*Math.abs(x));let r=divide(subtract(interval(1),q),add(interval(1),q));r=interval(Math.max(0,r.lo),Math.min(1,r.hi));return x<0?interval(-r.hi,-r.lo):r;}
export function tanh(a){iv(a);return interval(tanhPoint(a.lo).lo,tanhPoint(a.hi).hi);}

const MASK=0xffffffff;
function seed32(seed){if(typeof seed==='number'){if(!Number.isInteger(seed)||seed<0||seed>MASK)throw new RangeError('Invalid numeric seed');return seed>>>0;}if(typeof seed!=='string'||seed.length>1024)throw new TypeError('Invalid seed');let h=2166136261;for(const b of new TextEncoder().encode(seed))h=Math.imul(h^b,16777619)>>>0;return h;}
function hash32(seed,index,channel){let h=(seed^Math.imul(index>>>0,0x9e3779b9)^Math.imul(channel>>>0,0x85ebca6b))>>>0;h=Math.imul(h^(h>>>16),0x7feb352d)>>>0;h=Math.imul(h^(h>>>15),0x846ca68b)>>>0;return (h^(h>>>16))>>>0;}
const unit=(s,k,c)=>hash32(s,k,c)/4294967296;
function configNumber(c,k,lo,hi){const x=c[k];if(!finiteNumber(x)||x<lo||x>hi)throw new TypeError('Invalid native config field: '+k);return x;}
function checkedLeaf(leaf){if(!leaf||typeof leaf!=='object'||typeof leaf.sample!=='function'||typeof leaf.at!=='function'||typeof leaf.ridges!=='function'||!leaf.config||!Array.isArray(leaf.dips)||!Array.isArray(leaf.domain)||leaf.domain.length!==2)throw new TypeError('A native leaf facade is required');const c=leaf.config;seed32(c.seed);for(const [k,l,h] of [['openingEndpoint',2,50000],['introVariation',0,1],['boundarySoftness',0,1],['stabilizer',0,1],['strength',0,2],['ramp',1,64],['tempo',.25,4],['contrast',0,16]])configNumber(c,k,l,h);if(!['none','zero','baseline','backbone'].includes(c.floor))throw new TypeError('Invalid native floor');if(c.ceilingMargin!==null&&(!finiteNumber(c.ceilingMargin)||c.ceilingMargin<0||c.ceilingMargin>16))throw new TypeError('Invalid native ceiling');for(const k of ['latent','velocity']){const v=c[k];if((!Array.isArray(v)&&!ArrayBuffer.isView(v))||v.length!==16||Array.from(v).some(x=>!finiteNumber(x)))throw new TypeError('Invalid native vector');}if(!leaf.domain.every(finiteNumber)||leaf.domain[0]!==1||leaf.domain[1]<leaf.domain[0]||leaf.domain[1]>50000)throw new TypeError('Invalid native domain');if(leaf.dips.length!==2||leaf.dips.some(d=>!d||!finiteNumber(d.center)||!finiteNumber(d.width)||d.width<=0||!finiteNumber(d.depth)||d.depth<=0))throw new TypeError('Invalid native dips');const rs=leaf.ridges();if(!Array.isArray(rs)||rs.length!==48)throw new TypeError('A native leaf must expose all 48 stored ridges');for(let i=0;i<48;i++){const q=rs[i];if(!q||q.index!==i||q.channel!==i+200||!Array.isArray(q.direction)&&!ArrayBuffer.isView(q.direction)||q.direction.length!==16||Array.from(q.direction).some(x=>!finiteNumber(x)))throw new TypeError('Invalid native ridge');for(const k of ['scale','phase','basePhase','slope','guidePhase','guideSlope'])if(!finiteNumber(q[k]))throw new TypeError('Invalid native ridge');if(q.scale<=0)throw new TypeError('Invalid native ridge scale');}return {c,rs,seed:seed32(c.seed)};}
function noise(p,seed,channel){if(Math.floor(p.hi)-Math.floor(p.lo)>1)return interval(-1-2048*Number.EPSILON,1+2048*Number.EPSILON);let out=null;const first=Math.floor(p.lo),last=Math.floor(p.hi);for(let k=first;k<=last;k++){const a=Math.max(p.lo,k),b=Math.min(p.hi,k+1);if(a>b)continue;const t=interval(a-k,b-k),u=unit(seed,k,channel),v=unit(seed,k+1,channel),na=2*u-1,nb=2*v-1;let q=add(interval(na),multiply(subtract(interval(nb),interval(na)),smooth5(t)));out=out? hull(out,q):q;}return out||interval(-1,1);}
function noiseScores({c,rs,seed},x,coordinates){let score=interval(0),guide=interval(0);const clock=c.openingEndpoint===100?x:add(interval(1),divide(multiply(subtract(x,interval(1)),interval(99)),interval(c.openingEndpoint-1))),u=subtract(clock,interval(100));for(const q of rs){let p,g;if(coordinates===undefined){p=add(interval(q.phase),multiply(u,interval(q.slope)));g=add(interval(q.guidePhase),multiply(u,interval(q.guideSlope)));}else{let projection=interval(0);for(let j=0;j<16;j++)projection=add(projection,multiply(interval(q.direction[j]),interval(coordinates[j])));p=add(add(interval(q.basePhase),projection),divide(u,interval(q.scale)));g=add(add(interval(q.basePhase),divide(projection,interval(32))),divide(u,interval(32*q.scale)));}score=add(score,divide(noise(p,seed,q.channel),interval(48)));guide=add(guide,divide(noise(g,seed,q.channel),interval(48)));}return {score,guide};}
function pointFloor(y,lo,e){if(e===0)return interval(Math.max(y,lo));if(y<=lo)return interval(lo);if(Number.isFinite(lo+e)&&y>=nextUp(lo+e))return interval(y);return add(interval(lo),multiply(interval(e),boundaryRamp(divide(subtract(interval(y),interval(lo)),interval(e)))));}
function pointCeil(y,hi,e){if(e===0)return interval(Math.min(y,hi));if(y>=hi)return interval(hi);if(Number.isFinite(hi-e)&&y<=nextDown(hi-e))return interval(y);return subtract(interval(hi),multiply(interval(e),boundaryRamp(divide(subtract(interval(hi),interval(y)),interval(e)))));}
function rampRange(t){
 return multiply(multiply(multiply(t,t),t),add(interval(6),multiply(t,add(interval(-8),multiply(interval(3),t)))));
}
function floorMap(y,lo,e){
 if(e.lo<0)throw new RangeError('A nonnegative boundary width is required');
 let out=null;const include=q=>{out=out===null?q:hull(out,q);};
 if(e.lo===0)include(interval(Math.max(y.lo,lo.lo),Math.max(y.hi,lo.hi)));
 if(y.lo<=lo.hi)include(lo);
 if(y.hi>=add(lo,e).lo)include(y);
 if(e.hi>0&&y.hi>lo.lo){
  const ratio=e.lo===0?interval(0,1):divide(subtract(y,lo),e),a=Math.max(0,ratio.lo),b=Math.min(1,ratio.hi);
  if(a<=b)include(add(lo,multiply(e,rampRange(interval(a,b)))));
 }
 if(out===null)throw new RangeError('Unresolved boundary branches');
 return out;
}
function ceilMap(y,hi,e){const q=floorMap(interval(-y.hi,-y.lo),interval(-hi.hi,-hi.lo),e);return interval(-q.hi,-q.lo);}
function widthEnclosure(lo,hi,a){if(!lo||!hi)return lo||hi?a:interval(0);const gap=subtract(hi,lo);if(gap.hi<=0||a.hi===0)return interval(0);const g0=Math.max(0,gap.lo),g1=Math.max(0,gap.hi);const f=(g,z)=>z===0||g===0?interval(0):divide(multiply(interval(z),interval(g)),add(interval(g),multiply(interval(2),interval(z))));const low=gap.lo>0&&a.lo>0?f(g0,a.lo).lo:0,high=f(g1,a.hi).hi;return interval(Math.max(0,low),Math.max(0,high));}
function normalizedApply(y,ug,c,gate){const floor=c.floor==='none'?null:c.floor==='zero'?interval(0):c.floor==='baseline'?interval(1):add(interval(1),ug);const ceiling=c.ceilingMargin===null?null:add(c.floor==='baseline'?interval(1):add(interval(1),ug),interval(c.ceilingMargin));const a=multiply(multiply(interval(c.strength),gate),interval(c.boundarySoftness)),e=widthEnclosure(floor,ceiling,a);if(floor&&ceiling&&ceiling.hi<=floor.lo)return floor;let z=floor?floorMap(y,floor,e):y;if(ceiling)z=ceilMap(z,ceiling,e);if(floor&&ceiling&&ceiling.lo<=floor.hi)z=hull(z,floor);return subtract(z,interval(1));}
function checkedCoordinates(h){if(h===undefined)return undefined;if((!Array.isArray(h)&&!ArrayBuffer.isView(h))||h.length!==16||Array.from(h).some(x=>!finiteNumber(x)||Math.abs(x)>1e6))throw new TypeError('Coordinates must contain 16 finite values in [-1000000,1000000]');return Array.from(h);}
function clockInterval(c,x){return c.openingEndpoint===100?x:add(interval(1),divide(multiply(subtract(x,interval(1)),interval(99)),interval(c.openingEndpoint-1)));}
function baselineInterval(c,x){
 const t=clockInterval(c,x),gaussian=(center,width,depth)=>{const q=subtract(t,interval(center));return multiply(interval(depth),exp(multiply(interval(-1),divide(multiply(q,q),interval(width)))));};
 return subtract(subtract(multiply(t,exp(divide(t,interval(100)))),gaussian(42,32,25)),gaussian(71,28,38));
}
function rawState(data,x,h){
 const {c,rs,seed}=data,{score,guide}=noiseScores({c,rs,seed},x,h),gate=smooth5(divide(subtract(x,interval(c.openingEndpoint)),interval(c.ramp)));
 const raw=multiply(multiply(multiply(interval(c.strength),gate),interval(2.2)),tanh(multiply(interval(c.contrast),score)));
 const ug=multiply(multiply(multiply(interval(c.strength),gate),interval(.25)),tanh(multiply(interval(2),guide))),w=smooth5(interval(c.stabilizer)),u=c.stabilizer===0?raw:c.stabilizer===1?ug:add(raw,multiply(subtract(ug,raw),w));
 const base=baselineInterval(c,x),unconstrainedValue=add(base,multiply(base,u)),unconstrainedGuideValue=add(base,multiply(base,ug));
 const floorValue=c.floor==='none'?null:c.floor==='zero'?interval(0):c.floor==='baseline'?base:unconstrainedGuideValue;
 const ceilingValue=c.ceilingMargin===null?null:add(c.floor==='baseline'?base:unconstrainedGuideValue,multiply(interval(c.ceilingMargin),base));
 const amplitude=multiply(multiply(interval(c.strength),gate),interval(c.boundarySoftness));let boundaryWidth;
 if(floorValue&&ceilingValue){
  if(floorValue===ceilingValue||amplitude.hi===0)boundaryWidth=interval(0);
  else{
   const gap=divide(subtract(ceilingValue,floorValue),base);
   if(gap.hi<=0)boundaryWidth=interval(0);
   else if(gap.lo>0&&amplitude.lo>0)boundaryWidth=multiply(base,divide(multiply(amplitude,gap),add(gap,multiply(interval(2),amplitude))));
   else{const cap=Math.min(multiply(interval(2),interval(amplitude.hi)).hi,multiply(interval(2),interval(Math.max(0,gap.hi))).hi);boundaryWidth=multiply(base,interval(0,cap));}
  }
 }else boundaryWidth=floorValue||ceilingValue?multiply(base,amplitude):interval(0);
 return Object.freeze({base,unconstrainedValue,unconstrainedGuideValue,floorValue,ceilingValue,boundaryWidth,u});
}
export function projectConstraintState(s){
 const {floorValue:lo,ceilingValue:hi,boundaryWidth:e}=s;
 if(lo&&hi&&hi.hi<=lo.lo)return lo;
 let value=lo?floorMap(s.unconstrainedValue,lo,e):s.unconstrainedValue;
 if(hi)value=ceilMap(value,hi,e);
 if(lo&&hi&&hi.lo<=lo.hi)value=hull(value,lo);
 return value;
}
export function constraintDeviation(s){return divide(subtract(projectConstraintState(s),s.base),s.base);}
export function nativeConstraintState(nativeLeaf,lo,hi,coordinates){
 const data=checkedLeaf(nativeLeaf),h=checkedCoordinates(coordinates);
 if(!finiteNumber(lo)||!finiteNumber(hi)||lo>hi||lo<Math.max(data.c.openingEndpoint,nativeLeaf.domain[0])||hi>nativeLeaf.domain[1])throw new RangeError('Raw state interval must lie after the opening inside its native domain');
 return rawState(data,interval(lo,hi),h);
}
export function nativeDeviationEnclosure(nativeLeaf,lo,hi,coordinates){
 if(!finiteNumber(lo)||!finiteNumber(hi)||lo>hi)throw new RangeError('Finite ordered x bounds are required');
 const data=checkedLeaf(nativeLeaf),h=checkedCoordinates(coordinates),{c}=data;
 if(lo<nativeLeaf.domain[0]||hi>nativeLeaf.domain[1])throw new RangeError('x interval is outside the native domain');
 if(hi<=c.openingEndpoint)return interval(0);
 const state=rawState(data,interval(Math.max(lo,c.openingEndpoint),hi),h);
 let result=c.floor==='none'&&c.ceilingMargin===null?state.u:constraintDeviation(state);
 return lo<=c.openingEndpoint?hull(result,interval(0)):result;
}

export function mixConstraintStates(a,b,weight,coherent=true){
 const out={};
 for(const key of ['base','unconstrainedValue','unconstrainedGuideValue','boundaryWidth','floorValue','ceilingValue']){
  if(a[key]===null&&b[key]===null)out[key]=null;
  else{if(a[key]===null||b[key]===null)throw new RangeError('Incompatible constraint states');out[key]=add(a[key],multiply(subtract(b[key],a[key]),weight));}
 }
 if(!coherent){
  let value=add(a.value??projectConstraintState(a),multiply(subtract(b.value??projectConstraintState(b),a.value??projectConstraintState(a)),weight));
  if(out.floorValue)value=interval(Math.max(value.lo,out.floorValue.lo),Math.max(value.hi,out.floorValue.hi));
  if(out.ceilingValue)value=interval(Math.min(value.lo,out.ceilingValue.lo),Math.min(value.hi,out.ceilingValue.hi));
  out.value=value;
 }
 return Object.freeze(out);
}
export function appliedDeviation(s){return divide(subtract(s.value??projectConstraintState(s),s.base),s.base);}
