import unittest,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
class InfluenceTests(unittest.TestCase):
 def test_capture(self):
  import vixen as V,json,math
  calls=[]
  def source(x):calls.append(x);return 100+20*math.sin(x/17)+x*x/100
  g=V.capture_guide(source,domain=[100,500],count=33);self.assertEqual(len(calls),33);f=V.Vixen().influence(g,at=100,amount=.8)
  self.assertEqual(V.restore(json.loads(json.dumps(f.snapshot()))).sample(222.25),f.sample(222.25));self.assertEqual(len(calls),33)
 def test_shared_jets(self):
  import vixen as V
  g=V.sampled_guide([{'x':x,'y':x*x,'d1':2*x,'d2':2} for x in [100,200,300]])
  for x in [100,120,199.9,200,240,300]:self.assertAlmostEqual(V.evaluate_guide(g,x),x*x,places=8)
 def test_taylor(self):
  import vixen as V
  g=V.taylor_guide([1,1,1,1],center=200)
  for x in [200,201,210]:
   z=x-200;self.assertAlmostEqual(V.evaluate_guide(g,x),1+z+z*z/2+z*z*z/6)
 def test_line_history_replay(self):
  import vixen as V
  root=V.Vixen(seed='guided');f=root.influence(V.polynomial_guide([3,2]),at=160,amount=1,handoffWidth=20)
  for x in [1,42,100,159,160]:self.assertEqual(f.sample(x),root.sample(x))
  for x in [180,200,300]:self.assertEqual(f.sample(x)['value'],2*x+3)
  import json
  self.assertEqual(V.restore(json.loads(json.dumps(f.snapshot()))).sample(200),f.sample(200))
if __name__=='__main__':unittest.main()
