import math,unittest
from vixen_analysis import restore_occupancy,calibrate_occupancy,validate_correlation_matrix,next_crossing
class ReviewRegressions(unittest.TestCase):
    def test_restored_occupancy_requires_representable_ratio(self):
        r=calibrate_occupancy([1,2],{'targetFraction':.5,'outputThreshold':.5}).serialize()
        for slope,scale in [(float.fromhex('0x1.fffffffffffffp+1023'),math.ulp(0.)),(math.ulp(0.),float.fromhex('0x1.fffffffffffffp+1023'))]:
            with self.assertRaises((ValueError,TypeError)):restore_occupancy({**r,'slope':slope,'scale':scale})
    def test_exact_psd_eligibility(self):
        for matrix in [[[1,1+1e-12],[1+1e-12,1]],[[1,.5,.5],[.5,1,-.5-1e-12],[.5,-.5-1e-12,1]]]:
            with self.assertRaises(ValueError):validate_correlation_matrix(matrix)
        validate_correlation_matrix([[1,.5,.5],[.5,1,-.5],[.5,-.5,1]])
    def test_iteration_budget_is_honored(self):
        c=type('C',(),{'domain':(0,1),'sample':lambda self,x:x*x+1})()
        r=next_crossing(c,.5,0,1,{'continuous':True,'maxIterations':1,'maxIntervals':1000,'enclose':lambda a,b:(0,3)})
        self.assertEqual(r['status'],'unresolved');self.assertLessEqual(r['enclosureChecks'],1)
