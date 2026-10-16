const storage = new DataView(new ArrayBuffer(8));
const FRACTION = (1n << 52n) - 1n;
function dyadic(value) {
  if (typeof value !== 'number' || !Number.isFinite(value)) throw new RangeError('Finite binary64 operand required');
  storage.setFloat64(0,value,false);
  const bits=storage.getBigUint64(0,false), encoded=Number((bits>>52n)&2047n);
  const mantissa=(bits&FRACTION)|(encoded===0?0n:1n<<52n);
  return [(bits>>63n)?-mantissa:mantissa,encoded===0?-1074:encoded-1075];
}
function rounded(numerator,denominator,exponent) {
  if(numerator===0n)return 0;
  const negative=numerator<0n;
  let n=negative?-numerator:numerator;
  let k=n.toString(2).length-denominator.toString(2).length;
  if(k>=0?n<(denominator<<BigInt(k)):(n<<BigInt(-k))<denominator)k--;
  const shift=Math.max(k+exponent-52,-1074),scale=exponent-shift;
  let d=denominator;
  if(scale>=0)n<<=BigInt(scale);else d<<=BigInt(-scale);
  let q=n/d;const rem=n%d;
  if(2n*rem>d||(2n*rem===d&&(q&1n)))q++;
  const value=Number(q)*2**shift;
  return negative?-value:value;
}
function sum(terms) {
  let exponent=Infinity;
  for(const [n,e]of terms)if(n!==0n)exponent=Math.min(exponent,e);
  if(exponent===Infinity)return[0n,0];
  let numerator=0n;
  for(const [n,e]of terms)if(n!==0n)numerator+=n<<BigInt(e-exponent);
  return[numerator,exponent];
}
export function exactMean(values) {
  if(!Array.isArray(values)||!values.length)throw new TypeError('A nonempty numeric array is required');
  for(const v of values)if(typeof v!=='number'||!Number.isFinite(v))throw new RangeError('Finite binary64 operand required');
  if(values.every(v=>Object.is(v,values[0])))return values[0];
  const[n,e]=sum(values.map(dyadic));
  return rounded(n,BigInt(values.length),e);
}
export function sumProducts(terms,divisors=[]) {
  const products=terms.map(factors=>{
    let n=1n,e=0;
    for(const factor of factors){const[m,p]=dyadic(factor);n*=m;e+=p;}
    return[n,e];
  });
  let[n,e]=sum(products),denominator=1n;
  for(const divisor of divisors){const[m,p]=dyadic(divisor);if(m===0n)throw new RangeError('Nonzero divisor required');denominator*=m;e-=p;}
  if(denominator<0n){n=-n;denominator=-denominator;}
  return rounded(n,denominator,e);
}
