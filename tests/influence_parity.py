"""Differential guided timelines, with actual evaluations in both ports."""
from pathlib import Path
import sys,json,subprocess,random,math
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT))
from vixen import Vixen,polynomial_guide,capture_guide,restore
rng=random.Random(97431);cases=[]
for i in range(160):
 cfg={'seed':str(i),'floor':['none','zero','baseline','backbone'][i%4],'boundarySoftness':[0,.1,1][i%3],'ceilingMargin':[None,0,.1,2][(i//4)%4],'strength':rng.random()*2,'stabilizer':rng.random()}
 root=Vixen(**cfg)
 if i%2:g=polynomial_guide([rng.uniform(-200,200),rng.uniform(-3,3),rng.uniform(-.01,.01)],domain=[1,500])
 else:g=capture_guide(lambda x:80*math.sin(x/23)+.3*x,domain=[100,500],count=17)
 f=root.influence(g,at=120,amount=rng.random(),handoffWidth=24).fork(at=150,operation='door',handoffWidth=32)
 if i%3==0:f=f.influence(polynomial_guide([20,.5],domain=[1,500]),at=160,amount=.7,handoffWidth=20)
 snap=f.snapshot()
 for x in [1,100,120,130,150,160,171,180,200,499,500]:
  h=[rng.uniform(-3,3) for _ in range(16)] if i%2 else None;cases.append({'snapshot':snap,'x':x,'h':h})
js="import fs from 'node:fs';import {restore} from './vixen.mjs';const c=JSON.parse(fs.readFileSync(0,'utf8'));console.log(JSON.stringify(c.map(r=>{let f=restore(r.snapshot);return r.h?f.at(r.x,r.h):f.sample(r.x)})));"
p=subprocess.run(['node','--input-type=module','-e',js],cwd=ROOT,input=json.dumps(cases),text=True,capture_output=True,check=True);rows=json.loads(p.stdout);assert len(rows)==len(cases);maximum=0
for case,b in zip(cases,rows):
 f=restore(case['snapshot']);a=f.at(case['x'],case['h']) if case['h'] is not None else f.sample(case['x']);assert set(a)==set(b)
 for k,v in a.items():
  if v is None or isinstance(v,bool):assert v==b[k];continue
  error=abs(v-b[k])/max(1,abs(v),abs(b[k]));maximum=max(maximum,error);assert error<1e-10,(k,error,case)
 if a['floorValue'] is not None:assert a['value']>=a['floorValue']
 if a['ceilingValue'] is not None:assert a['value']<=a['ceilingValue']
result={'cases':len(cases),'seed':97431,'max_scaled_error':maximum,'tolerance':1e-10,'scope':'stored polynomial/captured guide recipes, layered guides, later forks, four floors, nullable/collapsed ceilings and coordinate sampling'}
(ROOT/'research/influence-parity.json').write_text(json.dumps(result,indent=2),encoding='utf-8');print(json.dumps(result,indent=2))
