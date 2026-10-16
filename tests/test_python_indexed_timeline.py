import unittest,json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from vixen import Vixen,restore
from vixen_checkpoint import history_witness

class IndexedTimeline(unittest.TestCase):
 def test_deep_history(self):
  f=Vixen(seed='python-deep');saved=[]
  for i in range(3000):
   f=f.fork(at=200+i,operation='step:'+str(i),handoffWidth=.25)
   if i%500==0:saved.append((200+i+.5,f.sample(200+i+.5)))
  for x,s in saved:self.assertEqual(f.sample(x),s)
  self.assertEqual(restore(json.loads(json.dumps(f.snapshot()))).sample(4000),f.sample(4000))
 def test_tail_and_refolding(self):
  f=Vixen(seed='tail:🦊',introVariation=1,floor='backbone',ceilingMargin=.5)
  for i in range(150):f=f.fork(at=200+i*2,operation='step:'+str(i),handoffWidth=16 if i%3 else 100)
  for start in [1,100,200,250.125,499,520,1000]:
   g=restore(json.loads(json.dumps(f.tail(start))))
   for x in [start,start+.001,start+30,2000]:
    self.assertEqual(g.sample(x),f.sample(x));self.assertEqual(g.at(x,[.125]*16),f.at(x,[.125]*16))
   if start>1:
    with self.assertRaises(ValueError):g.sample(start-.001)
  g=restore(f.tail(1000));self.assertEqual(g.snapshot()['foldedCount'],150);self.assertEqual(g.lineage,())
  a=g.fork(at=1100,operation='new');b=f.fork(at=1100,operation='new')
  for x in [1100,1108,1116,2000]:self.assertEqual(a.sample(x),b.sample(x))
  self.assertEqual(restore(a.tail(1200)).sample(1300),b.sample(1300))
 def test_unfolded_seed_type(self):
  for seed in ['0',0]:
   f=Vixen(seed=seed);g=restore(f.tail(150))
   self.assertEqual(type(g.config['seed']),type(seed))
   self.assertEqual(g.fork(at=200,operation='x').sample(300),f.fork(at=200,operation='x').sample(300))

if __name__=='__main__':unittest.main()
