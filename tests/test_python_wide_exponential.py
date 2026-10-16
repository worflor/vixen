import json
from pathlib import Path
import math,unittest
from decimal import Decimal,localcontext,ROUND_HALF_EVEN
from vixen_exponential import wide_exp
from vixen_wide import Wide

class WideExponentialTests(unittest.TestCase):
    def test_native_values_and_huge_exponents(self):
        for x in [-700,-100,-1,-.1,0,.1,1,100,700]:
            want=math.exp(x);self.assertLessEqual(abs(wide_exp(x).to_number()-want),8*math.ulp(1.0)*want)
        self.assertEqual(wide_exp(0).to_number(),1);self.assertEqual(wide_exp(math.ulp(0.0)).to_number(),1)
        for x in [1e308,-1e308]:
            q=wide_exp(x);self.assertIsNone(q.to_number());self.assertEqual(q.sign,1);self.assertEqual(Wide.decode(q.to_json()).to_json(),q.to_json())
        for x in [float('inf'),float('-inf'),float('nan'),'1',True]:
            with self.assertRaises((TypeError,ValueError)):wide_exp(x)
    def test_independent_high_precision_reduction(self):
        with localcontext() as ctx:
            ctx.prec=420;ln2=Decimal(2).ln()
            for x in [1e308,-1e308,1e100,-1e100,1e12,-1e12,1000.25,-1000.25]:
                value=Decimal.from_float(x);k=(value/ln2).to_integral_value(rounding=ROUND_HALF_EVEN);r=value-k*ln2
                expected=Wide.from_number(float(r.exp())).scale(int(k));actual=wide_exp(x)
                gap=abs(actual.m-expected.m) if actual.e==expected.e else float('inf')
                self.assertLessEqual(gap,4,(x,actual.to_json(),expected.to_json()))

class FrozenExponentialTests(unittest.TestCase):
    def test_full_frozen_reduction_range(self):
        data=json.loads((Path(__file__).parent/'fixtures/scaled-binary64-1/exponential.json').read_text());self.assertEqual(len(data['cases']),21)
        for c in data['cases']:
            q=wide_exp(c['x']);m,e=int(c['m']),int(c['e']);gap=q.e-e;self.assertLessEqual(abs(gap),1)
            self.assertLessEqual(abs((q.m<<max(0,gap))-(m<<max(0,-gap))),8)

class ExponentialCertificateBindingTests(unittest.TestCase):
    def test_every_runtime_uses_the_actually_verified_constant(self):
        import vixen_exponential as module
        root=Path(__file__).resolve().parents[1];data=json.loads((root/'research/wide-ln2-constant.json').read_text());integer=data['roundedConstant']['n']
        self.assertEqual(data['proofReplayed'],'valid');self.assertTrue(data['verifiedEnclosureInsideRoundingCell']);self.assertEqual(str(module.LN2),integer)
        javascript=(root/'vixen-exponential.mjs').read_text().split('const LN2=')[1].split('n,BITS=')[0]
        java=(root/'java/src/dev/woflo/vixen/WideExponential.java').read_text().split('LN2=new BigInteger("')[1].split('"')[0]
        self.assertEqual(javascript,integer);self.assertEqual(java,integer)
        receipt=json.loads((root/'research/proofs/wide-ln2.replay.json').read_text())['verdict'];self.assertEqual(receipt['kind'],'valid');self.assertEqual(receipt['roots'],[data['proofRoot']])
