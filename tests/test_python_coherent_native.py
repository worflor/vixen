import unittest
import vixen as V

def ramp(t):return 0 if t<=0 else t if t>=1 else t*t*t*(6+t*(-8+3*t))
def policy(y,p):
    e,lo,hi=p['boundaryWidth'],p['floorValue'],p['ceilingValue']
    if lo is not None and hi is not None and hi<=lo:return lo
    if lo is not None:y=lo if y<=lo else y if e==0 or y>=lo+e else lo+e*ramp((y-lo)/e)
    if hi is not None:y=hi if y>=hi else y if e==0 or y<=hi-e else hi-e*ramp((hi-y)/e)
    return y

def build():return getattr(V,'create_coherent',V.Vixen)(seed=1,floor='baseline',boundarySoftness=.1).fork(at=120,handoffWidth=80,operation='branch')

class CoherentNativeTests(unittest.TestCase):
    def test_constraint_state_closure(self):
        c=build()
        for x in [125,140,150,160,175,190]:
            p=c.sample(x);self.assertEqual(p['value'],policy(p['unconstrainedValue'],p));self.assertEqual(p['guideValue'],policy(p['unconstrainedGuideValue'],p))
        self.assertEqual(c.algorithm,V.COHERENT_ALGORITHM);self.assertNotEqual(c.algorithm,'vixen-hyperfield-c2/4')
    def test_influence_activation_release_and_replay(self):
        parent=build();c=parent.influence(V.polynomial_guide([.3]),at=150,amount=1,handoffWidth=10,reference='baseline',until=180,releaseWidth=10)
        for edge in [150,190]:
            p=c.sample(edge);left=c.sample(edge-1e-7);right=c.sample(edge+1e-7)
            self.assertLess(abs(right['value']-left['value'])/max(1,abs(p['value'])),1e-6)
            self.assertEqual(p,parent.sample(edge))
        r=V.restore(c.snapshot())
        for x in [120,149,150,155,185,190,220]:self.assertEqual(r.sample(x),c.sample(x))

    def test_jets_active_checkpoints_and_versioned_forks(self):
        c=build();q=c.jet(150);h=1e-4;finite=(c.sample(150+h)['value']-c.sample(150-h)['value'])/(2*h)
        self.assertEqual(q['sample'],c.sample(150));self.assertLess(abs(q['slope']-finite),1e-5*max(1,abs(finite)))
        saved=c.tail(150);self.assertEqual(saved['algorithm'],V.COHERENT_TAIL_ALGORITHM);r=V.restore(saved)
        for x in [150,160,180,200,220]:self.assertEqual(r.sample(x),c.sample(x));self.assertEqual(r.jet(x),c.jet(x))
        self.assertEqual(r.fork(at=160,operation='tail/next',handoffWidth=10).snapshot()['algorithm'],V.COHERENT_TAIL_ALGORITHM)
        enclosure=c.enclose_deviation(149,151);self.assertLessEqual(enclosure[0],c.sample(150)['deviation']);self.assertLessEqual(c.sample(150)['deviation'],enclosure[1])

    def test_enclosure_matches_constrained_timeline(self):
        for floor in ['none','zero','baseline','backbone']:
            c=V.create_coherent(seed='coherent/interval',floor=floor,ceilingMargin=None if floor=='none' else .4).fork(at=120,operation='overlap/a',handoffWidth=80).fork(at=140,operation='overlap/b',handoffWidth=40)
            for a,b in [(99,101),(119,121),(149.9,150.1),(180,200),(220,220)]:
                q=c.enclose_deviation(a,b)
                for i in range(13):
                    d=c.sample(a+(b-a)*i/12)['deviation'];self.assertLessEqual(q[0],d);self.assertLessEqual(d,q[1])
