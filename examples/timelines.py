import json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from vixen import Vixen,restore
root=Vixen(seed='timeline-example',openingEndpoint=160,introVariation=1,floor='zero')
door=root.fork(at=240,operation='opened-left-door',handoffWidth=24)
key=door.fork(at=320,operation='took-key')
replay=restore(json.loads(json.dumps(key.snapshot())))
for x in [1,42,160,239,240]:assert root.sample(x)==door.sample(x)
for x in [241,270,319,320]:assert door.sample(x)==key.sample(x)
for x in [1,240,250,320,330,500]:assert key.sample(x)==replay.sample(x)
print(json.dumps({'lineage':[dict(n) for n in key.lineage],'position':key.location(320),'futureActivity':key.sample(500)['activity'],'history':'exact','replay':'exact'},indent=2))
