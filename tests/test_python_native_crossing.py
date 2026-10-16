import unittest
from vixen import Vixen

class NativeCrossingTests(unittest.TestCase):
    def test_root_enclosure_covers_overlapping_handoffs(self):
        f=Vixen(seed='native-enclosure',velocity=[.03]*16).fork(at=120,operation='a',handoffWidth=45).fork(at=130,operation='b',handoffWidth=30)
        for a,b in [(90,110),(119,121),(125,150),(145,166),(190,191)]:
            box=f.enclose_deviation(a,b)
            for i in range(21):
                value=f.sample(a+(b-a)*i/20)['deviation']
                self.assertLessEqual(box[0],value);self.assertLessEqual(value,box[1])
    def test_query_proves_absence_and_an_actual_excursion(self):
        self.assertEqual(Vixen(contrast=0).next_crossing(.2,100,1000)['status'],'absent')
        r=Vixen(seed='native-enclosure').next_crossing(.2,100,250,{'tolerance':1e-4,'maxIntervals':4096})
        self.assertEqual(r['status'],'found');self.assertLessEqual(r['interval'][1]-r['interval'][0],1e-4)
    def test_valid_integral_float_seed_uses_the_same_enclosure(self):
        f=Vixen(seed=42.0)
        lo,hi=f.enclose_deviation(120,121)
        self.assertLessEqual(lo,f.sample(120.5)['deviation']);self.assertLessEqual(f.sample(120.5)['deviation'],hi)
    def test_custom_evaluator_does_not_inherit_native_continuity(self):
        controls={'evaluate':lambda x:-1 if x<100.5 else 1,'enclose':lambda a,b:(-1,-1) if b<100.5 else (1,1) if a>=100.5 else (-1,1),'tolerance':1e-4}
        self.assertNotEqual(Vixen().next_crossing(0,100,101,controls)['status'],'found')
