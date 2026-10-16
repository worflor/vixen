import json,unittest
from vixen import Vixen,restore,polynomial_guide

class CompositionReplayTests(unittest.TestCase):
    def test_native_immutable_container_snapshots_restore_without_a_json_detour(self):
        f=Vixen(seed='isolated').fork(at=120,operation='a').influence(polynomial_guide([.2],domain=(1,300)),at=140,reference='baseline',until=180,releaseWidth=20).impulse(at=160,magnitude=.1,relaxation=30)
        for snapshot in (f.snapshot(),json.loads(json.dumps(f.snapshot()))):
            self.assertEqual(restore(snapshot).sample(170),f.sample(170))
        self.assertEqual(restore(f.tail(170)).sample(175),f.sample(175))
