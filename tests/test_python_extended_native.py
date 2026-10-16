import json
import unittest
from vixen import create_coherent
from vixen_extended import create_extended, restore_extended, MAX_DOMAIN_END

class ExtendedNativeTests(unittest.TestCase):
    def assert_primal(self, actual, expected):
        for key, value in expected.items():
            self.assertEqual(actual[key], value, key)
    def test_finite_samples_match_coherent5_and_explicit_moving_coordinates(self):
        legacy=create_coherent(seed='native-test')
        extended=create_extended(seed='native-test')
        self.assert_primal(extended.sample(250),legacy.sample(250))
        h=tuple((i-7)/20 for i in range(16))
        self.assert_primal(extended.at(250,h),legacy.at(250,h))
        self.assertEqual(extended.normalized_domain,(1,MAX_DOMAIN_END))
    def test_far_scaled_sample_and_raw_range_error(self):
        c=create_extended();p=c.sample_normalized(1e6)
        self.assertIsNone(p['base']);self.assertIsNone(p['value'])
        self.assertIsNotNone(p['scaledBase']);self.assertIsNotNone(p['scaledValue'])
        self.assertTrue(p['signedLogValue']['logAbs'].__class__ is float)
        with self.assertRaisesRegex(ValueError,'Raw base is unavailable'):
            c.sample(1e6)
        self.assertEqual(c.at_normalized(1e6,(0,)*16)['x'],1e6)
    def test_far_forks_replay_active_and_settled_checkpoints(self):
        root=create_extended(seed='fork-test')
        a=root.fork(at=1e6,operation='a',handoffWidth=100)
        b=a.fork(at=1e6+20,operation='b',handoffWidth=100)
        old=a.sample_normalized(1e6+30);self.assertEqual(a.sample_normalized(1e6+30),old)
        replay=restore_extended(json.loads(json.dumps(b.snapshot())))
        now=b.sample_normalized(1e6+50);self.assertEqual(replay.sample_normalized(1e6+50),now)
        active=restore_extended(json.loads(json.dumps(b.tail(1e6+10))))
        self.assertEqual(active.sample_normalized(1e6+50),now)
        expected=b.fork(at=1e6+150,operation='future').sample_normalized(1e6+160)
        actual=active.fork(at=1e6+150,operation='future').sample_normalized(1e6+160)
        self.assertEqual(actual,expected)
        settled=restore_extended(json.loads(json.dumps(b.tail(1e6+200))))
        self.assertEqual(settled.sample_normalized(1e6+210),b.sample_normalized(1e6+210))
        expected=b.fork(at=1e6+210,operation='settled-future').sample_normalized(1e6+220)
        actual=settled.fork(at=1e6+210,operation='settled-future').sample_normalized(1e6+220)
        self.assertEqual(actual,expected)
    def test_deep_fork_history_is_iterative(self):
        c=create_extended()
        for i in range(5000):c=c.fork(at=1e6,operation=f'deep-{i}',handoffWidth=16)
        self.assertEqual(len(c.lineage),5000)
        self.assertEqual(c.sample_normalized(1e6+1)['x'],1e6+1)
        replay=restore_extended(json.loads(json.dumps(c.snapshot())))
        self.assertEqual(len(replay.lineage),5000)

if __name__=='__main__':
    unittest.main()
