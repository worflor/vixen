import unittest,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from vixen import restore,ALGORITHM
class ReplayBoundary(unittest.TestCase):
 def test_unknown_fields_across_algorithms(self):
  for algorithm in [ALGORITHM,'vixen-hyperfield-c2/1','vixen-hyperfield-c2/2','vixen-hyperfield-c2/3']:
   with self.assertRaises(ValueError):restore({'algorithm':algorithm,'config':{'seed':'x'},'surprise':1})
   self.assertEqual(restore({'algorithm':algorithm,'config':{'seed':'x'}}).config['seed'],'x')
 def test_malformed_envelopes(self):
  for value in [None,[],True,{'algorithm':ALGORITHM},{'algorithm':ALGORITHM,'config':[]}]:
   with self.assertRaises((TypeError,ValueError)):restore(value)
if __name__=='__main__':unittest.main()
