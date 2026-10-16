import math,unittest
from vixen import create_coherent,couple,polynomial_guide
class FiniteNormalizedFacades(unittest.TestCase):
    def test_operations_guides_and_shared_projections(self):
        root=create_coherent()
        curves=[root.impulse(at=120,magnitude=.1,relaxation=20),root.influence(polynomial_guide([.1]),at=120,reference='baseline'),couple([root,create_coherent(seed=2)],.4,[120,200]).curves[0]]
        for curve in curves:
            for x in [1,130,180,220]:
                raw=curve.sample(x);normal=curve.sample_normalized(x)
                for key,value in raw.items():self.assertEqual(normal[key],value)
                self.assertEqual(normal['logBase'],math.log(raw['base']))
                self.assertEqual(curve.at_normalized(x,[0]*16)['value'],curve.at(x,[0]*16)['value'])
            self.assertEqual(tuple(curve.normalized_domain),tuple(curve.domain))
