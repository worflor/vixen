"""Conformance reads frozen data, never regenerates expected samples."""
import gzip,json,math,unittest
from pathlib import Path
import vixen as V
ROOT=Path(__file__).resolve().parent/'fixtures'/'v1.4.0'
def fixture(name):
 with gzip.open(ROOT/(name+'.json.gz'),'rt',encoding='utf-8') as f:return json.load(f)
class FrozenConformance(unittest.TestCase):
 def compare(self,actual,expected,label,tolerance=1e-10):
  if expected is None or isinstance(expected,(bool,str)):
   self.assertEqual(actual,expected,label);return
  if isinstance(expected,(int,float)):
   self.assertTrue(math.isfinite(actual) and math.isfinite(expected),label)
   self.assertLessEqual(abs(actual-expected),tolerance*max(1,abs(actual),abs(expected)),label);return
  if isinstance(expected,list):
   self.assertEqual(len(actual),len(expected),label)
   for i,(a,b) in enumerate(zip(actual,expected)):self.compare(a,b,label+str(i),tolerance)
  else:
   self.assertEqual(set(actual),set(expected),label)
   for k in expected:self.compare(actual[k],expected[k],label+'.'+k,tolerance)
 def test_field_corpus(self):
  doc=fixture('cross-port');self.assertEqual(len(doc['cases']),2400)
  self.assertEqual(doc['vixen']['commit'],'3f6a6683f54380516181f3a8f62958ae628957d0')
  for i,c in enumerate(doc['cases']):
   f=V.restore({'algorithm':V.ALGORITHM,'config':c['config'],'lineage':c['lineage']});e=c['expected']
   self.compare(f.at(c['x'],c['h']) if c['h'] is not None else f.sample(c['x']),e['sample'],str(i))
   self.assertEqual(f.config['seed'],e['seed']);self.assertEqual(V.hash32(V.seed32(c['config']['seed']),i,7),e['hash'])
   self.assertEqual(f.bounds['unconstrainedSpatialSlope'],e['sensitivity'])
   self.compare(V.navigation_basis(i%20),e['plane'],'navigation'+str(i),1e-12)
 def test_guided_corpus(self):
  doc=fixture('influence-parity');self.assertEqual(len(doc['cases']),1760)
  for i,c in enumerate(doc['cases']):
   f=V.restore(c['snapshot'])
   self.compare(f.at(c['x'],c['h']) if c['h'] is not None else f.sample(c['x']),c['expected'],'guided'+str(i))
if __name__=='__main__':unittest.main()
