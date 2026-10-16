import json, math, unittest
from pathlib import Path
from vixen_guide import guide_jet

class GuideJetPortability(unittest.TestCase):
    def test_frozen_javascript_polynomial_and_quintic_jets(self):
        fixture=json.loads((Path(__file__).parent/'fixtures/v5/guide-jets.json').read_text())
        for case in fixture['cases']:
            actual=guide_jet(case['guide'],case['x'])
            for key in ('value','d1','d2'):
                expected=case['expected'][key]
                if expected is None:self.assertIsNone(getattr(actual,key))
                else:
                    self.assertTrue(math.isfinite(getattr(actual,key)))
                    self.assertLessEqual(abs(getattr(actual,key)-expected),2e-12*max(1,abs(expected)))

if __name__=='__main__':unittest.main()
