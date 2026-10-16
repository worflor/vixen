export const WIDE_FORMAT='vixen-scaled-binary64/1';
const storage=new DataView(new ArrayBuffer(8)),LOW52=(1n<<52n)-1n;
const exponent=value=>{
 if(typeof value==='bigint')return value;
 if(typeof value==='number'&&Number.isSafeInteger(value))return BigInt(value);
 throw new TypeError('An exact integer exponent is required');
};
function roundRatio(n,d){
 let q=n/d;const r=n%d;
 if(2n*r>d||(2n*r===d&&(q&1n)))q++;
 return q;
}
function normalized(m,e){
 if(m===0n)return[0n,0n];
 const negative=m<0n;let n=negative?-m:m;
 const bits=n.toString(2).length,shift=bits-53;
 if(shift>0){n=roundRatio(n,1n<<BigInt(shift));e+=BigInt(shift);}
 else if(shift<0){n<<=BigInt(-shift);e+=BigInt(shift);}
 if(n===(1n<<53n)){n>>=1n;e++;}
 return[negative?-n:n,e];
}
export class Wide {
 constructor(m,e=0n){
  if(typeof m!=='bigint')throw new TypeError('An integer significand is required');
  [this.m,this.e]=normalized(m,exponent(e));Object.freeze(this);
 }
 static fromNumber(value){
  if(typeof value!=='number'||!Number.isFinite(value))throw new RangeError('Finite binary64 input required');
  storage.setFloat64(0,value,false);const bits=storage.getBigUint64(0,false),p=Number((bits>>52n)&2047n);
  const m=(bits&LOW52)|(p===0?0n:1n<<52n);
  return new Wide((bits>>63n)?-m:m,BigInt(p===0?-1074:p-1075));
 }
 static fromRatio(n,d,power=0n){
  if(typeof n!=='bigint'||typeof d!=='bigint')throw new TypeError('Exact integer numerator and denominator required');
  if(d===0n)throw new RangeError('Nonzero denominator required');
  const e=exponent(power);if(n===0n)return new Wide(0n);
  const negative=(n<0n)!==(d<0n);n=n<0n?-n:n;d=d<0n?-d:d;
  let p=n.toString(2).length-d.toString(2).length;
  if(p>=0?n<(d<<BigInt(p)):(n<<BigInt(-p))<d)p--;
  const shift=52-p,q=shift>=0?roundRatio(n<<BigInt(shift),d):roundRatio(n,d<<BigInt(-shift));
  return new Wide(negative?-q:q,e+BigInt(p-52));
 }
 static from(value){return value instanceof Wide?value:Wide.fromNumber(value);}
 get sign(){return this.m<0n?-1:this.m>0n?1:0;}
 neg(){return new Wide(-this.m,this.e);}
 abs(){return this.m<0n?this.neg():this;}
 scale(power){return new Wide(this.m,this.e+exponent(power));}
 add(other){
  const b=Wide.from(other);if(this.m===0n)return b;if(b.m===0n)return this;
  const a=this.e>=b.e?this:b,c=this.e>=b.e?b:this,d=a.e-c.e;
  if(d>54n)return a;
  return new Wide((a.m<<d)+c.m,c.e);
 }
 sub(other){return this.add(Wide.from(other).neg());}
 mul(other){const b=Wide.from(other);return new Wide(this.m*b.m,this.e+b.e);}
 div(other){
  const b=Wide.from(other);if(b.m===0n)throw new RangeError('Nonzero divisor required');
  if(this.m===0n)return this;
  const n=this.m<0n?-this.m:this.m,d=b.m<0n?-b.m:b.m,s=n<d?53n:52n;
  const q=roundRatio(n<<s,d),negative=(this.m<0n)!==(b.m<0n);
  return new Wide(negative?-q:q,this.e-b.e-s);
 }
 pow(power){
  let n=exponent(power);if(n<0n)return Wide.fromNumber(1).div(this.pow(-n));
  let base=this,out=Wide.fromNumber(1);
  while(n){if(n&1n)out=out.mul(base);n>>=1n;if(n)base=base.mul(base);}
  return out;
 }
 cmp(other){
  const b=Wide.from(other);if(this.sign!==b.sign)return this.sign>b.sign?1:-1;
  if(this.m===0n)return 0;
  if(this.e!==b.e)return(this.e>b.e?1:-1)*this.sign;
  return this.m>b.m?1:this.m<b.m?-1:0;
 }
 toNumber(){
  if(this.m===0n)return 0;
  const n=this.m<0n?-this.m:this.m,p=this.e+52n;
  if(p>1023n)return null;
  if(p>=-1022n)return this.sign*(Number(n)/2**52)*2**Number(p);
  const shift=-1074n-this.e;if(shift>53n)return null;
  const q=roundRatio(n,1n<<shift);return q===0n?null:this.sign*Number(q)*Number.MIN_VALUE;
 }
 toJSON(){
  return Object.freeze({format:WIDE_FORMAT,sign:this.sign,significand:this.m===0n?0:Number(this.m<0n?-this.m:this.m)/2**52,exponent2:this.m===0n?'0':String(this.e+52n)});
 }
 static decode(value){
  if(!value||typeof value!=='object'||Array.isArray(value)||Reflect.ownKeys(value).some(k=>!['format','sign','significand','exponent2'].includes(k)))throw new TypeError('Invalid scaled number');
  const{format,sign,significand,exponent2}=value;
  if(format!==WIDE_FORMAT||![-1,0,1].includes(sign)||typeof exponent2!=='string'||!/^(?:0|-?[1-9][0-9]*)$/.test(exponent2))throw new TypeError('Invalid scaled number');
  if(sign===0){if(significand!==0||exponent2!=='0')throw new TypeError('Noncanonical scaled zero');return Wide.fromNumber(0);}
  if(typeof significand!=='number'||!Number.isFinite(significand)||significand<1||significand>=2)throw new RangeError('Invalid scaled significand');
  return Wide.fromNumber(sign*significand).scale(BigInt(exponent2));
 }
}
