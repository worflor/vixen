import json,math,unittest
from vixen_wide import Wide

class WideTests(unittest.TestCase):
    def test_extended_exponents_and_json(self):
        a=Wide.from_number(1.5).scale(10**12);b=Wide.from_number(2).scale(10**12)
        self.assertEqual(a.add(b).scale(-10**12).to_number(),3.5)
        self.assertEqual(a.mul(b).scale(-2*10**12).to_number(),3)
        self.assertEqual(a.div(b).to_number(),.75)
        self.assertEqual(a.add(a.neg()).to_number(),0)
        self.assertIsNone(a.to_number())
        self.assertEqual(Wide.decode(json.loads(json.dumps(a.to_json()))).cmp(a),0)
        with self.assertRaises(AttributeError):a.m=0
    def test_subnormal_ties_and_power_of_two_spacing(self):
        self.assertEqual(Wide.from_number(1).add(Wide.from_number(-.375).scale(-52)).to_number(),1-2.0**-53)
        minimum=float.fromhex('0x0.0000000000001p-1022')
        self.assertEqual(Wide.from_number(3*minimum).scale(-1).to_number(),2*minimum)
        self.assertIsNone(Wide.from_number(minimum).scale(-1).to_number())
    def test_canonical_input(self):
        s=Wide.from_number(-2).scale(1<<200).to_json()
        self.assertEqual(Wide.decode(s).cmp(Wide.from_number(-2).scale(1<<200)),0)
        for exponent2 in ['01','-0','+1','1e3']:
            with self.assertRaises((TypeError,ValueError)):Wide.decode(dict(s,exponent2=exponent2))
        with self.assertRaises((TypeError,ValueError)):Wide.from_number(math.inf)

    def test_large_decimal_exponents_do_not_change_global_integer_limits(self):
        import sys
        before=sys.get_int_max_str_digits()
        a=Wide.from_number(1.5).scale(1<<20000)
        self.assertEqual(Wide.decode(a.to_json()).cmp(a),0)
        self.assertEqual(sys.get_int_max_str_digits(),before)
