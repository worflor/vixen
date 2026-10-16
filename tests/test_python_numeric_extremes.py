import math,sys,unittest
from vixen import Vixen,polynomial_guide,couple,perturbation_jet

class NumericExtremesTests(unittest.TestCase):
    def test_raw_overflow_and_representable_weighted_target(self):
        for mode in ['blend','add']:
            for reference in ['baseline','backbone']:
                c=Vixen().influence(polynomial_guide([1e100]),at=100,amount=1,mode=mode,reference=reference)
                with self.assertRaises(ValueError):c.sample(50000)
        p=Vixen();s=p.sample(50000)
        c=p.influence(polynomial_guide([1e250]),at=100,amount=1e-250,reference='baseline')
        v=c.sample(50000)
        self.assertTrue(math.isfinite(v['value']))
        self.assertAlmostEqual((v['unconstrainedValue']-s['unconstrainedValue'])/s['base'],1,places=12)

    def test_far_boundary_width(self):
        c=Vixen(floor='baseline',ceilingMargin=.2,boundarySoftness=.5).influence(polynomial_guide([.3]),at=100,reference='baseline')
        self.assertTrue(all(math.isfinite(v) for v in c.sample(50000).values() if isinstance(v,(int,float))))

    def test_equal_extreme_coupling_is_identity(self):
        curves=[Vixen().impulse(at=1,attack=.0002,relaxation=.0002,magnitude=sys.float_info.max) for _ in range(3)]
        samples=tuple(c.sample(1.0001) for c in curves)
        self.assertTrue(all(math.isfinite(s['deviation']) for s in samples))
        self.assertEqual(couple(curves,1,[1,2]).sample(1.0001),samples)

    def test_zero_and_narrow_impulse_jets(self):
        j=perturbation_jet(dict(type='impulse',at=0,magnitude=0,attack=1e-200,relaxation=1),5e-201)
        self.assertEqual(j,dict(value=0,first=0,second=0))
        j=perturbation_jet(dict(type='impulse',at=0,magnitude=1e-300,attack=1e-200,relaxation=1),2.5e-201)
        self.assertTrue(all(math.isfinite(v) for v in j.values()))
        self.assertGreater(j['first'],0);self.assertGreater(j['second'],0)

    def test_influence_bounds_contain_extreme_finite_parent(self):
        p=Vixen().impulse(dict(at=1,magnitude=1e300,attack=.0002,relaxation=.0002))
        c=p.influence(polynomial_guide([0]),at=1,amount=0)
        d=c.sample(1.0001)['deviation']
        self.assertLessEqual(c.bounds['deviation'][0],d)
        self.assertLessEqual(d,c.bounds['deviation'][1])
