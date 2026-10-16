"""Independent principal-minor and cross-language checks for exact PSD eligibility."""
import json,random,subprocess,unittest
from fractions import Fraction as F
from itertools import combinations
from pathlib import Path
from vixen_correlation import exact_correlation_factor

def determinant(matrix):
    if not matrix:return F(1)
    return sum(((-1)**j*matrix[0][j]*determinant([row[:j]+row[j+1:] for row in matrix[1:]]) for j in range(len(matrix))),F(0))
def principal_minor_oracle(matrix):
    n=len(matrix);a=[[F(x) for x in row] for row in matrix]
    return all(determinant([[a[i][j] for j in idx] for i in idx])>=0 for size in range(1,n+1) for idx in combinations(range(n),size))
class ExactCorrelationTests(unittest.TestCase):
    def test_independent_minor_oracle_and_cross_port_factors(self):
        rng=random.Random(41871);cases=[]
        for size in range(1,5):
            for _ in range(20):
                matrix=[[1. if i==j else 0. for j in range(size)] for i in range(size)]
                for i in range(size):
                    for j in range(i):matrix[i][j]=matrix[j][i]=rng.choice([-1,-.75,-.5,-.25,0,.25,.5,.75,1])
                cases.append(matrix)
        root=Path(__file__).resolve().parents[1]
        js="import{exactCorrelationFactor as f}from'./vixen-correlation.mjs';let s='';for await(const c of process.stdin)s+=c;console.log(JSON.stringify(JSON.parse(s).map(m=>{try{return{ok:true,...f(m)}}catch(e){return{ok:false}}})));"
        p=subprocess.run(['node','--input-type=module','-e',js],cwd=root,input=json.dumps(cases),capture_output=True,text=True,timeout=60)
        self.assertEqual(p.returncode,0,p.stderr);answers=json.loads(p.stdout)
        for matrix,j in zip(cases,answers):
            expected=principal_minor_oracle(matrix)
            try:q=exact_correlation_factor(matrix);pyok=True
            except ValueError:q=None;pyok=False
            self.assertEqual(pyok,expected,matrix);self.assertEqual(j['ok'],expected,matrix)
            if expected:
                self.assertEqual(q['factor'],j['factor']);self.assertEqual(q['factorError'],j['factorError'])
                f=q['factor'];n=len(f)
                worst=max(abs(sum((F(f[i][k])*F(f[z][k]) for k in range(n)),F(0))-F(matrix[i][z])) for i in range(n) for z in range(n))
                self.assertLessEqual(worst,F(q['factorError']));self.assertLessEqual(F(q['factorError']),F(q['tolerance']))
