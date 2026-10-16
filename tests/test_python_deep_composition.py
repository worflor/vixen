import math,unittest,json
from vixen import Vixen,polynomial_guide,restore,couple

class DeepCompositionTests(unittest.TestCase):
    def test_six_thousand_identity_influences_preserve_all_fields(self):
        root=Vixen();f=root;g=polynomial_guide([.1])
        for _ in range(6000):f=f.influence(g,at=120,amount=0)
        self.assertEqual(f.sample(200),root.sample(200))
        self.assertEqual(len(f.influences),6000)
    def test_active_influences_follow_the_analytic_blend(self):
        root=Vixen();f=root;g=polynomial_guide([.1]);count=1500;amount=.01
        for _ in range(count):f=f.influence(g,at=120,amount=amount,reference='baseline')
        p=root.sample(200);q=f.sample(200);weight=(1-amount)**count
        expected=p['value']*weight+1.1*p['base']*(1-weight)
        self.assertAlmostEqual(q['value']/expected,1,places=12)
        self.assertTrue(math.isfinite(q['value']))

    def test_deep_mixed_json_replay_fork_and_tail(self):
        parent=Vixen(seed='mixed-replay');c=parent;guide=polynomial_guide([.1])
        for _ in range(1200):c=c.influence(guide,at=120,amount=0).impulse(120,0,10)
        snapshot=c.snapshot()
        self.assertEqual(snapshot['algorithm'],'vixen-graph/1')
        replay=restore(json.loads(json.dumps(snapshot)))
        for x in [50,130,200]:self.assertEqual(dict(replay.sample(x)),dict(parent.sample(x)))
        branch=c.fork(at=200,operation='deep',handoffWidth=4)
        expected=parent.fork(at=200,operation='deep',handoffWidth=4)
        for x in [130,201,250]:self.assertEqual(dict(branch.sample(x)),dict(expected.sample(x)))
        tail=restore(json.loads(json.dumps(c.tail(200))))
        self.assertEqual(dict(tail.sample(250)),dict(c.sample(250)))

    def test_deep_shared_coupling_graph(self):
        parent=Vixen(seed='group-a');other=Vixen(seed='group-b');c=parent
        for _ in range(600):c=couple([c,other],.5,[120,200]).curves[0]
        snapshot=c.snapshot()
        self.assertEqual(snapshot['algorithm'],'vixen-graph/1')
        self.assertLess(len(snapshot['nodes']),1803)
        replay=restore(json.loads(json.dumps(snapshot)))
        self.assertEqual(dict(replay.sample(150)),dict(c.sample(150)))
        tail=restore(json.loads(json.dumps(c.tail(250))))
        self.assertEqual(dict(tail.sample(300)),dict(parent.sample(300)))

    def test_generic_fork_chronology_and_effective_domain(self):
        c=Vixen().influence(polynomial_guide([.1]),at=200).impulse(240,.1,10)
        with self.assertRaises(ValueError):c.fork(at=150,operation='back',handoffWidth=4)
        short=Vixen().influence(polynomial_guide([.1],domain=[1,180]),at=120).impulse(140,.1,10)
        f=short.fork(at=175,operation='edge')
        self.assertEqual(f.lineage[-1]['handoffWidth'],5)
        with self.assertRaises(ValueError):short.fork(at=181,operation='outside')
