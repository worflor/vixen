import unittest
from vixen import create_extended,extended_polynomial_guide,couple_extended,restore

class ExtendedCouplingTests(unittest.TestCase):
 def test_values_replay_history_and_fork_refusal(self):
  a=create_extended(seed='coupling-a');b=create_extended(seed='coupling-b')
  group=couple_extended([a,b],.6,[1e9,1e9+100])
  for x in [1e9-1,1e9,1e9+30,1e9+100]:
   actual=group.sample_normalized(x)
   self.assertEqual(restore(group.snapshot()).sample_normalized(x),actual)
   if x<=1e9 or x>=1e9+100:self.assertEqual(tuple(actual),(a.sample_normalized(x),b.sample_normalized(x)))
  tiny=a.influence(extended_polynomial_guide([1]),at=120,amount=1,reference='value')
  self.assertEqual(couple_extended([tiny,tiny],1,[1e9,1e9+100]).sample_normalized(1e9+50)[0]['value'],1)
  with self.assertRaisesRegex((ValueError,TypeError),'ambiguous'):
   group.curves[0].fork(at=1e9+120,operation='ambiguous')
  with self.assertRaises((ValueError,TypeError)):
   group.curves[0].impulse(dict(at=1e9-1,magnitude=.1,relaxation=10))
  tail=restore(group.curves[0].tail(1e9+110))
  self.assertEqual(tail.sample_normalized(1e9+130),a.sample_normalized(1e9+130))

if __name__=='__main__':unittest.main()
