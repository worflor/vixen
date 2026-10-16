import random,unittest
from fractions import Fraction
from vixen_wide import Wide

class WideRatioTests(unittest.TestCase):
    def test_exact_rational_constructor_and_scale(self):
        for n,d,want in [(3,2,1.5),(-3,2,-1.5),(3,-2,-1.5),(1,3,1/3),(0,7,0)]:self.assertEqual(Wide.from_ratio(n,d).to_number(),want)
        self.assertEqual(Wide.from_ratio((1<<53)+1,2).to_number(),2**52)
        q=Wide.from_ratio(3,2,10**12);self.assertEqual(q.to_json()['exponent2'],'1000000000000');self.assertEqual(q.to_json()['significand'],1.5)
        for args in [(1,0),(True,2),(1,False),(1,2,1.5)]:
            with self.assertRaises((TypeError,ValueError)):Wide.from_ratio(*args)
    def test_independent_fraction_rounding(self):
        rng=random.Random(0xA710)
        for _ in range(512):
            n=rng.getrandbits(250)*rng.choice([-1,1]);d=rng.getrandbits(250)+1
            self.assertEqual(Wide.from_ratio(n,d).to_number(),float(Fraction(n,d)))
