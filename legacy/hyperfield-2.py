"""Vixen hyperfield reference port; standard library, no RNG state."""
import math
from types import MappingProxyType
ALGORITHM='vixen-hyperfield-c2/2'
DIMENSIONS=16
FEATURES=48
DEFAULTS={'seed':'vixen','stabilizer':0,'strength':1,'ramp':16,'tempo':.8,'contrast':10,'latent':(0,)*16,'velocity':(0,)*16}
MASK=0xffffffff
def smooth5(t):return 0 if t<=0 else 1 if t>=1 else t*t*t*(10+t*(-15+6*t))
def bump(t):return 0 if abs(t)>=1 else (1-t*t)**3
def seed32(seed):
 if isinstance(seed,bool):raise TypeError('Seed must be text or uint32')
 if isinstance(seed,(int,float)):
  if not math.isfinite(seed) or int(seed)!=seed or not 0<=seed<=MASK:raise ValueError('Numeric seed must be uint32')
  return int(seed)
 if not isinstance(seed,str):raise TypeError('Seed must be text or uint32')
 raw=seed.encode('utf-16-le',errors='surrogatepass')
 if len(raw)>2048:raise ValueError('Text seed limit:1024 UTF-16 code units')
 h=2166136261
 for b in raw.decode('utf-16-le',errors='replace').encode('utf-8'):h=((h^b)*16777619)&MASK
 return h
def hash32(seed,index,channel):
 h=(seed^(((index&MASK)*0x9e3779b9)&MASK)^(((channel&MASK)*0x85ebca6b)&MASK))&MASK
 h=((h^(h>>16))*0x7feb352d)&MASK;h=((h^(h>>15))*0x846ca68b)&MASK
 return (h^(h>>16))&MASK
def _unit(s,k,c):return hash32(s,k,c)/4294967296
def _dot(a,b):return sum(x*y for x,y in zip(a,b))
def _normal(a):
 n=math.sqrt(_dot(a,a))
 return tuple(v/n for v in a) if n else (1.,)+(0.,)*15
def _vector(a,limit=1000000):
 if not isinstance(a,(list,tuple)) or len(a)!=16:raise ValueError('Exactly16 coordinates required')
 if any(isinstance(v,bool) or not isinstance(v,(int,float)) or not math.isfinite(v) or abs(v)>limit for v in a):raise ValueError('Invalid hypervector coordinate')
 return tuple(a)
def _coordinate(x):
 if isinstance(x,bool) or not isinstance(x,(int,float)) or not math.isfinite(x) or not 1<=x<=50000:raise ValueError('x must be finite in [1,50000]')
def baseline(x):return x*math.exp(x/100)-25*math.exp(-((x-42)**2)/32)-38*math.exp(-((x-71)**2)/28)
def navigation_basis(index=0):
 if isinstance(index,bool) or not isinstance(index,int) or not 0<=index<=100000:raise ValueError('Invalid plane index')
 if not index:return {'u':(1.,)+(0.,)*15,'v':(0.,1.)+(0.,)*14}
 seed=seed32('vixen-navigation');u=_normal([2*_unit(seed,index,100+j)-1 for j in range(16)])
 raw=[2*_unit(seed,index,200+j)-1 for j in range(16)];proj=_dot(raw,u);v=[n-proj*u[j] for j,n in enumerate(raw)]
 if _dot(v,v)<1e-20:
  k=min(range(16),key=lambda j:abs(u[j]));v=[(1 if j==k else 0)-u[k]*u[j] for j in range(16)]
 return {'u':u,'v':_normal(v)}
class Vixen:
 def __init__(self,**options):
  if set(options)-set(DEFAULTS):raise ValueError('Unknown configuration field')
  c={**DEFAULTS,**options};self.seed=seed32(c['seed'])
  for key,lo,hi in [('stabilizer',0,1),('strength',0,2),('ramp',1,64),('tempo',.25,4),('contrast',0,16)]:
   v=c[key]
   if isinstance(v,bool) or not isinstance(v,(int,float)) or not math.isfinite(v) or not lo<=v<=hi:raise ValueError('Invalid '+key)
  c['latent']=_vector(c['latent'],128);c['velocity']=_vector(c['velocity'],4);self.config=MappingProxyType(c)
  ridges=[]
  for i in range(FEATURES):
   direction=_normal([2*_unit(self.seed,i,20+j)-1 for j in range(16)])
   scale=(4+76*_unit(self.seed,i,1))*c['tempo'];base_phase=_unit(self.seed,i,2)*1024
   ridges.append(MappingProxyType({'index':i,'direction':direction,'scale':scale,'basePhase':base_phase,'phase':base_phase+_dot(direction,c['latent']),'slope':1/scale+_dot(direction,c['velocity'])/100,'guidePhase':base_phase+_dot(direction,c['latent'])/32,'guideSlope':(1/scale+_dot(direction,c['velocity'])/100)/32,'channel':i+200}))
  self._ridges=tuple(ridges)
  envelope=(2.2*(1-smooth5(c['stabilizer']))+.25*smooth5(c['stabilizer']))*c['strength']
  self.bounds=MappingProxyType({'score':(-1,1),'deviation':(-envelope,envelope),'multiplier':(1-envelope,1+envelope),'activity':(0,1),'positiveMultiplier':(math.exp(-.55),math.exp(.55))})
 def _noise(self,p,channel):
  k=math.floor(p);t=p-k;a=2*_unit(self.seed,k,channel)-1;b=2*_unit(self.seed,k+1,channel)-1
  return a+(b-a)*smooth5(t)
 def _result(self,x,score,guide_score):
  c=self.config;gate=smooth5((x-100)/c['ramp']);raw=c['strength']*gate*2.2*math.tanh(c['contrast']*score);guide=c['strength']*gate*.25*math.tanh(2*guide_score);w=smooth5(c['stabilizer']);d=raw if w==0 else guide if w==1 else raw+(guide-raw)*w;base=baseline(x);pos=math.exp(.55*math.tanh(d))
  return {'x':x,'value':base+base*d,'base':base,'score':score,'guideScore':guide_score,'rawDeviation':raw,'guideDeviation':guide,'guideValue':base+base*guide,'stabilizationWeight':w,'deviation':d,'multiplier':1+d,'activity':.5+.5*math.tanh(d),'positiveMultiplier':pos,'positiveValue':base*pos,'gate':gate}
 def sample(self,x):
  _coordinate(x);score=guide_score=0
  if x>100:
   u=x-100
   for r in self._ridges:
    score+=self._noise(r['phase']+u*r['slope'],r['channel'])/FEATURES
    guide_score+=self._noise(r['guidePhase']+u*r['guideSlope'],r['channel'])/FEATURES
  return self._result(x,score,guide_score)
 def at(self,x,h):
  _coordinate(x);h=_vector(h);score=guide_score=0
  if x>100:
   u=x-100
   for r in self._ridges:
    projection=_dot(r['direction'],h)
    score+=self._noise(r['basePhase']+projection+u/r['scale'],r['channel'])/FEATURES
    guide_score+=self._noise(r['basePhase']+projection/32+u/(32*r['scale']),r['channel'])/FEATURES
  return self._result(x,score,guide_score)
 def location(self,x):
  _coordinate(x);t=max(0,x-100)/100
  return tuple(a+t*b for a,b in zip(self.config['latent'],self.config['velocity']))
 def ridges(self):return tuple(dict(r) for r in self._ridges)
 def inspect(self,x):
  _coordinate(x)
  if x<=100:return []
  u=x-100;out=[]
  for r in self._ridges:
   p=r['phase']+u*r['slope'];out.append({'index':r['index'],'scale':r['scale'],'coordinate':p,'cell':math.floor(p),'contribution':self._noise(p,r['channel'])/FEATURES,'guideContribution':self._noise(r['guidePhase']+u*r['guideSlope'],r['channel'])/FEATURES})
  return sorted(out,key=lambda r:abs(r['contribution']),reverse=True)

def restore(snapshot):
 if not isinstance(snapshot,dict):raise ValueError('Snapshot required')
 if snapshot.get('algorithm')==ALGORITHM:return Vixen(**snapshot['config'])
 if snapshot.get('algorithm')=='vixen-hyperfield-c2/1':
  if 'stabilizer' in snapshot.get('config',{}):raise ValueError('Old snapshots cannot contain stabilizer')
  return Vixen(**{**snapshot['config'],'stabilizer':0})
 raise ValueError('Algorithm mismatch')
