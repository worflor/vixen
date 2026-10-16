"""Exact binary64 PSD eligibility, separately bounded numerical factorization."""
import math,struct
from fractions import Fraction

def _bits(x):return int.from_bytes(struct.pack('>d',x),'big')
def _number(b):return struct.unpack('>d',b.to_bytes(8,'big'))[0]
def _floor_float(q,squared=False,bound=1.):
    if q<0 or q>(Fraction.from_float(bound)**2 if squared else Fraction.from_float(bound)):raise ValueError('Factor conversion outside its proved range')
    lo=0;hi=_bits(bound)
    while lo<hi:
        mid=(lo+hi+1)//2;v=Fraction.from_float(_number(mid))
        if squared:v*=v
        if v<=q:lo=mid
        else:hi=mid-1
    return _number(lo)
def exact_correlation_factor(matrix,tolerance=1e-10):
    if isinstance(tolerance,bool) or not isinstance(tolerance,(int,float)) or not math.isfinite(tolerance) or tolerance<=0:raise ValueError('Factor tolerance must be finite and positive')
    if not isinstance(matrix,(list,tuple)) or not matrix:raise ValueError('Correlation matrix must be nonempty')
    n=len(matrix);rows=[]
    for row in matrix:
        if not isinstance(row,(list,tuple)) or len(row)!=n:raise ValueError('Correlation matrix must be square')
        if any(isinstance(v,bool) or not isinstance(v,(int,float)) or not math.isfinite(v) or abs(v)>1 for v in row):raise ValueError('Correlation coefficients must be finite in [-1,1]')
        rows.append([float(v) for v in row])
    for i in range(n):
        if rows[i][i]!=1:raise ValueError('Correlation diagonal must be exactly one')
        for j in range(i):
            if rows[i][j]!=rows[j][i]:raise ValueError('Correlation matrix must be exactly symmetric')
    original=[[Fraction.from_float(v) for v in row] for row in rows];work=[row[:] for row in original];factor=[[0.]*n for _ in range(n)]
    for j in range(n):
        pivot=work[j][j]
        if pivot<0:raise ValueError('Correlation matrix is not positive semidefinite')
        if pivot==0:
            if any(work[i][j] for i in range(j+1,n)):raise ValueError('Nonzero column at a zero PSD pivot')
            continue
        factor[j][j]=_floor_float(pivot,True)
        for i in range(j+1,n):
            col=work[i][j];factor[i][j]=(-1 if col<0 else 1)*_floor_float(col*col/pivot,True)
        for i in range(j+1,n):
            for k in range(i,n):work[k][i]-=work[k][j]*work[i][j]/pivot
    numeric=[[Fraction.from_float(v) for v in row] for row in factor];worst=Fraction(0)
    for i in range(n):
        for j in range(i+1):
            covariance=sum((numeric[i][k]*numeric[j][k] for k in range(j+1)),Fraction(0));worst=max(worst,abs(covariance-original[i][j]))
    if worst>Fraction.from_float(float(tolerance)):raise ValueError('Numerical factor cannot meet requested tolerance')
    error=_floor_float(worst,False,2.)
    if Fraction.from_float(error)<worst:error=math.nextafter(error,math.inf)
    return {'matrix':rows,'factor':factor,'tolerance':float(tolerance),'factorError':error,'validation':'exact-binary64-ldlt'}
