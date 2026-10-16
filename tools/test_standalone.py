"""Exercise generated SDKs outside repository imports."""
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

ROOT=Path(__file__).resolve().parents[1]

def run(args,cwd):
    result=subprocess.run(args,cwd=cwd,text=True,capture_output=True)
    if result.returncode:
        sys.stderr.write(result.stdout+result.stderr)
        raise SystemExit(result.returncode)
    return result.stdout.strip()

run([sys.executable,'tools/build_instrument.py'],ROOT)
expected=json.loads(run(['node','--input-type=module','-e',f'import * as m from {json.dumps((ROOT/"vixen.mjs").as_uri())};console.log(JSON.stringify(Object.keys(m).sort()));'],ROOT))
with tempfile.TemporaryDirectory(prefix='vixen-isolated-sdk-') as directory:
    target=Path(directory)
    for name in ['standalone-vixen.mjs','standalone_vixen.py']:
        shutil.copy2(ROOT/'.smoke'/name,target/name)
    javascript="""
import assert from 'node:assert/strict';
import * as m from './standalone-vixen.mjs';
assert.deepEqual(Object.keys(m).sort(),EXPECTED);
const n=m.createCoherent({seed:1,floor:'baseline'}).fork({at:120,operation:'standalone',handoffWidth:80});
const c=n.influence(m.polynomialGuide([.2,.01],{center:150}),{at:150,handoffWidth:10,amount:1,reference:'baseline',until:180,releaseWidth:10}).impulse({at:160,magnitude:.2,attack:4,relaxation:10});
const restored=m.restore(c.snapshot());
for(const x of [150,160,165,185]){const q=c.jet(x);assert.deepEqual(q.sample,c.sample(x));assert.ok(Number.isFinite(q.slope));assert.deepEqual(restored.jet(x),q);}
const e=n.encloseDeviation(149,151);assert.ok(e[0]<=n.sample(150).deviation&&n.sample(150).deviation<=e[1]);
const tail=m.restore(n.tail(150));assert.equal(tail.snapshot().algorithm,m.COHERENT_TAIL_ALGORITHM);assert.deepEqual(tail.sample(170),n.sample(170));
const far=m.createExtended({seed:'sdk-far',floor:'baseline'}).fork({at:1e8,operation:'far',handoffWidth:32});
assert.deepEqual(m.restore(far.snapshot()).sampleNormalized(1e9),far.sampleNormalized(1e9));assert.deepEqual(m.restore(far.tail(1e9)).sampleNormalized(1e9+10),far.sampleNormalized(1e9+10));
const tiny=m.createExtended().influence(m.extendedPolynomialGuide([1]),{at:120,amount:1,handoffWidth:1,reference:'value'});assert.equal(tiny.sampleNormalized(1e9).value,1);assert.deepEqual(m.restore(tiny.snapshot()).sampleNormalized(1e9),tiny.sampleNormalized(1e9));
const shared=m.coupleExtended([m.createExtended(),m.createExtended({seed:2})],.5,[1e9,1e9+100]);assert.deepEqual(m.restore(shared.snapshot()).sampleNormalized(1e9+25),shared.sampleNormalized(1e9+25));
console.log('Isolated JavaScript SDK: coherent and extended roots, analytic composition, graph replay, checkpoints, tiny targets and enclosure passed');
""".replace('EXPECTED',json.dumps(expected))
    print(run(['node','--input-type=module','-e',javascript],target))
    python="""
import math,sys
sys.path.insert(0,DIRECTORY)
import standalone_vixen as m
n=m.create_coherent(seed=1,floor='baseline').fork(at=120,operation='standalone',handoffWidth=80)
c=n.influence(m.polynomial_guide([.2,.01],center=150),at=150,handoffWidth=10,amount=1,reference='baseline',until=180,releaseWidth=10).impulse(dict(at=160,magnitude=.2,attack=4,relaxation=10))
restored=m.restore(c.snapshot())
for x in [150,160,165,185]:
    q=c.jet(x)
    assert q['sample']==c.sample(x) and math.isfinite(q['slope'])
    assert restored.jet(x)==q
e=n.enclose_deviation(149,151)
assert e[0]<=n.sample(150)['deviation']<=e[1]
tail=m.restore(n.tail(150))
assert tail.snapshot()['algorithm']==m.COHERENT_TAIL_ALGORITHM and tail.sample(170)==n.sample(170)
assert not any(name in sys.modules for name in ['vixen_api','vixen_operations','vixen_influence_calculus'])
far=m.create_extended(seed='sdk-far',floor='baseline').fork(at=1e8,operation='far',handoffWidth=32)
assert m.restore(far.snapshot()).sample_normalized(1e9)==far.sample_normalized(1e9)
assert m.restore(far.tail(1e9)).sample_normalized(1e9+10)==far.sample_normalized(1e9+10)
tiny=m.create_extended().influence(m.extended_polynomial_guide([1]),at=120,amount=1,handoffWidth=1,reference='value')
assert tiny.sample_normalized(1e9)['value']==1
assert m.restore(tiny.snapshot()).sample_normalized(1e9)==tiny.sample_normalized(1e9)
shared=m.couple_extended([m.create_extended(),m.create_extended(seed=2)],.5,[1e9,1e9+100])
assert m.restore(shared.snapshot()).sample_normalized(1e9+25)==shared.sample_normalized(1e9+25)
print('Isolated Python SDK: coherent and extended roots, analytic composition, graph replay, checkpoints, tiny targets and enclosure passed')
""".replace('DIRECTORY',repr(str(target)))
    print(run([sys.executable,'-I','-c',python],target))
