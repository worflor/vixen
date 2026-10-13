import json
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from vixen import ALGORITHM, Vixen, restore

class PythonSurface(unittest.TestCase):
    def test_json_replay_uses_plain_configuration(self):
        curve = Vixen(seed='python-json', stabilizer=.73123)
        snapshot = {'algorithm': ALGORITHM, 'config': dict(curve.config)}
        replay = restore(json.loads(json.dumps(snapshot)))
        for x in [1, 100, 144.25, 300, 50000]:
            self.assertEqual(curve.sample(x), replay.sample(x))

    def test_views_are_read_only_and_bounds_are_a_mapping(self):
        curve = Vixen()
        with self.assertRaises(TypeError):
            curve.config['strength'] = 2
        with self.assertRaises(TypeError):
            curve.bounds['activity'] = (-1, 2)
        self.assertEqual(curve.bounds['activity'], (0, 1))

    def test_old_snapshot_and_domain_validation(self):
        curve = Vixen(seed='old')
        # Construct the actual v1 schema, not a newer configuration retagged as v1.
        config = {k:v for k,v in curve.config.items() if k not in ('stabilizer','floor','ceilingMargin','boundarySoftness')}
        replay = restore({'algorithm': 'vixen-hyperfield-c2/1', 'config': config})
        self.assertEqual(curve.sample(233), replay.sample(233))
        for config in [{'stabilizer': -1}, {'seed': True}, {'strength': float('nan')}]:
            with self.assertRaises((TypeError, ValueError)):
                Vixen(**config)

    def test_constraint_band(self):
        c=Vixen(floor='backbone',ceilingMargin=.15,boundarySoftness=.6)
        for x in range(1,601):
            p=c.sample(x)
            self.assertGreaterEqual(p['value'],p['floorValue'])
            self.assertLessEqual(p['value'],p['ceilingValue'])
        snapshot={'algorithm':ALGORITHM,'config':dict(c.config)}
        self.assertEqual(restore(json.loads(json.dumps(snapshot))).sample(144),c.sample(144))

if __name__ == '__main__':
    unittest.main()
