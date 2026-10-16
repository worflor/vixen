"""Exercise the distributions, never a checkout-shadowed runtime."""
from pathlib import Path
import importlib.util
import json
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
SMOKE = ROOT / '.smoke' / 'distribution'
if SMOKE.exists():
    shutil.rmtree(SMOKE)
SMOKE.mkdir(parents=True)

def run(args, cwd=ROOT):
    subprocess.run(args, cwd=cwd, check=True)

npm = shutil.which('npm')
node = shutil.which('node')
assert npm and node, 'Node and npm are required'
metadata = json.loads((ROOT / 'package.json').read_text(encoding='utf-8'))
version = metadata['version']
npm_name = metadata['name'].lstrip('@').replace('/', '-')
tarballs = [ROOT / f'{npm_name}-{version}.tgz']
wheels = list((ROOT / 'dist').glob(f'vixen_curves-{version}-*.whl'))
assert tarballs[0].is_file() and len(wheels) == 1, 'Build the current package-version tarball and wheel first'
node_target = SMOKE / 'node'
run([npm, 'install', '--prefix', str(node_target), str(tarballs[0]), '--ignore-scripts', '--no-package-lock'])
js = "import {create,restore,polynomialGuide,captureGuide} from '@worflor/vixen'; const root=create({seed:'distribution-smoke',openingEndpoint:160,introVariation:1,stabilizer:.85,floor:'backbone',ceilingMargin:.35,boundarySoftness:.1}); const c=root.fork({at:200,operation:'installed-door'}).fork({at:250,operation:'installed-key'}); const s=c.snapshot(); for(const x of [100,200,210,250,260,300])if(JSON.stringify(restore(s).sample(x))!==JSON.stringify(c.sample(x)))throw Error('Replay mismatch'); if(JSON.stringify(root.sample(200))!==JSON.stringify(c.sample(200)))throw Error('History mismatch'); const guided=create().influence(polynomialGuide([3,2]),{at:160,amount:1}).fork({at:220,operation:'guided'});if(restore(JSON.parse(JSON.stringify(guided.snapshot()))).sample(260).value!==523)throw Error('Installed guide replay'); const captured=create().influence(captureGuide(x=>Math.sin(x/10),{domain:[100,500],count:33}),{at:120});if(!Number.isFinite(captured.sample(200).value))throw Error('Installed capture');console.log('Installed JS package: nested timelines, polynomial and arbitrary capture replay passed');"
run([node, '--input-type=module', '-e', js], node_target)

python_target = SMOKE / 'python'
if importlib.util.find_spec('pip'):
    install = [sys.executable, '-m', 'pip', 'install']
else:
    uv = shutil.which('uv')
    assert uv, 'Provide pip or uv for the Python installation smoke'
    install = [uv, 'pip', 'install', '--python', sys.executable]
run(install + ['--no-deps', '--target', str(python_target), str(wheels[0])])
py = f"import sys,json;sys.path.insert(0,{str(python_target)!r});import vixen;from vixen import Vixen,restore,ALGORITHM;assert {str(python_target)!r} in vixen.__file__;root=Vixen(seed='distribution-smoke',openingEndpoint=160,introVariation=1,stabilizer=.85,floor='backbone',ceilingMargin=.35,boundarySoftness=.1);c=root.fork(at=200,operation='installed-door').fork(at=250,operation='installed-key');s=c.snapshot();assert all(restore(json.loads(json.dumps(s))).sample(x)==c.sample(x) for x in [100,200,210,250,260,300]);assert root.sample(200)==c.sample(200);guided=vixen.Vixen().influence(vixen.polynomial_guide([3,2]),at=160,amount=1).fork(at=220,operation='guided');assert restore(json.loads(json.dumps(guided.snapshot()))).sample(260)['value']==523;capture=vixen.capture_guide(lambda x:x*x,domain=[100,500],count=33);assert vixen.Vixen().influence(capture,at=120).sample(200)['value']>0;print('Installed Python wheel: nested timelines, polynomial and arbitrary capture replay passed')"
run([sys.executable, '-I', '-c', py], SMOKE)
