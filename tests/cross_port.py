"""Seeded timing and nested-fork parity: actual JS/Python evaluations."""
from pathlib import Path
import random,json,subprocess,sys
ROOT=Path(__file__).resolve().parent.parent
sys.path.insert(0,str(ROOT))
from vixen import Vixen,restore,ALGORITHM,seed32,hash32,navigation_basis
rng=random.Random(760123);seeds=['vixen','🌲','42',42,0,4294967295,'','é','e\u0301','\ud800'];cases=[]
for i in range(2400):
 cfg={'seed':seeds[i%len(seeds)],'openingEndpoint':[2,50,100,200,1000][i%5],'introVariation':[0,.25,.5,1][i%4],'stabilizer':(i%101)/100,'floor':['none','zero','baseline','backbone'][i%4],'ceilingMargin':[None,0,.01,.5,8][(i//4)%5],'boundarySoftness':[0,.1,1][(i//20)%3],'strength':rng.uniform(0,2),'ramp':rng.uniform(1,64),'tempo':rng.uniform(.25,4),'contrast':rng.uniform(0,16),'latent':[rng.uniform(-6,6) for _ in range(16)],'velocity':[rng.uniform(-.4,.4) for _ in range(16)]}
 f=Vixen(**cfg);end=f.domain[1];at=min(end/2,cfg['openingEndpoint']+30);lineage=[]
 if i%3:
  lineage=[{'at':at,'operation':['door','🦊','e\u0301','\ud800'][i%4],'handoffWidth':16}]
 if i%3==2:lineage.append({'at':at+40.125,'operation':'nested:'+str(i%31),'handoffWidth':20})
 x=rng.uniform(1,end) if i%3 else rng.uniform(1,min(end,700))
 if i%7==0 and lineage:x=lineage[-1]['at']+lineage[-1]['handoffWidth']*.5
 h=[rng.uniform(-8,8) for _ in range(16)] if i%2 else None
 cases.append({'config':cfg,'x':x,'h':h,'lineage':lineage})
program="import fs from 'node:fs';import {ALGORITHM,restore,seed32,hash32,navigationBasis} from './vixen.mjs';const cases=JSON.parse(fs.readFileSync(0,'utf8'));console.log(JSON.stringify(cases.map((c,i)=>{const f=restore({algorithm:ALGORITHM,config:c.config,lineage:c.lineage});return {sample:c.h?f.at(c.x,c.h):f.sample(c.x),seed:f.config.seed,hash:hash32(seed32(c.config.seed),i,7),plane:navigationBasis(i%20),sensitivity:f.bounds.unconstrainedSpatialSlope}})));"
r=subprocess.run(['node','--input-type=module','-e',program],input=json.dumps(cases),text=True,encoding='utf-8',capture_output=True,cwd=ROOT,check=True);rows=json.loads(r.stdout);assert len(rows)==len(cases)==2400;max_error=0
for i,(case,js) in enumerate(zip(cases,rows)):
 f=restore({'algorithm':ALGORITHM,'config':case['config'],'lineage':case['lineage']});p=f.at(case['x'],case['h']) if case['h'] else f.sample(case['x']);assert js['hash']==hash32(seed32(case['config']['seed']),i,7);assert js['seed']==f.config['seed'];assert js['sensitivity']==f.bounds['unconstrainedSpatialSlope']
 if p['floorValue'] is not None:assert p['value']>=p['floorValue']
 if p['ceilingValue'] is not None:assert p['value']<=p['ceilingValue']
 assert f.bounds['deviation'][0]-1e-12<=p['deviation']<=f.bounds['deviation'][1]+1e-12
 for key in p:
  if p[key] is None or isinstance(p[key],bool):assert p[key]==js['sample'][key],(i,key,p[key],js['sample'][key]);continue
  error=abs(p[key]-js['sample'][key])/max(1,abs(p[key]),abs(js['sample'][key]));max_error=max(max_error,error);assert error<1e-10,(i,key,error)
 plane=navigation_basis(i%20)
 for key in ['u','v']:
  for a,b in zip(plane[key],js['plane'][key]):assert abs(a-b)<1e-12
metrics={'algorithm':ALGORITHM,'cases':len(cases),'fork_cases':sum(bool(c['lineage']) for c in cases),'nested_fork_cases':sum(len(c['lineage'])==2 for c in cases),'max_scaled_error':max_error,'acceptance_tolerance':1e-10,'sensitivity_parity':'exact','hash_parity':'exact','branch_seed_parity':'exact','navigation_basis_parity_tolerance':1e-12,'seed_types':len(seeds)}
(ROOT/'research'/'hyper-parity.json').write_text(json.dumps(metrics,indent=2),encoding='utf-8');print(json.dumps(metrics,indent=2))
