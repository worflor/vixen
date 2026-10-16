import math,unittest
from vixen_analysis import make_lipschitz_enclosure
class IntervalContractTests(unittest.TestCase):
    def test_adjacent_coordinates_are_enclosed_outward(self):
        a=1.;b=math.nextafter(a,math.inf);lo,hi=make_lipschitz_enclosure(lambda x:x,1)(a,b)
        self.assertLessEqual(lo,a);self.assertGreaterEqual(hi,b)
