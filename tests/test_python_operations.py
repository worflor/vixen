import json
import unittest

from vixen import Vixen, restore
from vixen_operations import (OPERATION_ALGORITHM, couple, perturbation_jet,
                              restore_operation, with_impulse, with_redistribution)


def restore_any(snapshot):
    if isinstance(snapshot, dict) and snapshot.get('algorithm') == OPERATION_ALGORITHM:
        return restore_operation(snapshot, restore_any)
    return restore(snapshot)


class OperationTests(unittest.TestCase):
    def assertClose(self, a, b, tolerance=1e-10):
        self.assertLessEqual(abs(a - b), tolerance * max(1, abs(a), abs(b)))

    def test_impulse_signed_causal_and_c2(self):
        parent = Vixen(seed='operation-test')
        at = 120.0
        positive = with_impulse(parent, {'at': at, 'magnitude': .2, 'attack': 2, 'relaxation': 8})
        negative = with_impulse(parent, {'at': at, 'magnitude': -.2, 'attack': 2, 'relaxation': 8})
        self.assertEqual(positive.sample(at), parent.sample(at))
        self.assertEqual(positive.sample(131), parent.sample(131))
        self.assertGreater(positive.sample(at + 1)['unconstrainedDeviation'], parent.sample(at + 1)['unconstrainedDeviation'])
        self.assertLess(negative.sample(at + 1)['unconstrainedDeviation'], parent.sample(at + 1)['unconstrainedDeviation'])
        for x in (at, at + 2, at + 10):
            jet = perturbation_jet({'type': 'impulse', 'at': at, 'magnitude': -.2, 'attack': 2, 'relaxation': 8}, x)
            self.assertEqual(jet['first'], 0)
            self.assertEqual(jet['second'], 0)

    def test_redistribution_endpoint_jets_certificate_and_policy(self):
        parent = Vixen(seed='redistribution')
        curve = with_redistribution(parent, [120, 140], [1, -.5, .25])
        self.assertEqual(curve.sample(120), parent.sample(120))
        self.assertEqual(curve.sample(140), parent.sample(140))
        self.assertEqual(curve.snapshot()['recipe']['certificate']['integral'], 'Q(1)-Q(0)=0')
        for x in (120, 140):
            self.assertEqual(perturbation_jet({'type': 'redistribution', 'from': 120, 'until': 140, 'coefficients': [1, -.5, .25], 'magnitude': 1, 'measure': 'actualDeviation'}, x), {'value': 0, 'first': 0, 'second': 0})
        with self.assertRaisesRegex(ValueError, 'unconstrained source'):
            with_redistribution(Vixen(floor='zero'), [120, 140], [1])
        with_redistribution(Vixen(floor='zero'), [120, 140], {'coefficients': [1], 'measure': 'unconstrainedDeviation'})

    def test_coupling_pointwise_deviation_sum(self):
        curves = [Vixen(seed=s) for s in ('a', 'b', 'c')]
        group = couple(curves, .8, [120, 160])
        for x in (120, 125, 140, 159, 160):
            before = sum(curve.sample(x)['deviation'] for curve in curves)
            after = sum(sample['deviation'] for sample in group.sample(x))
            self.assertClose(after, before, 1e-9)
        self.assertEqual(couple(curves[:2], 0, [120, 160]).sample(140), (curves[0].sample(140), curves[1].sample(140)))
        with self.assertRaisesRegex(ValueError, 'nonlinear'):
            couple([curves[0], Vixen(ceilingMargin=1)], .5, [120, 160])
        with self.assertRaisesRegex(ValueError, 'ambiguous'):
            group.fork(at=170, operation='x')

    def test_adapters_replay_tail_and_invalid_controls(self):
        base = Vixen(seed=17)
        op = with_impulse(base, {'at': 120, 'magnitude': .1, 'relaxation': 8}).redistribute([130, 150], {'coefficients': [1, 2], 'magnitude': .3}).fork(at=170, operation='branch')
        sample = op.sample(135)
        self.assertClose(sample['multiplier'], 1 + sample['deviation'])
        self.assertClose(sample['activity'], .5 + .5 * __import__('math').tanh(sample['deviation']))
        self.assertClose(sample['positiveValue'], sample['base'] * sample['positiveMultiplier'])
        self.assertEqual(op.sample_range(130, 132, 3)[0], op.sample(130))
        replay = restore_any(json.loads(json.dumps(op.snapshot())))
        self.assertEqual(replay.sample(135), op.sample(135))
        tail_replay = restore_any(json.loads(json.dumps(op.tail(125))))
        self.assertEqual(tail_replay.sample(135), op.sample(135))
        with self.assertRaises(ValueError):
            with_impulse(base, {'at': 120, 'magnitude': 1, 'relaxation': 0})
        with self.assertRaises(ValueError):
            with_redistribution(base, [120, 120], [1])
        with self.assertRaises((TypeError, ValueError)):
            with_redistribution(base, [120, 140], [True])
        with self.assertRaises(TypeError):
            op.snapshot()['curve'] = None
        cyclic = dict(op.snapshot())
        cyclic['curve'] = cyclic
        with self.assertRaisesRegex(ValueError, 'cycles'):
            restore_operation(cyclic, restore_any)

    def test_coupling_group_and_view_replay(self):
        group = couple([Vixen(seed=1), Vixen(seed=2)], .5, [120, 150])
        replay = restore_any(json.loads(json.dumps(group.snapshot())))
        self.assertEqual(replay.sample(135), group.sample(135))
        view = restore_any(json.loads(json.dumps(group.curves[0].snapshot())))
        self.assertEqual(view.sample(135), group.curves[0].sample(135))


if __name__ == '__main__':
    unittest.main()
