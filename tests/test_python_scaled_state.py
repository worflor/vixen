import math
import unittest

from vixen_scaled_state import apply_wide_policy, ceil_wide, floor_wide, make_scaled_sample
from vixen_wide import Wide


def wide(n, e=0):
    return Wide(int(n), int(e))


ZERO = Wide.from_number(0)
ONE = Wide.from_number(1)
KEYS = [
    'x', 'logBase', 'score', 'guideScore', 'gate', 'rawDeviation',
    'unconstrainedDeviation', 'unconstrainedGuideDeviation', 'deviation', 'guideDeviation',
    'multiplier', 'activity', 'positiveMultiplier', 'signedLogValue', 'value', 'base',
    'guideValue', 'unconstrainedValue', 'unconstrainedGuideValue', 'positiveValue',
    'stabilizationWeight', 'floorRatio', 'ceilingRatio', 'floorValue', 'ceilingValue',
    'boundaryWidthRatio', 'boundaryWidth', 'constraintApplied', 'scaledValue', 'scaledBase',
    'scaledGuideValue', 'scaledUnconstrainedValue', 'scaledUnconstrainedGuideValue',
    'scaledPositiveValue', 'scaledFloorValue', 'scaledCeilingValue', 'scaledBoundaryWidth',
    'scaledDeviation', 'scaledGuideDeviation', 'scaledUnconstrainedDeviation',
    'scaledUnconstrainedGuideDeviation'
]


class ScaledStateTests(unittest.TestCase):
    def test_tiny_ratios_and_stable_strip_branches(self):
        tiny = Wide.from_ratio(1, 1, -1000)
        self.assertGreater(floor_wide(tiny, ZERO, ONE).cmp(ZERO), 0)
        self.assertEqual(ceil_wide(tiny, ONE, ONE).cmp(tiny), 0)

    def test_endpoints_collapsed_bands_and_width_validation(self):
        self.assertEqual(floor_wide(ZERO, ZERO, ONE).cmp(ZERO), 0)
        self.assertEqual(floor_wide(ONE, ZERO, ONE).cmp(ONE), 0)
        self.assertEqual(ceil_wide(ZERO, ONE, ONE).cmp(ZERO), 0)
        self.assertEqual(ceil_wide(ONE, ONE, ONE).cmp(ONE), 0)
        self.assertEqual(apply_wide_policy(wide(5), wide(3), wide(3), ONE).cmp(wide(3)), 0)
        self.assertEqual(apply_wide_policy(wide(5), wide(3), wide(2), ONE).cmp(wide(3)), 0)
        with self.assertRaises(ValueError):
            floor_wide(ONE, ZERO, wide(-1))
        with self.assertRaises(ValueError):
            apply_wide_policy(ONE, ZERO, ONE, wide(-1))

    @staticmethod
    def state(base, value, guide=None):
        if guide is None:
            guide = value
        return {
            'base': base,
            'unconstrainedValue': value,
            'unconstrainedGuideValue': guide,
            'floorValue': None,
            'ceilingValue': None,
            'boundaryWidth': ZERO,
            'score': 0,
            'guideScore': 0,
            'gate': 1,
            'rawDeviation': 0,
            'stabilizationWeight': 0.5,
            'x': 120,
        }

    def test_large_baseline_preserves_small_absolute_target_and_key_parity(self):
        target = ONE
        huge_baseline = wide(1, 2000)
        sample = make_scaled_sample(self.state(huge_baseline, target))
        self.assertEqual(list(sample), KEYS)
        self.assertEqual(sample['value'], 1)
        self.assertEqual(Wide.decode(sample['scaledValue']).cmp(target), 0)
        self.assertEqual(sample['deviation'], -1)
        self.assertIsNone(sample['multiplier'])
        self.assertEqual(dict(sample['signedLogValue']), {'sign': 1, 'logAbs': 0})
        self.assertEqual(Wide.decode(sample['scaledBase']).cmp(huge_baseline), 0)

    def test_huge_deviation_saturates_bounded_adapters(self):
        huge_value = wide(1, 2000)
        sample = make_scaled_sample(self.state(ONE, huge_value))
        self.assertEqual(sample['activity'], 1)
        self.assertEqual(sample['positiveMultiplier'], math.exp(0.55))
        self.assertIsNone(sample['value'])
        self.assertEqual(Wide.decode(sample['scaledValue']).cmp(huge_value), 0)

    def test_raw_numeric_fields_are_finite_or_null_and_result_is_immutable(self):
        sample = make_scaled_sample(self.state(wide(1, 2000), ONE))
        for key, value in sample.items():
            if key.startswith('scaled') or key in ('signedLogValue', 'constraintApplied'):
                continue
            self.assertTrue(value is None or isinstance(value, (int, float)) and math.isfinite(value), key)
        with self.assertRaises(TypeError):
            sample['value'] = 9
        with self.assertRaises(TypeError):
            sample['scaledValue']['sign'] = -1


if __name__ == '__main__':
    unittest.main()
