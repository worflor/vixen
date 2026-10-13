"""Vixen reference port. Match vixen-counter-c2/1; standard library only.
Binary64/libm curves are numerically reproducible, not cross-platform bit-identical.
"""
import math
from types import MappingProxyType
ALGORITHM='vixen-counter-c2/1'
DEFAULTS={'seed':'vixen','variation':.35,'strength':1,'ramp':16,'spacing':12}
MASK=0xffffffff

def seed32(seed):
    if isinstance(seed,(int,float)) and not isinstance(seed,bool):
        if not math.isfinite(seed) or int(seed)!=seed or seed<0 or seed>MASK: raise ValueError('Numeric seed must be uint32')
        return int(seed)
    if not isinstance(seed,str): raise TypeError('Seed must be text or uint32')
    utf16=seed.encode('utf-16-le','surrogatepass')
    if len(utf16)//2>1024: raise ValueError('Text seed limit is 1024 UTF-16 code units')
    data=utf16.decode('utf-16-le','replace').encode('utf-8')
    h=2166136261
    for b in data:h=((h^b)*16777619)&MASK
    return h

def hash32(seed,index,channel):
    h=(seed^((int(index)&MASK)*0x9e3779b9)^((int(channel)&MASK)*0x85ebca6b))&MASK
    h=((h^(h>>16))*0x7feb352d)&MASK
    h=((h^(h>>15))*0x846ca68b)&MASK
    return (h^(h>>16))&MASK

def smooth5(t):
    if t<=0:return 0.0
    if t>=1:return 1.0
    return t*t*t*(10+t*(-15+6*t))

def bump(t):
    if abs(t)>=1:return 0.0
    v=1-t*t
    return v*v*v

def baseline(x):
    return x*math.exp(x/100)-25*math.exp(-((x-42)**2)/32)-38*math.exp(-((x-71)**2)/28)

class Vixen:
    def __init__(self,**options):
        if set(options)-set(DEFAULTS):raise ValueError('Unknown config key')
        self.config=MappingProxyType(DEFAULTS|options)
        self.seed=seed32(self.config['seed'])
        for key,lo,hi in [('variation',0,1),('strength',0,2),('ramp',1,64),('spacing',6,24)]:
            v=self.config[key]
            if isinstance(v,bool) or not isinstance(v,(int,float)) or not math.isfinite(v) or not lo<=v<=hi:raise ValueError(key+' out of range')
        s=self.config['strength']
        self.bounds={'deviation':[-2.61*s,2.06*s],'multiplier':[1-2.61*s,1+2.06*s],'activity':[0,1],'positiveMultiplier':[math.exp(-.55),math.exp(.55)]}
    def _rnd(self,k,c):
        a=hash32(0,k,c)/4294967296;b=hash32(self.seed,k,c)/4294967296
        return a+(b-a)*self.config['variation']
    def _noise(self,u,period,c):
        p=u/period;k=math.floor(p);t=p-k
        a=2*self._rnd(k,c)-1;b=2*self._rnd(k+1,c)-1
        return a+(b-a)*smooth5(t)
    @staticmethod
    def _amplitude(s,k):
        v=hash32(s,k,7)/4294967296
        return -(1.55+.8*v) if hash32(s,k,6)/4294967296<.43 else .7+1.1*v
    def event(self,k):
        if isinstance(k,bool) or not isinstance(k,int) or not 0<=k<=100000:raise ValueError('Event index out of range')
        c=self.config;a=self._amplitude(0,k);b=self._amplitude(self.seed,k)
        return {'index':k,'center':c['spacing']*(k+.5+.2*(2*self._rnd(k,4)-1)),'width':c['spacing']*(.14+.11*self._rnd(k,5)),'amplitude':a+(b-a)*c['variation']}
    def sample(self,x):
        if isinstance(x,bool) or not isinstance(x,(int,float)) or not math.isfinite(x) or not 1<=x<=50000:raise ValueError('x must be finite in [1,50000]')
        c=self.config;u=x-100;g=smooth5(u/c['ramp']);texture=pulse=0.0;active=None
        if u>0 and c['strength']>0:
            texture=.16*self._noise(u,18,1)+.10*self._noise(u,43,2)
            k=math.floor(u/c['spacing']);e=self.event(k);z=(u-e['center'])/e['width']
            pulse=e['amplitude']*bump(z)
            if abs(z)<1:active=e['index']
        deviation=c['strength']*g*(texture+pulse);base=baseline(x);positive=math.exp(.55*math.tanh(deviation))
        return {'x':x,'value':base+base*deviation,'base':base,'deviation':deviation,'multiplier':1+deviation,'activity':.5+.5*math.tanh(deviation),'positiveMultiplier':positive,'positiveValue':base*positive,'gate':g,'texture':texture,'pulse':pulse,'event':active}
