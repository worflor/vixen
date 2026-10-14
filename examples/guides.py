import math,json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from vixen import Vixen,polynomial_guide,taylor_guide,capture_guide,restore
root=Vixen(seed='guided-world')
line=root.influence(polynomial_guide([3,2]),at=160,amount=1)
series=root.influence(taylor_guide([1,1,1,1],center=160,domain=[1,500]),at=160,amount=.7)
guide=capture_guide(lambda x:80*math.sin(x/23)+.3*x,domain=[100,500],count=129)
future=root.influence(guide,at=120,amount=.7).fork(at=180,operation='take-key')
replay=restore(json.loads(json.dumps(future.snapshot())))
print(json.dumps({'line':line.sample(200)['value'],'series':series.sample(200)['value'],'guided':future.sample(200)['value'],'replay':replay.sample(200)['value']},indent=2))
