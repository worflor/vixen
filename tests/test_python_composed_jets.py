import math,unittest
from vixen import Vixen,polynomial_guide,couple,restore

class ComposedJetTests(unittest.TestCase):
    def close(self,a,b):self.assertLessEqual(abs(a-b),1e-10*max(1,abs(b)))
    def test_additive_product_rule_and_primal(self):
        p=Vixen(seed='guide-jet');c=p.influence(polynomial_guide([.2,.01],center=150),at=120,handoffWidth=10,amount=.6,until=170,releaseWidth=10,mode='add')
        for x,w,w1,w2 in [(120,0,0,0),(125,.3,.1125,0),(150,.6,0,0),(175,.3,-.1125,0),(180,0,0,0)]:
            q=p.jet(x);b=q['sample']['base'];b1=q['fields']['base']['d1'];b2=q['fields']['base']['d2'];v=.2+.01*(x-150);r=c.jet(x)
            self.assertEqual(r['sample'],c.sample(x))
            self.close(r['slope'],q['slope']+b1*w*v+b*(w1*v+w*.01))
            self.close(r['curvature'],q['curvature']+b2*w*v+2*b1*(w1*v+w*.01)+b*(w2*v+2*w1*.01))
    def test_value_guide_takeover_and_operators(self):
        c=Vixen().influence(polynomial_guide([2,.3,.02],center=150),at=120,handoffWidth=10,amount=1)
        q=c.jet(150);self.assertEqual(q['slope'],.3);self.assertEqual(q['curvature'],.04)
        c=c.impulse(dict(at=140,magnitude=.2,attack=5,relaxation=10)).redistribute([160,180],[1])
        for x in [139,142.5,150,159,170,180]:
            q=c.jet(x);self.assertEqual(q['sample'],c.sample(x));self.assertTrue(math.isfinite(q['slope']));self.assertTrue(math.isfinite(q['curvature']));self.assertEqual(restore(c.snapshot()).jet(x),q)
    def test_coupling_equality_does_not_erase_derivatives(self):
        a=Vixen().influence(polynomial_guide([0,1],center=150),at=120,handoffWidth=10,amount=1,reference='baseline')
        b=Vixen().influence(polynomial_guide([0,-1],center=150),at=120,handoffWidth=10,amount=1,reference='baseline')
        c=couple([a,b],1,[140,160]).curves[0];q=c.jet(150);base=Vixen().jet(150)
        self.assertEqual(q['sample'],c.sample(150));self.close(q['slope'],base['fields']['base']['d1']);self.close(q['curvature'],base['fields']['base']['d2'])
    def test_legacy_discontinuity_is_unavailable(self):
        p=Vixen(seed=1,floor='baseline',boundarySoftness=1).fork(at=120,operation='overlap',handoffWidth=80)
        c=p.influence(polynomial_guide([.1]),at=150,handoffWidth=10,amount=.5,reference='baseline');q=c.jet(150)
        self.assertIsNone(q['slope']);self.assertIsNone(q['curvature']);self.assertIn('discontinuous',q['differentiability']['rationale'])

class ComposedAvailabilityTests(unittest.TestCase):
    def test_finite_slope_survives_curvature_overflow(self):
        e=math.ulp(1.);c=Vixen(floor='none').impulse(at=1,magnitude=1e280,attack=8*e,relaxation=1);q=c.jet(1+2*e)
        self.assertTrue(q['availability']['slope']);self.assertFalse(q['availability']['curvature']);self.assertTrue(math.isfinite(q['slope']));self.assertIsNone(q['curvature'])
    def test_fixed_path_primal_and_deep_graph(self):
        c=Vixen(velocity=(.07,)+(0,)*15).influence(polynomial_guide([.1,.003],center=150),at=120,handoffWidth=10,reference='baseline');h=(.3,)*16
        self.assertEqual(c.jet(150,h)['sample'],c.at(150,h));self.assertTrue(c.jet(150,h)['availability']['curvature'])
        c=Vixen();g=polynomial_guide([.1])
        for _ in range(1200):c=c.influence(g,at=120,handoffWidth=10,amount=.03,reference='baseline')
        q=c.jet(150);self.assertEqual(q['sample'],c.sample(150));self.assertTrue(math.isfinite(q['slope']));self.assertTrue(math.isfinite(q['curvature']));self.assertEqual(restore(c.snapshot()).jet(150),q)
