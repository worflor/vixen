import math
import unittest

from vixen_analysis import (calibrate_occupancy, create_correlated_channels,
                            make_lipschitz_enclosure, measure_channel_correlation,
                            next_crossing, restore_occupancy,
                            validate_correlation_matrix, validate_occupancy)


class Polynomial:
    def __init__(self, coefficients):
        self.c, self.b, self.a = coefficients
        self.domain = (0.0, 1.0)

    def value(self, x):
        return self.c + self.b * x + self.a * x * x

    def sample(self, x):
        return {'deviation': self.value(x)}

    def enclose_deviation(self, lo, hi):
        values = [self.value(lo), self.value(hi)]
        if self.a:
            vertex = -self.b / (2 * self.a)
            if lo < vertex < hi:
                values.append(self.value(vertex))
        return min(values) - 1e-13, max(values) + 1e-13


class AnalysisTests(unittest.TestCase):
    def test_sign_change_and_first_crossing(self):
        result = next_crossing(Polynomial((-0.25, 1, 0)), 0, 0, 1,
                              {'continuous': True, 'tolerance': 1e-5})
        self.assertEqual(result['status'], 'found')
        self.assertLessEqual(result['interval'][1] - result['interval'][0], 1e-5)
        first = next_crossing(Polynomial((0.1875, -1, 1)), 0, 0, 1,
                             {'continuous': True, 'tolerance': 1e-5})
        self.assertEqual(first['status'], 'found')
        self.assertLess(first['interval'][0], 0.5)

    def test_narrow_bump_tangency_absence_and_no_oracle(self):
        bump = next_crossing(Polynomial((-0.039999, 0.4, -1)), 0, 0, 1,
                             {'continuous': True, 'tolerance': 1e-5})
        self.assertEqual(bump['status'], 'found')
        tangent = next_crossing(Polynomial((0.25, -1, 1)), 0, 0, 1,
                                {'continuous': True, 'tolerance': 1e-4})
        self.assertEqual(tangent['status'], 'unresolved')
        absent = next_crossing(Polynomial((0.35, -1, 1)), 0, 0, 1)
        self.assertEqual(absent['status'], 'absent')
        missing = next_crossing(lambda x: x, 0.5, 0, 1, {'domain': (0, 1)})
        self.assertEqual(missing['reason'], 'enclosure-unavailable')

    def test_budgets_oracle_validation_and_lipschitz_hook(self):
        limited = next_crossing(Polynomial((-0.25, 1, 0)), 0, 0, 1,
                                {'continuous': True, 'maxIntervals': 1})
        self.assertEqual(limited['status'], 'unresolved')
        self.assertTrue(limited['unresolved'])
        with self.assertRaises(ValueError):
            next_crossing(type('Bad', (), {'domain': (0, 1), 'sample': lambda self, x: x,
                                           'enclose': lambda self, lo, hi: (1, -1)})(), 0, 0, 1)
        lo,hi=make_lipschitz_enclosure(lambda x:x,1)(0,1)
        self.assertLessEqual(lo,0);self.assertGreaterEqual(hi,1)

    def test_occupancy_ties_replay_and_holdout(self):
        calibration = calibrate_occupancy(list(range(1, 11)),
                                          {'targetFraction': 0.2, 'outputThreshold': 0.2})
        self.assertEqual(calibration.achieved_fraction, 0.2)
        self.assertGreaterEqual(calibration.evaluate(9), 0.2)
        self.assertLess(calibration.evaluate(8), 0.2)
        points = [-2, -1, 0, 1, 2]
        self.assertTrue(all(calibration.evaluate(a) < calibration.evaluate(b)
                            for a, b in zip(points, points[1:])))
        restored = restore_occupancy(calibration.serialize())
        self.assertEqual(restored.evaluate(4), calibration.evaluate(4))
        tied = calibrate_occupancy([1, 2, 2, 2, 3],
                                   {'targetFraction': 0.4, 'outputThreshold': 0.5})
        self.assertIn(tied.achieved_fraction, (0.2, 0.8))
        report = validate_occupancy(calibration, [0, 10, 11])
        self.assertEqual(report['totalPopulation'], 3)
        self.assertAlmostEqual(report['achievedFraction'], 2 / 3)

    def test_occupancy_reports_actual_mapping_with_near_ties(self):
        population=[0,1e-20,1]
        c=calibrate_occupancy(population,{'targetFraction':2/3,'outputThreshold':0.8})
        actual=sum(c.evaluate(x)>=0.8 for x in population)/len(population)
        self.assertEqual(c.achieved_fraction,actual)
        self.assertEqual(validate_occupancy(c,population)['achievedFraction'],actual)

    def test_occupancy_endpoints_and_invalid_numeric_types(self):
        self.assertEqual(calibrate_occupancy([1, 2], {'targetFraction': 1,
                                                       'outputThreshold': 0.5}).achieved_fraction, 1)
        self.assertEqual(calibrate_occupancy([1, 2], {'targetFraction': 0,
                                                       'outputThreshold': 0.5}).achieved_fraction, 0)
        with self.assertRaises(TypeError):
            calibrate_occupancy([True], {'targetFraction': 0.5, 'outputThreshold': 0.5})
        with self.assertRaises(ValueError):
            calibrate_occupancy([float('1.7976931348623157e308')],
                                {'targetFraction': 0, 'outputThreshold': 0.5})

    @staticmethod
    def source_factory(seed):
        phase = (seed % 997) / 997

        class Source:
            domain = (0.0, 1.0)

            def sample(self, x):
                value = math.sin(2 * math.pi * (x + phase))
                return {'score': value, 'unconstrainedDeviation': value}

            def at(self, x, coordinates):
                return self.sample(x)
        return Source()

    def test_correlation_psd_negative_replay_and_three_channels(self):
        checked = validate_correlation_matrix([[1, -1], [-1, 1]])
        self.assertEqual(len(checked['factor']), 2)
        with self.assertRaises(ValueError):
            validate_correlation_matrix([[1, 1.2], [1.2, 1]])
        group = create_correlated_channels([[1, -1], [-1, 1]], self.source_factory, 'root-seed')
        replay = create_correlated_channels(group.serialize()['matrix'], self.source_factory, 'root-seed')
        self.assertEqual(group.source_seeds, replay.source_seeds)
        values = group.sample(0.31)
        self.assertAlmostEqual(values[0], -values[1])
        measured = measure_channel_correlation(group, [i / 30 for i in range(30)])
        self.assertLess(measured[0][1], -0.999999)
        triple = create_correlated_channels([[1, 0, 0], [0, 1, 0], [0, 0, 1]],
                                            self.source_factory, 42)
        self.assertEqual(len(triple.at(0.2, [0] * 16)), 3)
        with self.assertRaises(ValueError):
            measure_channel_correlation(lambda x: (1, 1), [0, 1])


if __name__ == '__main__':
    unittest.main()
