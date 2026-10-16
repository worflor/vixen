import json,math,unittest
from pathlib import Path
from collections.abc import Mapping
from vixen import restore
from vixen_wide import Wide,WIDE_FORMAT

class ExtendedPortability(unittest.TestCase):
 def same(self,actual,expected,path):
  if isinstance(expected,dict):
   self.assertIsInstance(actual,Mapping,path);self.assertEqual(set(actual),set(expected),path)
   if expected.get('format')==WIDE_FORMAT:
    a,e=Wide.decode(actual),Wide.decode(expected)
    if a.cmp(e):
     self.assertNotEqual(e.sign,0,path);error=a.sub(e).abs().div(e.abs()).to_number()
     self.assertTrue(error is not None and error<=8e-14,(path,error))
    return
   for k,v in expected.items():self.same(actual[k],v,path+'.'+k)
  elif isinstance(expected,(int,float)) and not isinstance(expected,bool):self.assertTrue(actual is not None and abs(actual-expected)<=max(2e-13,abs(expected)*8e-14),(path,actual,expected))
  else:self.assertEqual(actual,expected,path)
 def test_frozen_native_and_composed(self):
  count=0
  for name in ('native','composed'):
   data=json.loads((Path(__file__).parent/'fixtures'/'v6'/(name+'.json')).read_text(encoding='utf-8'))
   for case in data['cases']:
    curve=restore(case['snapshot'])
    for row in case['samples']:
     p=curve.at_normalized(row['x'],row['h']) if 'h' in row else curve.sample_normalized(row['x']);self.same(p,row['expected'],name+str(count));count+=1
    for row in case['checkpoints']:self.same(restore(row['snapshot']).sample_normalized(row['x']),row['expected'],name+'checkpoint'+str(count));count+=1
  print('Python extended cross-port samples/checkpoints:',count)
if __name__=='__main__':unittest.main()
