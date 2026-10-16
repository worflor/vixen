import math
import unittest
from types import SimpleNamespace
from vixen import Vixen
from vixen_calculus import leaf_jet, native_normalized

def leaf(c):
    return SimpleNamespace(config=c.config,dips=c.dips,ridges=c.ridges,sample=c.sample,at=c.at,domain=c.domain)
def near(a,b,t=2e-5):
    if abs(a-b)>t*max(1,abs(a),abs(b)):raise AssertionError(f'{a} != {b}')

class CalculusTests(unittest.TestCase):
    def test_moving_path_and_primal(self):
        c=leaf(Vixen(seed='calculus',introVariation=.6,stabilizer=.35,openingEndpoint=80,velocity=(.08,)*16));x=180.;h=.01;p=leaf_jet(c,x)
        d=(c.sample(x+h)['value']-c.sample(x-h)['value'])/(2*h);dd=(c.sample(x+h)['value']-2*c.sample(x)['value']+c.sample(x-h)['value'])/(h*h)
        self.assertEqual(dict(p['sample']),c.sample(x));near(p['slope'],d,2e-4);near(p['curvature'],dd,3e-3)
    def test_fixed_coordinates_and_timeline_rejection(self):
        c=leaf(Vixen(seed=9,openingEndpoint=140));h=(.4,)*16;x=230.;eps=.01;p=leaf_jet(c,x,h);d=(c.at(x+eps,h)['value']-c.at(x-eps,h)['value'])/(2*eps)
        near(p['slope'],d,2e-4)
        with self.assertRaises(ValueError):leaf_jet(c,x,(1,2))
        with self.assertRaises(TypeError):leaf_jet(Vixen().fork(at=120,operation='branch'),130)
    def test_opening_and_hard_boundary(self):
        c=leaf(Vixen(seed='edge'));p=leaf_jet(c,100);self.assertIsNone(p['fields']['score']['d1']);self.assertTrue(p['differentiability']['value'])
        q=leaf_jet(leaf(Vixen(floor='baseline',boundarySoftness=0)),100);self.assertTrue(math.isfinite(q['slope']));self.assertTrue(q['differentiability']['value']);near(q['slope'],2*math.e+(25/16)*58*math.exp(-(58**2)/32)+(19/7)*29*math.exp(-(29**2)/28),1e-12)
    def test_negative_and_zero_multiplier(self):
        found=None
        for seed in range(12):
            c=leaf(Vixen(seed=seed,floor='zero',strength=2,contrast=16,boundarySoftness=0))
            for i in range(1561):
                x=120+i*.5;z=native_normalized(c,x)
                if z['value']==0:found=(seed,x,z);break
            if found:break
        self.assertIsNotNone(found);seed,x,z=found;self.assertEqual(z['multiplier'],0);self.assertEqual(dict(z['signedLogValue']),{'sign':0,'logAbs':None})
        n=native_normalized(leaf(Vixen(seed=seed,strength=2,contrast=16)),x);self.assertLess(n['multiplier'],0);self.assertEqual(n['signedLogValue']['sign'],-1)
    def test_large_domain(self):
        c=leaf(Vixen(seed='far',openingEndpoint=50000,introVariation=1));z=native_normalized(c,2**40)
        self.assertEqual(z['base'],None);self.assertEqual(z['value'],None);self.assertTrue(math.isfinite(z['logBase']));self.assertTrue(math.isfinite(z['score']))
        p=native_normalized(c,50001);t=1+(50001-1)*99/(50000-1);self.assertLess(p['logBase'],math.log(t)+t/100)

if __name__=='__main__':unittest.main()


class NormalizedHistoryTests(unittest.TestCase):
    def test_historical_and_active_blends_preserve_scalar_samples(self):
        from vixen import Vixen
        c=Vixen(seed='history').fork(at=120,handoffWidth=20,operation='later');z=c.sample_normalized(110);p=c.sample(110)
        self.assertAlmostEqual(z['multiplier'],p['multiplier'],places=12);self.assertAlmostEqual(z['score'],p['score'],places=12)
        a=c.sample_normalized(130);b=c.sample(130)
        self.assertAlmostEqual(a['multiplier'],b['multiplier'],places=12);self.assertAlmostEqual(a['score'],b['score'],places=12);self.assertEqual(a['value'],b['value'])
