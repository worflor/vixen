import unittest
from vixen_interval import interval,_floor_map,_ceil_map

def ramp(t):return 0 if t<=0 else t if t>=1 else t*t*t*(6+t*(-8+3*t))
def floor(y,l,e):return l if y<=l else y if e==0 or y>=l+e else l+e*ramp((y-l)/e)

class BoundaryIntervalTests(unittest.TestCase):
    def test_interior_extrema_in_moving_bounds(self):
        f=_floor_map(interval(.5),interval(-.2,.2),interval(1));c=_ceil_map(interval(-.5),interval(-.2,.2),interval(1))
        for i in range(401):
            l=-.2+i/1000;v=floor(.5,l,1);u=-floor(.5,-l,1)
            self.assertLessEqual(f.lo,v);self.assertLessEqual(v,f.hi)
            self.assertLessEqual(c.lo,u);self.assertLessEqual(u,c.hi)
    def test_hard_to_soft_width_intervals(self):
        f=_floor_map(interval(.2,.7),interval(-.2,.2),interval(0,1))
        for y in [.2,.5,.7]:
            for l in [-.2,.17,.2]:
                for e in [0,.001,.5,1]:
                    v=floor(y,l,e);self.assertLessEqual(f.lo,v);self.assertLessEqual(v,f.hi)
