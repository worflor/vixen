import json,math,random,struct,subprocess,unittest
from fractions import Fraction
from pathlib import Path
from vixen_float import sum_products

ROOT=Path(__file__).resolve().parents[1]
def bits(x):return struct.pack('>d',x).hex()
def exact(terms,divisors):
    n=sum((math.prod(Fraction.from_float(x) for x in factors) for factors in terms),Fraction())
    for x in divisors:n/=Fraction.from_float(x)
    try:return float(n)
    except OverflowError:return math.inf if n>0 else -math.inf

class QuotientRoundingTests(unittest.TestCase):
    def test_exact_fraction_oracle_and_js_for_extreme_quotients(self):
        rng=random.Random(0xF10A7)
        def finite():
            while True:
                x=struct.unpack('>d',rng.getrandbits(64).to_bytes(8,'big'))[0]
                if math.isfinite(x) and x!=0:return x
        cases=[{'terms':[[2.0**200,2.0**-1000],[-1,2.0**-400,2.0**-400]],'divisors':[2.0**-1000]*2}]
        for _ in range(512):
            cases.append({'terms':[[finite() for _ in range(rng.randint(1,3))] for _ in range(rng.randint(1,4))],'divisors':[finite() for _ in range(rng.randint(1,3))]})
        script="""import{sumProducts}from './vixen-float.mjs';let text='';for await(const s of process.stdin)text+=s;const d=new DataView(new ArrayBuffer(8));console.log(JSON.stringify(JSON.parse(text).map(c=>{d.setFloat64(0,sumProducts(c.terms,c.divisors),false);return d.getBigUint64(0,false).toString(16).padStart(16,'0')})));"""
        p=subprocess.run(['node','--input-type=module','-e',script],input=json.dumps(cases),cwd=ROOT,capture_output=True,text=True,check=True)
        actual=json.loads(p.stdout)
        self.assertEqual(len(actual),len(cases))
        for case,out in zip(cases,actual):
            wanted=bits(exact(case['terms'],case['divisors']))
            self.assertEqual(out,wanted)
            self.assertEqual(bits(sum_products(case['terms'],case['divisors'])),wanted)
