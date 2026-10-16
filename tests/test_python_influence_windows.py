import json
import unittest

from vixen import Vixen as create, restore
from vixen_guide import polynomial_guide
from vixen_influence import restore_influence


class InfluenceWindowTests(unittest.TestCase):
    def test_window_release_and_exact_parent_delegation(self):
        root = create()
        guide = polynomial_guide([25], domain=(120, 170))
        influenced = root.influence(
            guide,
            at=120,
            amount=1,
            handoffWidth=10,
            until=150,
            releaseWidth=20,
        )
        self.assertEqual(influenced.domain, root.domain)
        for x in (1, 100, 120):
            self.assertEqual(influenced.sample(x), root.sample(x))
        self.assertNotEqual(influenced.sample(160)['value'], root.sample(160)['value'])
        self.assertEqual(influenced.sample(170), root.sample(170))
        self.assertEqual(influenced.sample(50000), root.sample(50000))
        self.assertIsNone(influenced.influence_at(170)['targetValue'])

    def test_deviation_spaces_use_reference_channels(self):
        root = create(seed='issues-3-4')
        guide = polynomial_guide([.2], domain=(120, 500))
        baseline = root.influence(
            guide, at=120, amount=1, handoffWidth=10, reference='baseline'
        )
        backbone = root.influence(
            guide, at=120, amount=1, handoffWidth=10, reference='backbone'
        )
        for x in (140, 200, 400):
            parent = root.sample(x)
            self.assertEqual(baseline.sample(x)['unconstrainedValue'], parent['base'] * 1.2)
            self.assertEqual(backbone.sample(x)['unconstrainedValue'], parent['unconstrainedGuideValue'] + parent['base'] * .2)
        self.assertEqual(baseline.snapshot()['algorithm'], 'vixen-influence-c2/2')
        with self.assertRaises(TypeError):
            baseline.sample(200)['value'] = 0

    def test_same_time_order_and_schema_one_replay(self):
        root = create()
        earlier = root.influence(
            polynomial_guide([5], domain=(120, 500)),
            at=120, amount=1, handoffWidth=10,
        )
        later = earlier.influence(
            polynomial_guide([9], domain=(120, 500)),
            at=120, amount=1, handoffWidth=10,
        )
        self.assertEqual(later.sample(150)['value'], 9)
        snapshot = earlier.snapshot()
        self.assertEqual(snapshot['algorithm'], 'vixen-influence-c2/1')
        replay = restore(json.loads(json.dumps(snapshot)))
        self.assertEqual(replay.sample(150), earlier.sample(150))

    def test_json_replay_checkpoint_carry_and_lower_domain_checks(self):
        root = create()
        influenced = root.influence(
            polynomial_guide([25], domain=(120, 170)),
            at=120, amount=1, handoffWidth=10, until=150, releaseWidth=20,
        )
        replay = restore_influence(json.loads(json.dumps(influenced.snapshot())), restore)
        for x in (120, 130, 150, 160, 169, 170, 1000):
            self.assertEqual(replay.sample(x), influenced.sample(x))

        checkpoint_data = json.loads(json.dumps(influenced.tail(160)))
        checkpoint = restore_influence(checkpoint_data, restore)
        self.assertEqual(checkpoint.domain, (160, root.domain[1]))
        for x in (160, 165, 169, 170, 1000):
            self.assertEqual(checkpoint.sample(x), influenced.sample(x))
        for method, args in (
            (checkpoint.sample, (159,)),
            (checkpoint.at, (159, root.config['latent'])),
            (checkpoint.location, (159,)),
            (checkpoint.inspect, (159,)),
        ):
            with self.assertRaises((ValueError, TypeError)):
                method(*args)

    def test_tail_keeps_future_influence_and_drops_completed_recipe(self):
        root = create()
        influenced = root.influence(
            polynomial_guide([25], domain=(120, 170)),
            at=120, amount=1, handoffWidth=10, until=150, releaseWidth=20,
        )
        before_activation = restore_influence(json.loads(json.dumps(influenced.tail(110))), restore)
        self.assertEqual(before_activation.sample(110), root.sample(110))
        self.assertEqual(before_activation.sample(160), influenced.sample(160))
        after_release = influenced.tail(170)
        self.assertEqual(after_release['algorithm'], 'vixen-tail-c2/2')

    def test_invalid_controls_and_bad_snapshots_fail_closed(self):
        root = create()
        guide = polynomial_guide([3], domain=(120, 300))
        invalid = (
            {'at': None},
            {'amount': None},
            {'handoffWidth': None},
            {'until': None},
            {'releaseWidth': None},
            {'reference': None},
            {'until': 150},
            {'releaseWidth': 40, 'until': 290},
            {'reference': 'other'},
        )
        for options in invalid:
            with self.subTest(options=options):
                with self.assertRaises((TypeError, ValueError)):
                    root.influence(guide, at=120, **options)
        influenced = root.influence(guide, at=120, amount=.7, handoffWidth=10)
        snapshot = json.loads(json.dumps(influenced.snapshot()))
        with self.assertRaises((TypeError, ValueError)):
            restore_influence({**snapshot, 'amount': None}, restore)
        with self.assertRaises(TypeError):
            restore_influence({**snapshot, 'extra': True}, restore)
        cycle = dict(snapshot)
        cycle['curve'] = cycle
        with self.assertRaises(ValueError):
            restore_influence(cycle, restore)

    def test_restore_has_no_fixed_composition_depth_limit(self):
        root = create()
        guide = polynomial_guide([5], domain=(120, 500))
        composed = root
        for _ in range(40):
            composed = composed.influence(guide, at=120, amount=.1, handoffWidth=10)
        replay = restore_influence(json.loads(json.dumps(composed.snapshot())), restore)
        self.assertEqual(replay.sample(150), composed.sample(150))


if __name__ == '__main__':
    unittest.main()
