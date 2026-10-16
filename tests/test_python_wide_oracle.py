import json,random,struct,subprocess,unittest
from fractions import Fraction
from pathlib import Path
from vixen_wide import Wide

ROOT=Path(__file__).resolve().parents[1]
def bits(x):return struct.pack('>d',x).hex()

class WideOracleTests(unittest.TestCase):
    def test_common_huge_exponents_against_exact_fraction_rounding(self):
        rng=random.Random(0xB1F10A7);cases=[]
        for _ in range(256):
            a=struct.unpack('>d',((rng.getrandbits(1)<<63)|((1023+rng.randint(-200,200))<<52)|rng.getrandbits(52)).to_bytes(8,'big'))[0]
            b=struct.unpack('>d',((rng.getrandbits(1)<<63)|((1023+rng.randint(-200,200))<<52)|rng.getrandbits(52)).to_bytes(8,'big'))[0]
            cases.extend(dict(a=a,b=b,op=op) for op in ['add','mul','div'])
        script="""import{Wide}from './vixen-wide.mjs';let t='';for await(const s of process.stdin)t+=s;const d=new DataView(new ArrayBuffer(8));console.log(JSON.stringify(JSON.parse(t).map(c=>{const n=1000000000000n,a=Wide.fromNumber(c.a).scale(n),b=Wide.fromNumber(c.b).scale(n),scale=c.op==='add'?-n:c.op==='mul'?-2n*n:0n;const result=a[c.op](b).scale(scale).toNumber();d.setFloat64(0,result,false);return d.getBigUint64(0,false).toString(16).padStart(16,'0')})));"""
        p=subprocess.run(['node','--input-type=module','-e',script],input=json.dumps(cases),cwd=ROOT,text=True,capture_output=True,check=True)
        outputs=json.loads(p.stdout);self.assertEqual(len(outputs),len(cases))
        for c,out in zip(cases,outputs):
            a,b=Fraction.from_float(c['a']),Fraction.from_float(c['b'])
            exact=a+b if c['op']=='add' else a*b if c['op']=='mul' else a/b
            expected=bits(float(exact))
            left,right=Wide.from_number(c['a']).scale(10**12),Wide.from_number(c['b']).scale(10**12)
            result=getattr(left,c['op'])(right)
            scale=-(10**12) if c['op']=='add' else -2*10**12 if c['op']=='mul' else 0
            self.assertEqual(bits(result.scale(scale).to_number()),expected)
            self.assertEqual(out,expected)

class WidePowerBoundaryTests(unittest.TestCase):
    def test_gap54_and_lower_neighbour_ties(self):
        a=Wide(1<<52);lower=Wide((1<<53)-1,-1);small=Wide(-((1<<52)+1),-54)
        self.assertEqual(a.add(small).to_json(),lower.to_json())
        self.assertEqual(a.neg().sub(small).to_json(),lower.neg().to_json())
        self.assertEqual(a.add(Wide(-1,-2)).to_json(),a.to_json())
        self.assertEqual(a.add(Wide(-((1<<53)-1),-55)).to_json(),a.to_json())
