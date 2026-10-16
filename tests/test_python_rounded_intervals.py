import unittest,sys
import vixen_interval as I
from vixen import smooth5,boundary_ramp
class RoundedIntervalTests(unittest.TestCase):
 def test_program_polynomial_overshoot_is_enclosed(self):
  for sample,enclose in ((smooth5,I.smooth5),(boundary_ramp,I.boundaryRamp)):
   full=enclose(I.interval(0,1))
   for i in range(1,10000):
    x=1-i*sys.float_info.epsilon;value=sample(x)
    for bound in (full,enclose(I.interval(x)),enclose(I.interval(x,1))):
     self.assertLessEqual(bound.lo,value)
     self.assertLessEqual(value,bound.hi)
if __name__=='__main__':unittest.main()
