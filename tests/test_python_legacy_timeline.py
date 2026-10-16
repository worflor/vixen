import hashlib
import importlib.util
import json
import math
import random
import struct
import sys
import unittest
from pathlib import Path
from vixen import restore

REFERENCE = Path(__file__).parent / 'reference/v1_4_vixen.py'
REFERENCE_SHA256 = '9be0c2e1be387b0157b57352356ce31c25cca0eb650eb4566eb2e38be2337e67'


def legacy_runtime():
    if hashlib.sha256(REFERENCE.read_bytes()).hexdigest() != REFERENCE_SHA256:
        raise AssertionError('The pinned legacy evaluator changed')
    spec = importlib.util.spec_from_file_location('_vixen_v1_4_reference', REFERENCE)
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class LegacyTimelineTests(unittest.TestCase):
    def fixture(self):
        return json.loads((Path(__file__).parent / 'fixtures/v1.4.0/legacy-timeline-constraints.json').read_text())

    def assert_same_runtime(self, actual, expected):
        self.assertEqual(actual.keys(), expected.keys())
        for key, value in expected.items():
            if isinstance(value, (int, float)) and not isinstance(value, bool):
                self.assertEqual(struct.pack('!d', actual[key]), struct.pack('!d', value), key)
            else:
                self.assertEqual(actual[key], value, key)

    def test_frozen_constrained_timelines_and_active_tails(self):
        fixture = self.fixture()
        self.assertEqual(len(fixture['cases']), 6)
        for row in fixture['cases']:
            data = row['python']
            curve, tail = restore(data['snapshot']), restore(data['tail'])
            self.assertEqual(curve.snapshot(), data['snapshot'])
            for x, expected in zip(fixture['x'], data['samples']):
                actual = curve.sample(x)
                self.assertEqual(actual.keys(), expected.keys())
                for key, value in expected.items():
                    if isinstance(value, (int, float)) and not isinstance(value, bool):
                        self.assertTrue(math.isclose(actual[key], value, rel_tol=8e-14, abs_tol=1e-14), key)
                    else:
                        self.assertEqual(actual[key], value, key)
                if x >= 150:
                    self.assert_same_runtime(tail.sample(x), actual)

    def test_same_runtime_comparison_rejects_one_ulp_change(self):
        expected = restore(self.fixture()['cases'][0]['python']['snapshot']).sample(160)
        changed = dict(expected, value=math.nextafter(expected['value'], math.inf))
        with self.assertRaises(AssertionError):
            self.assert_same_runtime(changed, expected)

    def test_original_evaluator_is_bit_exact_on_this_runtime(self):
        fixture = self.fixture()
        original = legacy_runtime()
        rng = random.Random(140)
        for row in fixture['cases']:
            data = row['python']
            curve, tail = restore(data['snapshot']), restore(data['tail'])
            baseline = original.restore(data['snapshot'])
            positions = fixture['x'] + [rng.uniform(1, 400) for _ in range(256)]
            for x in positions:
                expected = baseline.sample(x)
                self.assert_same_runtime(curve.sample(x), expected)
                if x >= 150:
                    self.assert_same_runtime(tail.sample(x), expected)
