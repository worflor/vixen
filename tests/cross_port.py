from pathlib import Path
import random,json,subprocess,math
import sys
ROOT=Path(__file__).resolve().parent.parent
sys.path.insert(0,str(ROOT))
from vixen import Vixen,seed32,hash32,navigation_basis
rng=random.Random(760123);seeds=['vixen','🌲','42',42,0,4294967295,'','é','e\u0301','\ud800'];cases=[]
for i in range(2400):
 cfg={'seed':seeds[i%len(seeds)],'stabilizer':(i%101)/100,'strength':rng.uniform(0,2),'ramp':rng.uniform(1,64),'tempo':rng.uniform(.25,4),'contrast':rng.uniform(0,16),'latent':[rng.uniform(-6,6) for _ in range(16)],'velocity':[rng.uniform(-.4,.4) for _ in range(16)]}
 x=rng.uniform(1,50000) if i%3 else rng.uniform(90,700);h=[rng.uniform(-8,8) for _ in range(16)] if i%2 else None
 cases.append({'config':cfg,'x':x,'h':h})
program="import fs from 'node:fs';import {create,seed32,hash32,navigationBasis} from './vixen.mjs';const cases=JSON.parse(fs.readFileSync(0,'utf8'));console.log(JSON.stringify(cases.map((c,i)=>{const f=create(c.config);return {sample:c.h?f.at(c.x,c.h):f.sample(c.x),hash:hash32(seed32(c.config.seed),i,7),plane:navigationBasis(i%20)}})));"
r=subprocess.run(['node','--input-type=module','-e',program],input=json.dumps(cases),text=True,encoding='utf-8',capture_output=True,cwd=ROOT,check=True);rows=json.loads(r.stdout);max_error=0
for i,(case,js) in enumerate(zip(cases,rows)):
 f=Vixen(**case['config']);p=f.at(case['x'],case['h']) if case['h'] else f.sample(case['x']);assert js['hash']==hash32(seed32(case['config']['seed']),i,7)
 for key in p:
  error=abs(p[key]-js['sample'][key])/max(1,abs(p[key]),abs(js['sample'][key]));max_error=max(max_error,error);assert error<1e-10,(i,key,error)
 plane=navigation_basis(i%20)
 for key in ['u','v']:
  for a,b in zip(plane[key],js['plane'][key]):assert abs(a-b)<1e-12
metrics={'cases':len(cases),'max_scaled_error':max_error,'acceptance_tolerance':1e-10,'hash_parity':'exact','navigation_basis_parity_tolerance':1e-12,'seed_types':len(seeds)}
(ROOT/'research'/'hyper-parity.json').write_text(json.dumps(metrics,indent=2));print(json.dumps(metrics,indent=2))
