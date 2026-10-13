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
tarballs = list(ROOT.glob('worflor-vixen-*.tgz'))
wheels = list((ROOT / 'dist').glob('vixen_curves-*.whl'))
assert len(tarballs) == len(wheels) == 1, 'Build exactly one current tarball and wheel first'
node_target = SMOKE / 'node'
run([npm, 'install', '--prefix', str(node_target), str(tarballs[0]), '--ignore-scripts', '--no-package-lock'])
js = "import {create,restore,ALGORITHM} from '@worflor/vixen'; const c=create({seed:'distribution-smoke',stabilizer:.85,floor:'backbone',ceilingMargin:.35,boundarySoftness:.1}); const s={algorithm:ALGORITHM,config:c.config}; if(JSON.stringify(restore(s).sample(144))!==JSON.stringify(c.sample(144)))throw Error('Replay mismatch'); console.log('Installed JS package: replay passed');"
run([node, '--input-type=module', '-e', js], node_target)

python_target = SMOKE / 'python'
if importlib.util.find_spec('pip'):
    install = [sys.executable, '-m', 'pip', 'install']
else:
    uv = shutil.which('uv')
    assert uv, 'Provide pip or uv for the Python installation smoke'
    install = [uv, 'pip', 'install', '--python', sys.executable]
run(install + ['--no-deps', '--target', str(python_target), str(wheels[0])])
py = f"import sys,json;sys.path.insert(0,{str(python_target)!r});import vixen;from vixen import Vixen,restore,ALGORITHM;assert {str(python_target)!r} in vixen.__file__;c=Vixen(seed='distribution-smoke',stabilizer=.85,floor='backbone',ceilingMargin=.35,boundarySoftness=.1);s={{'algorithm':ALGORITHM,'config':dict(c.config)}};assert restore(json.loads(json.dumps(s))).sample(144)==c.sample(144);print('Installed Python wheel: JSON replay passed')"
run([sys.executable, '-I', '-c', py], SMOKE)
