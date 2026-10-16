import json,math,unittest
from pathlib import Path
from vixen import restore

class CoherentPortabilityTests(unittest.TestCase):
    def test_frozen_javascript_cases_and_exact_tail_witnesses(self):
        data=json.loads((Path(__file__).parent/'fixtures/v5/coherent-timelines.json').read_text());samples=tails=0
        self.assertEqual(len(data['cases']),8)
        for case in data['cases']:
            curve=restore(case['snapshot'])
            for expected in case['samples']:
                actual=curve.sample(expected['x']);self.assertEqual(actual.keys(),expected.keys())
                for key,value in expected.items():
                    if isinstance(value,(int,float)) and not isinstance(value,bool):
                        self.assertTrue(math.isfinite(actual[key]));self.assertLessEqual(abs(actual[key]-value),1e-10*max(1,abs(value)),key)
                    else:self.assertEqual(actual[key],value,key)
                samples+=1
            for tail in case['tails']:
                self.assertEqual(curve.tail(tail['from']),tail)
                self.assertEqual(restore(tail).sample(tail['from']+10),curve.sample(tail['from']+10));tails+=1
        self.assertEqual(samples,88);self.assertEqual(tails,16)
