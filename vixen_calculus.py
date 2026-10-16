"""Second-order calculus for native Vixen leaves; standard library only."""
import math
import sys
from types import MappingProxyType
from vixen_float import sum_products

class Jet:
    __slots__=('value','d1','d2')
    def __init__(self,value,d1=0.0,d2=0.0): self.value,self.d1,self.d2=float(value),float(d1),float(d2)
    def __add__(self,b):
        b=as_jet(b);return Jet(self.value+b.value,self.d1+b.d1,self.d2+b.d2)
    __radd__=__add__
    def __neg__(self): return Jet(-self.value,-self.d1,-self.d2)
    def __sub__(self,b): return self+-as_jet(b)
    def __rsub__(self,b): return as_jet(b)+-self
    def __mul__(self,b):
        b=as_jet(b)
        d1=self.d1*b.value+self.value*b.d1
        d2=self.d2*b.value+2*self.d1*b.d1+self.value*b.d2
        if all(math.isfinite(v) for v in (self.value,self.d1,self.d2,b.value,b.d1,b.d2)):
            if not math.isfinite(d1):d1=sum_products([[self.d1,b.value],[self.value,b.d1]])
            if not math.isfinite(d2):d2=sum_products([[self.d2,b.value],[2,self.d1,b.d1],[self.value,b.d2]])
        return Jet(self.value*b.value,d1,d2)
    __rmul__=__mul__
    def __truediv__(self,b):
        b=as_jet(b)
        value=self.value/b.value
        d1=(self.d1-value*b.d1)/b.value
        d2=(self.d2-value*b.d2-2*d1*b.d1)/b.value
        if b.value!=0 and all(math.isfinite(v) for v in (self.value,self.d1,self.d2,b.value,b.d1,b.d2)):
            if not math.isfinite(d1):d1=sum_products([[self.d1,b.value],[-1,self.value,b.d1]],[b.value,b.value])
            if not math.isfinite(d2):d2=sum_products([[self.d2,b.value,b.value],[-1,self.value,b.d2,b.value],[-2,self.d1,b.value,b.d1],[2,self.value,b.d1,b.d1]],[b.value,b.value,b.value])
        return Jet(value,d1,d2)
    def __rtruediv__(self,b): return as_jet(b)/self

def as_jet(x): return x if isinstance(x,Jet) else Jet(x)
def jet(value,d1=0.0,d2=0.0): return Jet(value,d1,d2)
def jet_add(a,b): return as_jet(a)+b
def jet_sub(a,b): return as_jet(a)-b
def jet_mul(a,b): return as_jet(a)*b
def jet_div(a,b): return as_jet(a)/b
def _unary(a,value,first,second): return Jet(value,first*a.d1,first*a.d2+second*a.d1*a.d1)
def jet_exp(a):
    a=as_jet(a);v=math.exp(a.value);return _unary(a,v,v,v)
def jet_log(a):
    a=as_jet(a);d1=a.d1/a.value;d2=a.d2/a.value-d1*d1
    if not math.isfinite(d2) and a.value>0 and all(math.isfinite(v) for v in (a.value,a.d1,a.d2)):
        d2=sum_products([[a.d2,a.value],[-1,a.d1,a.d1]],[a.value,a.value])
    return Jet(math.log(a.value),d1,d2)
def jet_tanh(a):
    a=as_jet(a);v=math.tanh(a.value);z=math.exp(-2*abs(a.value));p=4*z/((1+z)*(1+z));return _unary(a,v,p,-2*v*p)
def jet_smooth5(a):
    a=as_jet(a);t=a.value
    if t<=0:return Jet(0)
    if t>=1:return Jet(1)
    return _unary(a,t**3*(10+t*(-15+6*t)),30*t*t*(t-1)**2,60*t*(t-1)*(2*t-1))
def jet_boundary_ramp(a):
    a=as_jet(a);t=a.value
    if t<=0:return Jet(0)
    if t>=1:return Jet(t,a.d1,a.d2)
    return _unary(a,t**3*(6+t*(-8+3*t)),18*t*t-32*t**3+15*t**4,36*t-96*t*t+60*t**3)

_MASK=0xffffffff
_MAX_X=2**40
_MAX_LOG=math.log(sys.float_info.max)
_MIN_LOG=math.log(float.fromhex('0x0.0000000000001p-1022'))
def _seed32(seed):
    if isinstance(seed,bool): raise TypeError('Invalid seed')
    if isinstance(seed,(int,float)):
        if not math.isfinite(seed) or int(seed)!=seed or not 0<=seed<=_MASK: raise ValueError('Invalid numeric seed')
        return int(seed)
    if not isinstance(seed,str) or len(seed.encode('utf-16-le','surrogatepass'))>2048: raise TypeError('Invalid seed')
    raw=seed.encode('utf-16-le','surrogatepass').decode('utf-16-le','replace').encode('utf-8');h=2166136261
    for b in raw:h=((h^b)*16777619)&_MASK
    return h
def _hash32(seed,index,channel):
    h=(seed^((int(index)&_MASK)*0x9e3779b9&_MASK)^((int(channel)&_MASK)*0x85ebca6b&_MASK))&_MASK
    h=((h^(h>>16))*0x7feb352d)&_MASK;h=((h^(h>>15))*0x846ca68b)&_MASK
    return (h^(h>>16))&_MASK
def _unit(s,k,c): return _hash32(s,k,c)/4294967296
def _vector(v):
    if not isinstance(v,(list,tuple)) or len(v)!=16 or any(isinstance(n,bool) or not isinstance(n,(int,float)) or not math.isfinite(n) or abs(n)>1e6 for n in v): raise ValueError('Coordinates must be 16 finite numbers in [-1000000,1000000]')
    return tuple(v)
def _native(curve):
    if not all(hasattr(curve,k) for k in ('config','dips','sample','at','ridges')): raise TypeError('A native leaf-like object is required')
    if callable(getattr(curve,'fork',None)) or hasattr(curve,'lineage'): raise TypeError('Pass a native leaf, not a forked timeline')
    c=curve.config;r=curve.ridges()
    if len(r)!=48: raise TypeError('A native leaf must expose its 48 ridges')
    for q in r:
        _vector(q['direction'])
        for k in ('scale','phase','basePhase','slope','guidePhase','guideSlope','channel','index'):
            if isinstance(q[k],bool) or not isinstance(q[k],(int,float)) or not math.isfinite(q[k]): raise ValueError('Invalid ridge data')
        if q['scale']<=0: raise ValueError('Invalid ridge scale')
    _seed32(c['seed']);return c,r
def _noise(p,s,ch):
    k=math.floor(p.value);t=p-k;a=2*_unit(s,k,ch)-1;b=2*_unit(s,k+1,ch)-1
    return a+(b-a)*jet_smooth5(t)
def _smooth(t): return 0.0 if t<=0 else 1.0 if t>=1 else t**3*(10+t*(-15+6*t))
def _authored(t,c,dips):
    old=t*jet_exp(t/100)-25*jet_exp(-((t-42)*(t-42))/32)-38*jet_exp(-((t-71)*(t-71))/28)
    if c['introVariation']==0 or t.value>=100:return old
    fresh=t*jet_exp(t/100)
    for d in dips:fresh=fresh-d['depth']*jet_exp(-((t-d['center'])/d['width'])*((t-d['center'])/d['width']))
    return old+(fresh-old)*(1-jet_smooth5((t-90)/10))
def _hard_contact(y,bound,lower):
    z=y-bound
    if z.d1!=0:return bound,True
    inside=z.d2>0 if lower else z.d2<0
    return (y if inside else bound),False
def _map_floor(y,lo,e):
    if e.value==0 and y.value==lo.value:return _hard_contact(y,lo,True)
    if y.value<=lo.value:return lo,False
    if e.value==0 or y.value>=lo.value+e.value:return y,False
    return lo+e*jet_boundary_ramp((y-lo)/e),False
def _map_ceil(y,hi,e):
    if e.value==0 and y.value==hi.value:return _hard_contact(y,hi,False)
    if y.value>=hi.value:return hi,False
    if e.value==0 or y.value<=hi.value-e.value:return y,False
    return hi-e*jet_boundary_ramp((hi-y)/e),False
def _apply(y,lo,hi,e):
    if lo is not None and hi is not None and hi.value<=lo.value:return lo,False
    z,hard=(y,False) if lo is None else _map_floor(y,lo,e)
    if hi is not None:z,h=_map_ceil(z,hi,e);hard=hard or h
    return z,hard
def _finite_jet(q): return all(math.isfinite(v) for v in (q.value,q.d1,q.d2))

def leaf_jet(curve,x,coordinates=None):
    c,r=_native(curve)
    if isinstance(x,bool) or not isinstance(x,(int,float)) or not math.isfinite(x): raise ValueError('x must be finite')
    h=None if coordinates is None else _vector(coordinates);sample=curve.sample(x) if h is None else curve.at(x,h);X=Jet(x,1,0);endpoint=c['openingEndpoint'];score=Jet(0);guide=Jet(0);score_jump=x==endpoint
    if x>endpoint:
        clock=X if endpoint==100 else 1+(X-1)*(99/(endpoint-1));u=clock-100;s=_seed32(c['seed'])
        for q in r:
            if h is None:p=q['phase']+u*q['slope'];g=q['guidePhase']+u*q['guideSlope']
            else:
                projection=sum(a*b for a,b in zip(q['direction'],h));p=q['basePhase']+projection+u/q['scale'];g=q['basePhase']+projection/32+u/(32*q['scale'])
            score=score+_noise(p,s,q['channel'])/48;guide=guide+_noise(g,s,q['channel'])/48
    gate=jet_smooth5((X-endpoint)/c['ramp']);raw=c['strength']*gate*2.2*jet_tanh(c['contrast']*score);ug=c['strength']*gate*.25*jet_tanh(2*guide);w=_smooth(c['stabilizer']);u=raw if w==0 else ug if w==1 else raw+(ug-raw)*w
    t=X if endpoint==100 else 1+(X-1)*(99/(endpoint-1));base=_authored(t,c,curve.dips);uv=base+base*u;ugv=base+base*ug
    lo=None if c['floor']=='none' else Jet(0) if c['floor']=='zero' else base if c['floor']=='baseline' else ugv
    hi=None if c['ceilingMargin'] is None else (base if c['floor']=='baseline' else ugv)+c['ceilingMargin']*base
    a=c['strength']*gate*c['boundarySoftness'];e=Jet(0)
    if lo is not None and hi is not None:
        gap=(hi-lo)/base;e=Jet(0) if gap.value<=0 or a.value==0 else base*a*gap/(gap+2*a)
    elif lo is not None or hi is not None:e=base*a
    value,hard=_apply(uv,lo,hi,e);guide_value,guide_hard=_apply(ugv,lo,hi,e);constrained=c['floor']!='none' or c['ceilingMargin'] is not None
    d=value/base-1 if constrained else u;gd=guide_value/base-1 if constrained else ug;pos=jet_exp(.55*jet_tanh(d))
    jets={'x':X,'value':value,'base':base,'score':score,'guideScore':guide,'rawDeviation':raw,'guideDeviation':gd,'guideValue':guide_value,'stabilizationWeight':Jet(w),'deviation':d,'multiplier':1+d,'activity':.5+.5*jet_tanh(d),'positiveMultiplier':pos,'positiveValue':base*pos,'gate':gate,'unconstrainedValue':uv,'unconstrainedDeviation':u,'unconstrainedGuideValue':ugv,'unconstrainedGuideDeviation':ug,'floorValue':lo,'ceilingValue':hi,'boundaryWidth':e}
    bad=set()
    if hard:bad.update(('value','deviation','multiplier','activity','positiveMultiplier','positiveValue'))
    if guide_hard:bad.update(('guideValue','guideDeviation'))
    if score_jump:bad.update(('score','guideScore'))
    fields={}
    for key,val in sample.items():
        if isinstance(val,bool) or not isinstance(val,(int,float)):continue
        q=jets.get(key);ok=q is not None and key not in bad
        fields[key]=MappingProxyType({'value':val,'d1':q.d1 if ok and math.isfinite(q.d1) else None,'d2':q.d2 if ok and math.isfinite(q.d2) else None})
    q=jets['value'];ok=not hard and _finite_jet(q);reason='A zero-width constraint is evaluated at its hard boundary.' if hard else 'The score branch changes at the opening endpoint; score field derivatives are undefined there.' if score_jump else None
    first,second=fields['value']['d1'] is not None,fields['value']['d2'] is not None
    return MappingProxyType({'sample':sample,'slope':q.d1 if first else None,'curvature':q.d2 if second else None,'fields':MappingProxyType(fields),'availability':MappingProxyType({'slope':first,'curvature':second}),'differentiability':MappingProxyType({'value':first and second,'sample':all(f['d1'] is not None and f['d2'] is not None for f in fields.values()),'rationale':reason})})

def _raw(log_base,factor):
    if factor==0:return 0.0
    z=log_base+math.log(abs(factor))
    if not math.isfinite(z) or z>_MAX_LOG or z<_MIN_LOG:return None
    try:v=math.exp(z)
    except OverflowError:return None
    return math.copysign(v,factor) if v and math.isfinite(v) else None
def _signed(log_base,factor): return MappingProxyType({'sign':0,'logAbs':None}) if factor==0 else MappingProxyType({'sign':-1 if factor<0 else 1,'logAbs':log_base+math.log(abs(factor))})
def _log_base(t,c,dips):
    terms=[]
    def dip(depth,center,width,weight):
        if not weight:return
        z=(t-center)/width;v=math.log(weight)+math.log(depth)-math.log(t)-t/100-z*z
        terms.append(0.0 if v < -745 else math.exp(v))
    def old():dip(25,42,math.sqrt(32),1);dip(38,71,math.sqrt(28),1)
    if c['introVariation']>0 and t<100:
        w=1-_smooth((t-90)/10)
        for d,od in zip(dips,((25,42,math.sqrt(32)),(38,71,math.sqrt(28)))):
            dip(*od,1-w);dip(d['depth'],d['center'],d['width'],w)
    else:old()
    correction=sum(terms)
    if not correction<1:raise ValueError('Authored baseline is not positive')
    return math.log(t)+t/100+math.log1p(-correction)
def _noise_number(p,s,ch):
    k=math.floor(p);t=p-k;a=2*_unit(s,k,ch)-1;b=2*_unit(s,k+1,ch)-1
    return a+(b-a)*t**3*(10+t*(-15+6*t))
def _ramp(t): return 0.0 if t<=0 else t if t>=1 else t**3*(6+t*(-8+3*t))
def _floor(y,lo,e): return lo if y<=lo else y if e==0 or y>=lo+e else lo+e*_ramp((y-lo)/e)
def _ceil(y,hi,e): return hi if y>=hi else y if e==0 or y<=hi-e else hi-e*_ramp((hi-y)/e)
def native_normalized(curve,x,h=None):
    c,r=_native(curve)
    if isinstance(x,bool) or not isinstance(x,(int,float)) or not math.isfinite(x) or not 1<=x<=_MAX_X:raise ValueError('x must lie in [1,2^40]')
    h=None if h is None else _vector(h);domain=getattr(curve,'domain',None)
    if domain is not None and x<=domain[1]:
        p=curve.sample(x) if h is None else curve.at(x,h)
        if any(not math.isfinite(v) for v in p.values() if isinstance(v,(int,float)) and not isinstance(v,bool)):raise ValueError('Nonfinite native sample data')
        value=p['value'];return MappingProxyType({**p,'logBase':math.log(p['base']),'signedLogValue':MappingProxyType({'sign':0,'logAbs':None} if value==0 else {'sign':-1 if value<0 else 1,'logAbs':math.log(abs(value))})})
    t=x if c['openingEndpoint']==100 else 1+(x-1)*99/(c['openingEndpoint']-1);L=_log_base(t,c,curve.dips);s=_seed32(c['seed']);score=guide=0.0
    if x>c['openingEndpoint']:
        u=t-100
        for q in r:
            if h is None:p=q['phase']+u*q['slope'];g=q['guidePhase']+u*q['guideSlope']
            else:
                projection=sum(a*b for a,b in zip(q['direction'],h));p=q['basePhase']+projection+u/q['scale'];g=q['basePhase']+projection/32+u/(32*q['scale'])
            score+=_noise_number(p,s,q['channel'])/48;guide+=_noise_number(g,s,q['channel'])/48
    gate=_smooth((x-c['openingEndpoint'])/c['ramp']);raw=c['strength']*gate*2.2*math.tanh(c['contrast']*score);ug=c['strength']*gate*.25*math.tanh(2*guide);w=_smooth(c['stabilizer']);u=raw if w==0 else ug if w==1 else raw+(ug-raw)*w
    lo=None if c['floor']=='none' else 0.0 if c['floor']=='zero' else 1.0 if c['floor']=='baseline' else 1+ug
    hi=None if c['ceilingMargin'] is None else (1.0 if c['floor']=='baseline' else 1+ug)+c['ceilingMargin'];a=c['strength']*gate*c['boundarySoftness'];gap=None if lo is None or hi is None else hi-lo
    e=(0.0 if gap<=0 or a==0 else a*gap/(gap+2*a)) if gap is not None else 0.0 if lo is None and hi is None else a
    def apply(y):
        if lo is not None and hi is not None and hi<=lo:return lo
        z=y if lo is None else _floor(y,lo,e)
        return z if hi is None else _ceil(z,hi,e)
    un=1+u;ung=1+ug;vf=apply(un);gf=apply(ung);d=vf-1;gd=gf-1;pos=math.exp(.55*math.tanh(d));width=e
    return MappingProxyType({'x':x,'logBase':L,'score':score,'guideScore':guide,'gate':gate,'rawDeviation':raw,'unconstrainedDeviation':u,'unconstrainedGuideDeviation':ug,'deviation':d,'guideDeviation':gd,'multiplier':1+d,'activity':.5+.5*math.tanh(d),'positiveMultiplier':pos,'signedLogValue':_signed(L,vf),'value':_raw(L,vf),'base':_raw(L,1),'guideValue':_raw(L,gf),'unconstrainedValue':_raw(L,un),'unconstrainedGuideValue':_raw(L,ung),'positiveValue':_raw(L,pos),'stabilizationWeight':w,'floorRatio':lo,'ceilingRatio':hi,'floorValue':None if lo is None else _raw(L,lo),'ceilingValue':None if hi is None else _raw(L,hi),'boundaryWidthRatio':width,'boundaryWidth':_raw(L,width),'constraintApplied':vf!=un})

apply_boundary_jets = _apply
