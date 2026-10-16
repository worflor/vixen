import json,unittest
from pathlib import Path
from vixen import restore

class ComposedPortabilityTests(unittest.TestCase):
    def test_frozen_mixed_shared_and_unary_fork_graphs(self):
        data=json.loads((Path(__file__).parent/'fixtures/v5/composed-graphs.json').read_text())
        for case in data['cases']:
            curves=[(restore(case['snapshot']),1)]+[(restore(t['snapshot']),t['from']) for t in case['tails']]
            for curve,lower in curves:
                for x,expected in zip(case['coordinates'],case['samples']):
                    if x<lower:continue
                    actual=curve.sample(x);self.assertEqual(set(actual),set(expected))
                    for key,value in expected.items():
                        if isinstance(value,(int,float)) and not isinstance(value,bool):self.assertLessEqual(abs(actual[key]-value),1e-10*max(1,abs(value)),(case['id'],x,key))
                        else:self.assertEqual(actual[key],value)

if __name__=='__main__':unittest.main()
