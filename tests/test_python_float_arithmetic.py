import json,math,random,struct,subprocess,unittest
from fractions import Fraction
from pathlib import Path
from vixen_float import exact_mean,sum_products

ROOT=Path(__file__).resolve().parents[1]
def bits(x):return struct.pack('>d',x).hex()
def rounded(q):
    try:return float(q)
    except OverflowError:return math.inf if q>0 else -math.inf

class FloatArithmeticTests(unittest.TestCase):
    def test_exact_dyadic_arithmetic_against_fraction_oracle(self):
        rng=random.Random(0xF0A17)
        def finite():
            while True:
                x=struct.unpack('>d',rng.getrandbits(64).to_bytes(8,'big'))[0]
                if math.isfinite(x):return x
        tiny=math.ulp(0.0);large=float.fromhex('0x1.fffffffffffffp+1023')
        vectors=[[large,large,large],[large,-large,2*tiny],[-0.0,-0.0],[-0.0,0.0]]
        vectors += [[finite() for _ in range(rng.randint(1,32))] for _ in range(256)]
        products=[[[1e308,2],[-1e308]],[[tiny,2]],[[1e250,1e-250,1e220]]]
        products += [[[finite() for _ in range(rng.randint(1,3))] for _ in range(rng.randint(1,5))] for _ in range(128)]
        expected_means=[]
        for values in vectors:
            q=sum((Fraction.from_float(v) for v in values),Fraction())/len(values)
            value=rounded(q)
            if all(v==0 and math.copysign(1,v)<0 for v in values):value=-0.0
            expected_means.append(bits(value))
            self.assertEqual(bits(exact_mean(values)),bits(value))
        expected_products=[]
        for terms in products:
            q=Fraction()
            for factors in terms:
                product=Fraction(1)
                for factor in factors:product*=Fraction.from_float(factor)
                q+=product
            value=rounded(q);expected_products.append(bits(value))
            self.assertEqual(bits(sum_products(terms)),bits(value))
        script="""import{exactMean,sumProducts}from'./vixen-float.mjs';
let text='';for await(const c of process.stdin)text+=c;
const{vectors,products}=JSON.parse(text),b=new DataView(new ArrayBuffer(8));
const bits=x=>{b.setFloat64(0,x,false);return b.getBigUint64(0,false).toString(16).padStart(16,'0')};
console.log(JSON.stringify({means:vectors.map(v=>bits(exactMean(v))),products:products.map(v=>bits(sumProducts(v)))}));
"""
        result=subprocess.run(['node','--input-type=module','-e',script],cwd=ROOT,input=json.dumps(dict(vectors=vectors,products=products)),capture_output=True,text=True,check=True)
        actual=json.loads(result.stdout)
        self.assertEqual(actual['means'],expected_means)
        self.assertEqual(actual['products'],expected_products)
