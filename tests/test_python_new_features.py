import unittest,sys,json,math
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import vixen
class TimelineTests(unittest.TestCase):
 def test_clock_and_dips(self):
  f=vixen.Vixen(openingEndpoint=200,introVariation=1,seed='fox')
  self.assertEqual(f.sample(200)['gate'],0)
  self.assertEqual(f.sample(216)['gate'],1)
  for x in range(1,201):self.assertGreater(f.sample(x)['value'],0)
  self.assertEqual(f.sample(200)['base'],vixen.baseline(100))
  for options in [{'openingEndpoint':True},{'introVariation':True},{'boundarySoftness':True}]:
   with self.assertRaises((ValueError,TypeError)):vixen.Vixen(**options)
 def test_fork_and_snapshot(self):
  root=vixen.Vixen(seed='fox',floor='backbone',ceilingMargin=.4,introVariation=1)
  a=root.fork(at=200,operation='door')
  b=a.fork(at=300.125,operation='🦊',handoffWidth=20)
  for x in [1,42,100,200]:self.assertEqual(a.sample(x),root.sample(x))
  for x in [1,200,250,300.125]:self.assertEqual(b.sample(x),a.sample(x))
  replay=vixen.restore(json.loads(json.dumps(b.snapshot())))
  for x in [1,42,200,210,300.125,310,400]:self.assertEqual(b.sample(x),replay.sample(x))
  for x in range(190,400):
   s=b.sample(x);self.assertGreaterEqual(s['value'],s['floorValue']);self.assertLessEqual(s['value'],s['ceilingValue'])
  with self.assertRaises(AttributeError):a.config={}
  with self.assertRaises((ValueError,TypeError)):a.fork(at=199,operation='rewind')
  with self.assertRaises((ValueError,TypeError)):root.fork(at=200,operation='')
if __name__=='__main__':unittest.main()
