import math,unittest
from vixen_calculus import jet,jet_log,jet_tanh,jet_mul,jet_div

from vixen_jet_composition import finish_jet

class CalculusKernelTests(unittest.TestCase):
    def test_logarithmic_jet_divides_before_squaring(self):
        q=jet_log(jet(1e200,1e200,1e200))
        self.assertEqual((q.d1,q.d2),(1,0))
    def test_tanh_derivatives_survive_rounded_saturation(self):
        for x in [20,200]:
            e=math.exp(-2*x);p=4*e/(1+e)**2
            q=jet_tanh(jet(x,1e200,0))
            self.assertAlmostEqual(q.d1/(p*1e200),1,delta=1e-12)
            if x==200:self.assertAlmostEqual(q.d2/((-2*math.tanh(x)*p*1e200)*1e200),1,delta=1e-12)
    def test_product_jet_recovers_finite_cancelled_curvature(self):
        q=jet_mul(jet(1,2.0**500,-(2.0**1001)),jet(2.0**100,2.0**600,0))
        self.assertEqual(q.d2,0)

    def test_log_and_quotient_jets_recover_finite_cancelled_curvature(self):
        self.assertEqual(jet_log(jet(2.0**(-1000),2.0**(-400),2.0**200)).d2,0)
        q=jet_div(jet(1,2.0**523+2.0**500,2.0**1023),jet(1,2.0**500,-(2.0**1023)))
        self.assertEqual(q.d2,0)

    def test_first_derivative_availability_is_independent(self):
        out=finish_jet({'value':1},{'value':jet(1,2,math.inf)})
        self.assertEqual(out['slope'],2);self.assertIsNone(out['curvature'])
