"""Check the installed distributions expose current composition and scaled APIs."""
from pathlib import Path
import subprocess,sys
ROOT=Path(__file__).resolve().parents[1]
TARGET=ROOT/'.smoke/distribution'
JS="""import assert from 'node:assert/strict';
import {createCoherent,createExtended,extendedPolynomialGuide,coupleExtended,restore,polynomialGuide,Wide,wideExp,couple} from '@worflor/vixen';
assert.ok(import.meta.resolve('@worflor/vixen').includes('/node_modules/@worflor/vixen/'));
const root=createCoherent({seed:'installed-current',floor:'baseline',ceilingMargin:.3});
const curve=root.influence(polynomialGuide([.1]),{at:120,amount:.3,reference:'baseline',until:180,releaseWidth:10}).impulse(130,.1,20).fork({at:200,operation:'installed',handoffWidth:12});
for(const x of [110,125,145,190,205,230]){const replay=restore(JSON.parse(JSON.stringify(curve.snapshot())));assert.deepEqual(replay.sample(x),curve.sample(x));assert.deepEqual(replay.jet(x),curve.jet(x));}
const x=220,checkpoint=restore(JSON.parse(JSON.stringify(curve.tail(x))));assert.deepEqual(checkpoint.sample(230),curve.sample(230));
assert.throws(()=>couple([root,createCoherent()],.4,[120,190]),/nonlinear/);
const group=couple([createCoherent(),createCoherent({seed:8})],.4,[120,190]);assert.ok(group.curves[0].jet(150).availability.slope);
assert.equal(Wide.fromNumber(2).mul(3).toNumber(),6);assert.equal(wideExp(1e6).toNumber(),null);
const far=root.sampleNormalized(1e6);assert.equal(far.base,null);assert.ok(Number.isFinite(far.logBase));
const ext=createExtended().influence(extendedPolynomialGuide([1]),{at:120,amount:1,handoffWidth:1,reference:'value'});assert.equal(ext.sampleNormalized(1e9).value,1);const extendedGroup=coupleExtended([ext,createExtended({seed:2})],.4,[1e9,1e9+100]);assert.deepEqual(restore(extendedGroup.curves[0].snapshot()).sampleNormalized(1e9+25),extendedGroup.curves[0].sampleNormalized(1e9+25));
console.log('Installed JS: coherent composition, analytic jets, graph replay, active tails, coupling and scaled APIs passed');
"""
PY="""import json,sys
from pathlib import Path
sys.path.insert(0,sys.argv[1])
import vixen
from vixen import Vixen,create_coherent,create_extended,extended_polynomial_guide,couple_extended,restore,polynomial_guide,Wide,wide_exp
assert Path(sys.argv[1]) in Path(vixen.__file__).parents
root=create_coherent(seed='installed-current',floor='baseline',ceilingMargin=.3)
curve=root.influence(polynomial_guide([.1]),at=120,amount=.3,reference='baseline',until=180,releaseWidth=10).impulse(at=130,magnitude=.1,relaxation=20).fork(at=200,operation='installed',handoffWidth=12)
for x in [110,125,145,190,205,230]:
 replay=restore(json.loads(json.dumps(curve.snapshot())))
 assert replay.sample(x)==curve.sample(x)
 assert replay.jet(x)==curve.jet(x)
checkpoint=restore(json.loads(json.dumps(curve.tail(220))))
assert checkpoint.sample(230)==curve.sample(230)
assert Wide.from_number(2).mul(3).to_number()==6
assert wide_exp(1e6).to_number() is None
assert root.sample_normalized(1e6)['base'] is None
ext=create_extended().influence(extended_polynomial_guide([1]),at=120,amount=1,handoffWidth=1,reference='value')
assert ext.sample_normalized(1e9)['value']==1
group=couple_extended([ext,create_extended(seed=2)],.4,[1e9,1e9+100])
assert restore(group.curves[0].snapshot()).sample_normalized(1e9+25)==group.curves[0].sample_normalized(1e9+25)
print('Installed Python: coherent/extended composition, analytic jets, graph replay, active tails and scaled APIs passed')
"""
def main():
    subprocess.run(['node','--input-type=module','-e',JS],cwd=TARGET/'node',check=True)
    subprocess.run([sys.executable,'-I','-c',PY,str(TARGET/'python')],cwd=TARGET,check=True)
if __name__=='__main__':main()
