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
        config = dict(curve.config)
        del config['stabilizer']
        replay = restore({'algorithm': 'vixen-hyperfield-c2/1', 'config': config})
        self.assertEqual(curve.sample(233), replay.sample(233))
        for config in [{'stabilizer': -1}, {'seed': True}, {'strength': float('nan')}]:
            with self.assertRaises((TypeError, ValueError)):
                Vixen(**config)

if __name__ == '__main__':
    unittest.main()
