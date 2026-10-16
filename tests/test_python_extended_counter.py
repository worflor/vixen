import unittest
from vixen import create_extended,restore

class ExtendedCounterContract(unittest.TestCase):
    def test_folded_counter_does_not_emit_an_unrestorable_checkpoint(self):
        cp=dict(create_extended().tail(120))
        cp.update(seed=1,leafSeed=1,foldedCount=2**53-1)
        curve=restore(cp).fork(at=130,operation='counter',handoffWidth=1)
        with self.assertRaisesRegex(ValueError,'count|safe|represent'):
            curve.tail(140)
