import json,unittest
from pathlib import Path
from vixen import restore

class LegacyTimelineTests(unittest.TestCase):
    def test_frozen_constrained_timelines_and_active_tails(self):
        fixture=json.loads((Path(__file__).parent/'fixtures/v1.4.0/legacy-timeline-constraints.json').read_text())
        self.assertEqual(len(fixture['cases']),6)
        for row in fixture['cases']:
            data=row['python'];c=restore(data['snapshot']);tail=restore(data['tail'])
            self.assertEqual(c.snapshot(),data['snapshot'])
            for x,expected in zip(fixture['x'],data['samples']):
                self.assertEqual(c.sample(x),expected)
                if x>=150:self.assertEqual(tail.sample(x),expected)
