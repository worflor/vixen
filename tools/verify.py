"""Rebuild current acceptance from real executions; retain scoped history."""
from pathlib import Path
import json
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parent.parent

def run(args):
    p=subprocess.run(args,cwd=ROOT,text=True,encoding='utf-8',capture_output=True)
    if p.returncode:
        print(p.stdout,p.stderr)
        raise SystemExit(p.returncode)
    return p.stdout

files=['test-hyperfield.mjs','test-kernel.mjs','test-stabilizer.mjs','test-constraints.mjs','test-examples.mjs','test-opening-endpoint.mjs','test-fork.mjs','test-timeline-safety.mjs','test-fork-collisions.mjs']
xml=run(['node','--test','--test-reporter=junit']+['tests/'+f for f in files])
tree=ET.fromstring(xml)
assert not list(tree.iter('failure')) and not list(tree.iter('error'))
count=len(list(tree.iter('testcase')))
print(run([sys.executable,'tests/test_python.py']))
print(run([sys.executable,'tests/test_python_new_features.py']))
print(run([sys.executable,'tests/cross_port.py']))
report={
 'algorithm':'vixen-hyperfield-c2/4',
 'node_tests':count,
 'cross_port':json.loads((ROOT/'research/hyper-parity.json').read_text()),
 'constraints':{'floors':['none','zero','baseline','backbone'],'ceiling':'nullable baseline-scaled margin','softness':'0 hard; positive C2 boundary kernel'},
 'acceptance':['unchanged calm prefix','unrestricted legacy replay','feasible floor/ceiling band','zero-width collapse','constrained stabilization endpoint','derived adapters agree with constrained signal','numerical C2 kernel joins'],
 'component_evidence':'research/constraints-evidence.json',
 'limitations':['Component evidence is not a whole-program certificate.','Hard boundaries may introduce corners.','Nonlinear boundaries do not preserve the unrestricted linear contraction factor.','Float curves are not cross-platform bitwise lockstep.'],
}
(ROOT/'research/verification-game.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print('Verified current acceptance:',count,'JavaScript tests')
