"""Outward binary64 intervals and native Vixen leaf deviation enclosures.

Basic operations assume correctly rounded IEEE-754 binary64 arithmetic. Transcendentals
are enclosed with exact Fraction Taylor bounds; no libm rounding claim is made.
"""
from dataclasses import dataclass
from fractions import Fraction
import math

@dataclass(frozen=True)
class Interval:
    lo: float
    hi: float
    def __post_init__(self):
        if isinstance(self.lo,bool) or isinstance(self.hi,bool) or not isinstance(self.lo,(int,float)) or not isinstance(self.hi,(int,float)) or not math.isfinite(self.lo) or not math.isfinite(self.hi) or self.lo>self.hi:
            raise ValueError('A finite ordered interval is required')

def interval(lo,hi=None):
    return Interval(lo,lo if hi is None else hi)
def _iv(x):
    if not isinstance(x,Interval): raise TypeError('Expected an Interval')
    return x
def _down(x):
    if not math.isfinite(x): raise OverflowError('Unresolved: arithmetic overflow')
    y=math.nextafter(x,-math.inf)
    if not math.isfinite(y): raise OverflowError('Unresolved: no finite lower enclosure')
    return y
def _up(x):
    if not math.isfinite(x): raise OverflowError('Unresolved: arithmetic overflow')
    y=math.nextafter(x,math.inf)
    if not math.isfinite(y): raise OverflowError('Unresolved: no finite upper enclosure')
    return y
def _bounds(lo,hi):
    if not math.isfinite(lo) or not math.isfinite(hi): raise OverflowError('Unresolved: no finite binary64 enclosure')
    return Interval(lo,hi)
def add(a,b):
    _iv(a);_iv(b)
    if a.lo==a.hi==0:return b
    if b.lo==b.hi==0:return a
    lo=_down(a.lo+b.lo);hi=_up(a.hi+b.hi)
    if a.lo>=0 and b.lo>=0:lo=max(0,lo)
    if a.hi<=0 and b.hi<=0:hi=min(0,hi)
    return _bounds(lo,hi)
def subtract(a,b):
    _iv(a);_iv(b)
    if b.lo==b.hi==0:return a
    lo=_down(a.lo-b.hi);hi=_up(a.hi-b.lo)
    if a.lo>=b.hi:lo=max(0,lo)
    if a.hi<=b.lo:hi=min(0,hi)
    return _bounds(lo,hi)
def multiply(a,b):
    _iv(a);_iv(b)
    if a.lo==a.hi==0 or b.lo==b.hi==0:return interval(0)
    if a.lo==a.hi==1:return b
    if b.lo==b.hi==1:return a
    if a.lo==a.hi==-1:return interval(-b.hi,-b.lo)
    if b.lo==b.hi==-1:return interval(-a.hi,-a.lo)
    p=(a.lo*b.lo,a.lo*b.hi,a.hi*b.lo,a.hi*b.hi)
    if any(not math.isfinite(x) for x in p):raise OverflowError('Unresolved: multiplication overflow')
    lo=_down(min(p));hi=_up(max(p))
    if (a.lo>=0 and b.lo>=0) or (a.hi<=0 and b.hi<=0):lo=max(0,lo)
    if (a.lo>=0 and b.hi<=0) or (a.hi<=0 and b.lo>=0):hi=min(0,hi)
    return _bounds(lo,hi)
def divide(a,b):
    _iv(a);_iv(b)
    if b.lo<=0<=b.hi:raise ZeroDivisionError('Division interval contains zero')
    if a.lo==a.hi==0:return interval(0)
    if b.lo==b.hi==1:return a
    p=(a.lo/b.lo,a.lo/b.hi,a.hi/b.lo,a.hi/b.hi)
    if any(not math.isfinite(x) for x in p):raise OverflowError('Unresolved: division overflow')
    lo=_down(min(p));hi=_up(max(p))
    if (a.lo>=0 and b.lo>0) or (a.hi<=0 and b.hi<0):lo=max(0,lo)
    if (a.lo>=0 and b.hi<0) or (a.hi<=0 and b.lo>0):hi=min(0,hi)
    return _bounds(lo,hi)
def hull(a,b):
    _iv(a);_iv(b);return Interval(min(a.lo,b.lo),max(a.hi,b.hi))
def _smooth_point(x):
    if x<=0:return interval(0)
    if x>=1:return interval(1)
    t=interval(x);t2=multiply(t,t);t3=multiply(t2,t)
    r=multiply(t3,add(interval(10),multiply(t,add(interval(-15),multiply(interval(6),t)))))
    return Interval(max(0,r.lo),r.hi)
_POLYNOMIAL_ROUND_ERROR=512*2.0**-52
def smooth5(a):
    _iv(a)
    if a.hi<=0:return interval(0)
    if a.lo>=1:return interval(1)
    if a.lo==a.hi:return _smooth_point(a.lo)
    return Interval(max(0,_down(_smooth_point(a.lo).lo-_POLYNOMIAL_ROUND_ERROR)),_up(_smooth_point(a.hi).hi+_POLYNOMIAL_ROUND_ERROR))
def _ramp_point(x):
    if x<=0:return interval(0)
    if x>=1:return interval(x)
    t=interval(x);t2=multiply(t,t);t3=multiply(t2,t)
    return multiply(t3,add(interval(6),multiply(t,add(interval(-8),multiply(interval(3),t)))))
def boundaryRamp(a):
    _iv(a)
    if a.hi<=0:return interval(0)
    if a.lo>=1:return a
    if a.lo==a.hi:return _ramp_point(a.lo)
    return Interval(max(0,_down(_ramp_point(a.lo).lo-_POLYNOMIAL_ROUND_ERROR)),_up(_ramp_point(a.hi).hi+_POLYNOMIAL_ROUND_ERROR))

def _rational_float_bounds(q):
    f=float(q)
    fq=Fraction.from_float(f)
    if fq==q:return interval(f)
    if fq<q:return interval(f,math.nextafter(f,math.inf))
    return interval(math.nextafter(f,-math.inf),f)
_EXP_FIXED=1<<192
_EXP_FACTORIAL=math.factorial(65)
def _ceil_div(n,d):return (n+d-1)//d
def _reduced_exp_base(a,n):
    source=Fraction.from_float(a);den=source.denominator*(1<<n);num=source.numerator*_EXP_FIXED
    zlo,zhi=num//den,_ceil_div(num,den)
    tlo=thi=slo=shi=_EXP_FIXED
    for k in range(1,65):
        d=_EXP_FIXED*k;tlo=tlo*zlo//d;thi=_ceil_div(thi*zhi,d);slo+=tlo;shi+=thi
    shi+=_ceil_div(3*_EXP_FIXED,_EXP_FACTORIAL)
    return interval(_rational_float_bounds(Fraction(slo,_EXP_FIXED)).lo,_rational_float_bounds(Fraction(shi,_EXP_FIXED)).hi)
def _square_positive(q):
    r=multiply(q,q);return Interval(max(0,r.lo),r.hi)
def _exp_point(x):
    if x==0:return interval(1)
    negative=x<0;a=abs(x);z=a;n=0
    while z>1:z*=0.5;n+=1
    q=_reduced_exp_base(a,n)
    if negative:q=divide(interval(1),q)
    for _ in range(n):q=_square_positive(q)
    return Interval(max(0,q.lo),q.hi)
def exp(a):
    _iv(a);return Interval(_exp_point(a.lo).lo,_exp_point(a.hi).hi)
def _tanh_point(x):
    if x==0:return interval(0)
    if x>sys_float_max()/2:return interval(math.nextafter(1,-math.inf),1)
    if x< -sys_float_max()/2:return interval(-1,math.nextafter(-1,math.inf))
    q=_exp_point(-2*abs(x))
    r=divide(subtract(interval(1),q),add(interval(1),q))
    r=Interval(max(0,r.lo),min(1,r.hi))
    return Interval(-r.hi,-r.lo) if x<0 else r
def sys_float_max():return float.fromhex('0x1.fffffffffffffp+1023')
def tanh(a):
    _iv(a);return Interval(_tanh_point(a.lo).lo,_tanh_point(a.hi).hi)

_MASK=0xffffffff
def _seed32(seed):
    if isinstance(seed,bool):raise TypeError('Invalid seed')
    if isinstance(seed,(int,float)):
        if not math.isfinite(seed) or seed!=int(seed) or not 0<=seed<=_MASK:raise ValueError('Invalid numeric seed')
        return int(seed)
    if not isinstance(seed,str) or len(seed.encode('utf-16-le','surrogatepass'))>2048:raise ValueError('Invalid text seed')
    raw=seed.encode('utf-16-le','surrogatepass').decode('utf-16-le','replace').encode('utf-8')
    h=2166136261
    for b in raw:h=((h^b)*16777619)&_MASK
    return h
def _hash32(seed,index,channel):
    h=(seed^((index&_MASK)*0x9e3779b9&_MASK)^((channel&_MASK)*0x85ebca6b&_MASK))&_MASK
    h=((h^(h>>16))*0x7feb352d)&_MASK;h=((h^(h>>15))*0x846ca68b)&_MASK
    return (h^(h>>16))&_MASK
def _unit(seed,k,ch):return _hash32(seed,k,ch)/4294967296

def _check_leaf(leaf):
    if not all(hasattr(leaf,k) for k in ('config','dips','domain','sample','at','ridges')):raise TypeError('A native leaf facade is required')
    if not callable(leaf.sample) or not callable(leaf.at) or not callable(leaf.ridges):raise TypeError('A native leaf facade is required')
    c=leaf.config;_seed32(c['seed'])
    for k,lo,hi in (('openingEndpoint',2,50000),('introVariation',0,1),('boundarySoftness',0,1),('stabilizer',0,1),('strength',0,2),('ramp',1,64),('tempo',.25,4),('contrast',0,16)):
        x=c[k]
        if isinstance(x,bool) or not isinstance(x,(int,float)) or not math.isfinite(x) or not lo<=x<=hi:raise ValueError('Invalid native config field: '+k)
    if c['floor'] not in ('none','zero','baseline','backbone'):raise ValueError('Invalid native floor')
    m=c['ceilingMargin']
    if m is not None and (isinstance(m,bool) or not isinstance(m,(int,float)) or not math.isfinite(m) or not 0<=m<=16):raise ValueError('Invalid native ceiling')
    for k in ('latent','velocity'):
        v=c[k]
        if len(v)!=16 or any(isinstance(x,bool) or not isinstance(x,(int,float)) or not math.isfinite(x) for x in v):raise ValueError('Invalid native vector')
    if len(leaf.domain)!=2 or leaf.domain[0]!=1 or any(not math.isfinite(x) for x in leaf.domain) or not leaf.domain[0]<=leaf.domain[1]<=50000:raise ValueError('Invalid native domain')
    if len(leaf.dips)!=2 or any(not math.isfinite(d['center']) or not math.isfinite(d['width']) or d['width']<=0 or not math.isfinite(d['depth']) or d['depth']<=0 for d in leaf.dips):raise ValueError('Invalid native dips')
    rs=leaf.ridges()
    if len(rs)!=48:raise ValueError('A native leaf must expose all 48 stored ridges')
    for i,q in enumerate(rs):
        if q['index']!=i or q['channel']!=i+200 or len(q['direction'])!=16 or any(not math.isfinite(x) for x in q['direction']):raise ValueError('Invalid native ridge')
        for k in ('scale','phase','basePhase','slope','guidePhase','guideSlope'):
            if not math.isfinite(q[k]):raise ValueError('Invalid native ridge')
        if q['scale']<=0:raise ValueError('Invalid native ridge scale')
    return c,rs,_seed32(c['seed'])
def _noise(p,seed,ch):
    if math.floor(p.hi)-math.floor(p.lo)>1:return interval(-1-2048*2.0**-52,1+2048*2.0**-52)
    out=None
    for k in range(math.floor(p.lo),math.floor(p.hi)+1):
        a=max(p.lo,k);b=min(p.hi,k+1)
        if a>b:continue
        t=interval(a-k,b-k);v0=2*_unit(seed,k,ch)-1;v1=2*_unit(seed,k+1,ch)-1
        q=add(interval(v0),multiply(subtract(interval(v1),interval(v0)),smooth5(t)))
        out=q if out is None else hull(out,q)
    return out if out is not None else interval(-1-2048*2.0**-52,1+2048*2.0**-52)
def _scores(c,rs,seed,x,coordinates):
    if c['openingEndpoint']==100:clock=x
    else:clock=add(interval(1),divide(multiply(subtract(x,interval(1)),interval(99)),interval(c['openingEndpoint']-1)))
    u=subtract(clock,interval(100));score=interval(0);guide=interval(0)
    for q in rs:
        if coordinates is None:
            p=add(interval(q['phase']),multiply(u,interval(q['slope'])))
            g=add(interval(q['guidePhase']),multiply(u,interval(q['guideSlope'])))
        else:
            projection=interval(0)
            for d,h in zip(q['direction'],coordinates):projection=add(projection,multiply(interval(d),interval(h)))
            p=add(add(interval(q['basePhase']),projection),divide(u,interval(q['scale'])))
            g=add(add(interval(q['basePhase']),divide(projection,interval(32))),divide(u,interval(32*q['scale'])))
        score=add(score,divide(_noise(p,seed,q['channel']),interval(48)))
        guide=add(guide,divide(_noise(g,seed,q['channel']),interval(48)))
    return score,guide
def _point_floor(y,lo,e):
    if e==0:return interval(max(y,lo))
    if y<=lo:return interval(lo)
    if math.isfinite(lo+e) and y>=math.nextafter(lo+e,math.inf):return interval(y)
    return add(interval(lo),multiply(interval(e),boundaryRamp(divide(subtract(interval(y),interval(lo)),interval(e)))))
def _point_ceil(y,hi,e):
    if e==0:return interval(min(y,hi))
    if y>=hi:return interval(hi)
    if math.isfinite(hi-e) and y<=math.nextafter(hi-e,-math.inf):return interval(y)
    return subtract(interval(hi),multiply(interval(e),boundaryRamp(divide(subtract(interval(hi),interval(y)),interval(e)))))
def _ramp_range(t):
    return multiply(multiply(multiply(t,t),t),add(interval(6),multiply(t,add(interval(-8),multiply(interval(3),t)))))
def _floor_map(y,lo,e):
    if e.lo<0:raise ValueError('A nonnegative boundary width is required')
    pieces=[]
    if e.lo==0:pieces.append(Interval(max(y.lo,lo.lo),max(y.hi,lo.hi)))
    if y.lo<=lo.hi:pieces.append(lo)
    if y.hi>=add(lo,e).lo:pieces.append(y)
    if e.hi>0 and y.hi>lo.lo:
        ratio=interval(0,1) if e.lo==0 else divide(subtract(y,lo),e)
        a=max(0,ratio.lo);b=min(1,ratio.hi)
        if a<=b:pieces.append(add(lo,multiply(e,_ramp_range(Interval(a,b)))))
    if not pieces:raise ValueError('Unresolved boundary branches')
    out=pieces[0]
    for piece in pieces[1:]:out=hull(out,piece)
    return out
def _ceil_map(y,hi,e):
    q=_floor_map(Interval(-y.hi,-y.lo),Interval(-hi.hi,-hi.lo),e)
    return Interval(-q.hi,-q.lo)
def _width(lo,hi,a):
    if lo is None and hi is None:return interval(0)
    if lo is None or hi is None:return a
    gap=subtract(hi,lo)
    if gap.hi<=0 or a.hi==0:return interval(0)
    def f(g,z):return interval(0) if g==0 or z==0 else divide(multiply(interval(z),interval(g)),add(interval(g),multiply(interval(2),interval(z))))
    low=f(gap.lo,a.lo).lo if gap.lo>0 and a.lo>0 else 0
    high=f(max(0,gap.hi),a.hi).hi
    return Interval(max(0,low),max(0,high))
def _apply(y,ug,c,gate):
    floor=None if c['floor']=='none' else interval(0) if c['floor']=='zero' else interval(1) if c['floor']=='baseline' else add(interval(1),ug)
    ceiling=None if c['ceilingMargin'] is None else add(interval(1) if c['floor']=='baseline' else add(interval(1),ug),interval(c['ceilingMargin']))
    a=multiply(multiply(interval(c['strength']),gate),interval(c['boundarySoftness']));e=_width(floor,ceiling,a)
    if floor is not None and ceiling is not None and ceiling.hi<=floor.lo:return subtract(floor,interval(1))
    z=_floor_map(y,floor,e) if floor is not None else y
    if ceiling is not None:z=_ceil_map(z,ceiling,e)
    if floor is not None and ceiling is not None and ceiling.lo<=floor.hi:z=hull(z,floor)
    return subtract(z,interval(1))
def _baseline_interval(c,x):
    t=x if c['openingEndpoint']==100 else add(interval(1),divide(multiply(subtract(x,interval(1)),interval(99)),interval(c['openingEndpoint']-1)))
    def gaussian(center,width,depth):
        q=subtract(t,interval(center))
        return multiply(interval(depth),exp(multiply(interval(-1),divide(multiply(q,q),interval(width)))))
    return subtract(subtract(multiply(t,exp(divide(t,interval(100)))),gaussian(42,32,25)),gaussian(71,28,38))
def _raw_state(c,rs,seed,x,h):
    score,guide=_scores(c,rs,seed,x,h)
    gate=smooth5(divide(subtract(x,interval(c['openingEndpoint'])),interval(c['ramp'])))
    raw=multiply(multiply(multiply(interval(c['strength']),gate),interval(2.2)),tanh(multiply(interval(c['contrast']),score)))
    ug=multiply(multiply(multiply(interval(c['strength']),gate),interval(.25)),tanh(multiply(interval(2),guide)))
    w=smooth5(interval(c['stabilizer']));u=raw if c['stabilizer']==0 else ug if c['stabilizer']==1 else add(raw,multiply(subtract(ug,raw),w))
    base=_baseline_interval(c,x);unconstrainedValue=add(base,multiply(base,u));unconstrainedGuideValue=add(base,multiply(base,ug))
    lo=None if c['floor']=='none' else interval(0) if c['floor']=='zero' else base if c['floor']=='baseline' else unconstrainedGuideValue
    hi=None if c['ceilingMargin'] is None else add(base if c['floor']=='baseline' else unconstrainedGuideValue,multiply(interval(c['ceilingMargin']),base))
    amplitude=multiply(multiply(interval(c['strength']),gate),interval(c['boundarySoftness']))
    if lo is not None and hi is not None:
        if lo is hi or amplitude.hi==0:e=interval(0)
        else:
            gap=divide(subtract(hi,lo),base)
            if gap.hi<=0:e=interval(0)
            elif gap.lo>0 and amplitude.lo>0:e=multiply(base,divide(multiply(amplitude,gap),add(gap,multiply(interval(2),amplitude))))
            else:
                cap=min(multiply(interval(2),interval(amplitude.hi)).hi,multiply(interval(2),interval(max(0,gap.hi))).hi)
                e=multiply(base,interval(0,cap))
    else:e=multiply(base,amplitude) if lo is not None or hi is not None else interval(0)
    return dict(base=base,unconstrainedValue=unconstrainedValue,unconstrainedGuideValue=unconstrainedGuideValue,floorValue=lo,ceilingValue=hi,boundaryWidth=e,u=u)
def projectConstraintState(s):
    lo,hi,e=s['floorValue'],s['ceilingValue'],s['boundaryWidth']
    if lo is not None and hi is not None and hi.hi<=lo.lo:return lo
    value=_floor_map(s['unconstrainedValue'],lo,e) if lo is not None else s['unconstrainedValue']
    if hi is not None:value=_ceil_map(value,hi,e)
    if lo is not None and hi is not None and hi.lo<=lo.hi:value=hull(value,lo)
    return value
def constraintDeviation(s):return divide(subtract(projectConstraintState(s),s['base']),s['base'])
def _checked_coordinates(h):
    if h is None:return None
    if not isinstance(h,(list,tuple)) or len(h)!=16 or any(isinstance(x,bool) or not isinstance(x,(int,float)) or not math.isfinite(x) or abs(x)>1e6 for x in h):raise ValueError('Coordinates must contain16 finite values in [-1000000,1000000]')
    return tuple(h)
def nativeConstraintState(nativeLeaf,lo,hi,coordinates=None):
    c,rs,seed=_check_leaf(nativeLeaf);h=_checked_coordinates(coordinates)
    if any(isinstance(v,bool) or not isinstance(v,(int,float)) or not math.isfinite(v) for v in (lo,hi)) or lo>hi or lo<max(c['openingEndpoint'],nativeLeaf.domain[0]) or hi>nativeLeaf.domain[1]:raise ValueError('Raw state interval must lie after the opening inside its native domain')
    return _raw_state(c,rs,seed,interval(lo,hi),h)
def nativeDeviationEnclosure(nativeLeaf,lo,hi,coordinates=None):
    if isinstance(lo,bool) or isinstance(hi,bool) or not isinstance(lo,(int,float)) or not isinstance(hi,(int,float)) or not math.isfinite(lo) or not math.isfinite(hi) or lo>hi:raise ValueError('Finite ordered x bounds are required')
    c,rs,seed=_check_leaf(nativeLeaf);h=_checked_coordinates(coordinates);domain=nativeLeaf.domain
    if lo<domain[0] or hi>domain[1]:raise ValueError('x interval is outside the native domain')
    if hi<=c['openingEndpoint']:return interval(0)
    state=_raw_state(c,rs,seed,interval(max(lo,c['openingEndpoint']),hi),h)
    result=state['u'] if c['floor']=='none' and c['ceilingMargin'] is None else constraintDeviation(state)
    return hull(result,interval(0)) if lo<=c['openingEndpoint'] else result

def mixConstraintStates(a,b,weight,coherent=True):
    out={}
    for key in ('base','unconstrainedValue','unconstrainedGuideValue','boundaryWidth','floorValue','ceilingValue'):
        if a[key] is None and b[key] is None:out[key]=None
        else:
            if a[key] is None or b[key] is None:raise ValueError('Incompatible constraint states')
            out[key]=add(a[key],multiply(subtract(b[key],a[key]),weight))
    if not coherent:
        av=a['value'] if 'value' in a else projectConstraintState(a);bv=b['value'] if 'value' in b else projectConstraintState(b)
        value=add(av,multiply(subtract(bv,av),weight))
        if out['floorValue'] is not None:value=Interval(max(value.lo,out['floorValue'].lo),max(value.hi,out['floorValue'].hi))
        if out['ceilingValue'] is not None:value=Interval(min(value.lo,out['ceilingValue'].lo),min(value.hi,out['ceilingValue'].hi))
        out['value']=value
    return out
def appliedDeviation(s):return divide(subtract(s['value'] if 'value' in s else projectConstraintState(s),s['base']),s['base'])
