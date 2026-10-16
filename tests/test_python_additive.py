import json,unittest
from vixen import Vixen,polynomial_guide,restore

class AdditiveTests(unittest.TestCase):
    def test_additive_deviation_release_and_json_replay(self):
        parent=Vixen(seed='additive')
        curve=parent.influence(polynomial_guide([.2]),at=120,amount=.6,handoffWidth=10,until=170,releaseWidth=10,mode='add')
        for x,w in [(120,0),(125,.3),(130,.6),(150,.6),(175,.3),(180,0),(300,0)]:
            a,b=parent.sample(x),curve.sample(x)
            if w==0:self.assertEqual(a,b)
            else:self.assertAlmostEqual(b['unconstrainedValue'],a['unconstrainedValue']+a['base']*(w*.2),delta=1e-12*max(1,abs(b['unconstrainedValue'])))
        self.assertEqual(curve.snapshot()['mode'],'add')
        self.assertEqual(curve.snapshot()['reference'],'baseline')
        replay=restore(json.loads(json.dumps(curve.snapshot())))
        for x in [120,125,150,175,180,300]:self.assertEqual(replay.sample(x),curve.sample(x))

    def test_overlaps_forks_tail_and_zero_identity(self):
        parent=Vixen(seed='overlap')
        a=parent.influence(polynomial_guide([.2]),at=120,amount=.6,handoffWidth=10,until=170,releaseWidth=10,mode='add')
        b=a.influence(polynomial_guide([-.3]),at=140,amount=.4,handoffWidth=10,until=190,releaseWidth=10,mode='add')
        for x in [150,160]:self.assertAlmostEqual(b.sample(x)['unconstrainedValue'],parent.sample(x)['unconstrainedValue'],delta=1e-10)
        changed=a.fork(at=150,operation='new-course',handoffWidth=4)
        native=parent.fork(at=150,operation='new-course',handoffWidth=4)
        for x in [149,150,152,156,175,180,300]:
            s=native.sample(x);expected=s['unconstrainedValue']+s['base']*(a.influence_at(x)['weight']*.2)
            self.assertAlmostEqual(changed.sample(x)['unconstrainedValue'],expected,delta=1e-12*max(1,abs(expected)))
        tail=restore(a.tail(150))
        for x in [150,175,180,300]:self.assertEqual(a.sample(x),tail.sample(x))
        for floor in ['none','zero','baseline']:
            p=Vixen(floor=floor);identity=p.influence(polynomial_guide([0]),at=120,mode='add')
            for x in [1,120,125,140,500]:self.assertEqual(identity.sample(x),p.sample(x))

    def test_units_diagnostics_and_strict_version(self):
        p=Vixen();g=polynomial_guide([.2]);options=dict(at=120,handoffWidth=10,amount=.5,mode='add')
        a=p.influence(g,**options);b=p.influence(g,**options,reference='backbone')
        self.assertEqual(a.sample(150),b.sample(150))
        self.assertIsNone(a.influence_at(150)['targetValue']);self.assertIsNone(a.influence_at(150)['followingError'])
        raw=p.influence(polynomial_guide([10]),**options,reference='value')
        self.assertAlmostEqual(raw.sample(150)['unconstrainedValue'],p.sample(150)['unconstrainedValue']+5,delta=1e-10)
        s=json.loads(json.dumps(a.snapshot()))
        for bad in [{**s,'mode':'blend'},{**s,'mode':None},{**s,'algorithm':'vixen-influence-c2/2'}]:
            with self.assertRaises((ValueError,TypeError)):restore(bad)
        for mode in [None,{},0,True,'ADD','subtract']:
            with self.assertRaises((ValueError,TypeError)):p.influence(g,at=120,mode=mode)
        self.assertEqual(p.influence(g,at=120,mode='blend').snapshot(),p.influence(g,at=120).snapshot())

    def test_zero_crossing_does_not_skip_the_boundary_transform(self):
        parent=Vixen(seed=2,floor='baseline',boundarySoftness=1)
        c=parent.influence(polynomial_guide([-150,1]),at=120,handoffWidth=10,amount=.5,mode='add')
        s=c.sample(150)
        for x in [150-1e-8,150+1e-8]:self.assertLess(abs(c.sample(x)['value']-s['value'])/s['base'],1e-6)
