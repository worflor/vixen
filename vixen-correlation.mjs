// Eligibility is exact for the supplied binary64 matrix. Tolerance only bounds the returned numerical factor.
const view=new DataView(new ArrayBuffer(8));
const gcd=(a,b)=>{a=a<0n?-a:a;while(b){const t=a%b;a=b;b=t;}return a;};
function rational(n,d=1n){if(d===0n)throw new RangeError('Zero rational denominator');if(d<0n){n=-n;d=-d;}const g=gcd(n,d);return {n:n/g,d:d/g};}
const add=(a,b)=>rational(a.n*b.d+b.n*a.d,a.d*b.d);
const sub=(a,b)=>rational(a.n*b.d-b.n*a.d,a.d*b.d);
const mul=(a,b)=>rational(a.n*b.n,a.d*b.d);
const div=(a,b)=>rational(a.n*b.d,a.d*b.n);
const compare=(a,b)=>{const v=a.n*b.d-b.n*a.d;return v<0n?-1:v>0n?1:0;};
const abs=a=>({n:a.n<0n?-a.n:a.n,d:a.d});
function bits(x){view.setFloat64(0,x,false);return view.getBigUint64(0,false);}
function number(b){view.setBigUint64(0,b,false);return view.getFloat64(0,false);}
function fraction(x){
 const b=bits(x),e=Number((b>>52n)&2047n),f=b&((1n<<52n)-1n);let n=e===0?f:(1n<<52n)|f;
 if(b>>63n)n=-n;const shift=e===0?-1074:e-1075;
 return shift<0?rational(n,1n<<BigInt(-shift)):rational(n<<BigInt(shift));
}
function floorFloat(q,squared=false,bound=1){
 if(q.n<0n||compare(q,fraction(bound*bound))>0&&squared||!squared&&compare(q,fraction(bound))>0)throw new RangeError('Factor conversion outside its proved range');
 let lo=0n,hi=bits(bound);
 while(lo<hi){const mid=(lo+hi+1n)>>1n;let v=fraction(number(mid));if(squared)v=mul(v,v);if(compare(v,q)<=0)lo=mid;else hi=mid-1n;}
 return number(lo);
}
function ceilFloat(q){const f=floorFloat(q,false,2);return compare(fraction(f),q)===0?f:number(bits(f)+1n);}
export function exactCorrelationFactor(input,tolerance=1e-10){
 if(typeof tolerance!=='number'||!Number.isFinite(tolerance)||tolerance<=0)throw new RangeError('Factor tolerance must be finite and positive');
 if(!Array.isArray(input)||input.length===0)throw new RangeError('Correlation matrix must be nonempty');
 const n=input.length,rows=input.map(row=>{if(!Array.isArray(row)||row.length!==n)throw new RangeError('Correlation matrix must be square');return row.map(v=>{if(typeof v!=='number'||!Number.isFinite(v)||Math.abs(v)>1)throw new RangeError('Correlation coefficients must be finite in [-1,1]');return v;});});
 for(let i=0;i<n;i++){if(rows[i][i]!==1)throw new RangeError('Correlation diagonal must be exactly one');for(let j=0;j<i;j++)if(rows[i][j]!==rows[j][i])throw new RangeError('Correlation matrix must be exactly symmetric');}
 const original=rows.map(row=>row.map(fraction)),work=original.map(row=>row.slice()),factor=Array.from({length:n},()=>Array(n).fill(0));
 for(let j=0;j<n;j++){
  const pivot=work[j][j];if(pivot.n<0n)throw new RangeError('Correlation matrix is not positive semidefinite');
  if(pivot.n===0n){for(let i=j+1;i<n;i++)if(work[i][j].n!==0n)throw new RangeError('Nonzero column at a zero PSD pivot');continue;}
  factor[j][j]=floorFloat(pivot,true);
  for(let i=j+1;i<n;i++){const col=work[i][j];factor[i][j]=(col.n<0n?-1:1)*floorFloat(div(mul(col,col),pivot),true);}
  for(let i=j+1;i<n;i++)for(let k=i;k<n;k++)work[k][i]=sub(work[k][i],div(mul(work[k][j],work[i][j]),pivot));
 }
 const numeric=factor.map(row=>row.map(fraction));let worst=rational(0n);
 for(let i=0;i<n;i++)for(let j=0;j<=i;j++){let covariance=rational(0n);for(let k=0;k<=j;k++)covariance=add(covariance,mul(numeric[i][k],numeric[j][k]));const error=abs(sub(covariance,original[i][j]));if(compare(error,worst)>0)worst=error;}
 if(compare(worst,fraction(tolerance))>0)throw new RangeError('Numerical factor cannot meet the requested tolerance');
 return Object.freeze({matrix:Object.freeze(rows.map(Object.freeze)),factor:Object.freeze(factor.map(Object.freeze)),tolerance,factorError:ceilFloat(worst),validation:'exact-binary64-ldlt'});
}
