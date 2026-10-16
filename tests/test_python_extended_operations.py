import unittest
from vixen_extended import create_extended
from vixen_extended_operations import (extended_polynomial_guide, with_extended_influence,
    with_extended_impulse, with_extended_redistribution)

class ExtendedOperationsTests(unittest.TestCase):
    def setUp(self):
        self.domain=(1,2**40)
        self.guide=extended_polynomial_guide([1],{'domain':self.domain})
        self.root=create_extended(seed='ops-test',floor='none')

    def test_absolute_target_and_floor(self):
        full=with_extended_influence(self.root,self.guide,{'at':1,'amount':1,'handoffWidth':1,'reference':'value'})
        with self.assertRaises(ValueError):full.sample(1e9)
        self.assertEqual(full.sample_normalized(1e9)['value'],1)
        floor=with_extended_influence(create_extended(seed='floor-test',floor='baseline'),self.guide,
            {'at':1,'amount':1,'handoffWidth':1,'reference':'value'})
        p=floor.sample_normalized(1e9)
        self.assertEqual(p['scaledValue'],p['scaledBase'])

    def test_window_and_far_operations(self):
        windowed=with_extended_influence(self.root,self.guide,{'at':2,'amount':.7,'handoffWidth':1,'until':8,'releaseWidth':2})
        self.assertEqual(windowed.sample_normalized(10),self.root.sample_normalized(10))
        impulse=with_extended_impulse(self.root,{'at':1e9,'magnitude':.2,'attack':2,'relaxation':8})
        redist=with_extended_redistribution(self.root,{'from':1e9+20,'until':1e9+40,'coefficients':[1],'measure':'unconstrainedDeviation'})
        self.assertEqual(impulse.snapshot()['operation'],'impulse')
        self.assertEqual(redist.snapshot()['operation'],'redistribution')
        self.assertEqual(impulse.sample_normalized(1e9+30)['x'],1e9+30)
        self.assertEqual(redist.sample_normalized(1e9+30)['x'],1e9+30)
        self.assertEqual(impulse.fork(at=1e9+12,operation='far-fork').sample_normalized(1e9+15)['x'],1e9+15)
        self.assertTrue(redist.tail(1e9+50))

    def test_deep_unary_graph(self):
        deep=self.root
        for _ in range(6000):
            deep=with_extended_impulse(deep,{'at':1,'magnitude':0,'attack':1,'relaxation':1})
        self.assertEqual(deep.sample_normalized(100)['x'],100)
        graph=deep.snapshot()
        self.assertEqual(graph['algorithm'],'vixen-graph/1')
        self.assertEqual(len(graph['nodes']),6001)

if __name__=='__main__':
    unittest.main()
