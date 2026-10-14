"""Vixen hyperfield reference port; standard library, no RNG state."""
import math
from types import MappingProxyType
ALGORITHM='vixen-hyperfield-c2/4'
DIMENSIONS=16
FEATURES=48
DEFAULTS={'seed':'vixen','openingEndpoint':100,'introVariation':0,'floor':'none','ceilingMargin':None,'boundarySoftness':.1,'stabilizer':0,'strength':1,'ramp':16,'tempo':.8,'contrast':10,'latent':(0,)*16,'velocity':(0,)*16}
MASK=0xffffffff
def smooth5(t):return 0 if t<=0 else 1 if t>=1 else t*t*t*(10+t*(-15+6*t))
def boundary_ramp(t):
 if isinstance(t,bool) or not isinstance(t,(int,float)) or not math.isfinite(t):raise ValueError('Finite boundary coordinate required')
 return 0 if t<=0 else t if t>=1 else t*t*t*(6+t*(-8+3*t))
def _floor_map(y,lo,e):
 if y<=lo:return lo
 if e==0 or y>=lo+e:return y
 return lo+e*boundary_ramp((y-lo)/e)
def _ceil_map(y,hi,e):
 if y>=hi:return hi
 if e==0 or y<=hi-e:return y
 return hi-e*boundary_ramp((hi-y)/e)
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
class _Leaf:
 def __init__(self,**options):
  if set(options)-set(DEFAULTS):raise ValueError('Unknown configuration field')
  c={**DEFAULTS,**options};self.seed=seed32(c['seed'])
  if c['floor'] not in ('none','zero','baseline','backbone'):raise ValueError('Invalid floor')
  m=c['ceilingMargin']
  if m is not None and (isinstance(m,bool) or not isinstance(m,(int,float)) or not math.isfinite(m) or not 0<=m<=16):raise ValueError('Invalid ceilingMargin')
  for key,lo,hi in [('openingEndpoint',2,50000),('introVariation',0,1),('boundarySoftness',0,1),('stabilizer',0,1),('strength',0,2),('ramp',1,64),('tempo',.25,4),('contrast',0,16)]:
   v=c[key]
   if isinstance(v,bool) or not isinstance(v,(int,float)) or not math.isfinite(v) or not lo<=v<=hi:raise ValueError('Invalid '+key)
  c['latent']=_vector(c['latent'],128);c['velocity']=_vector(c['velocity'],4);self.config=MappingProxyType(c);self._stabilization_weight=smooth5(c['stabilizer'])
  self.domain=(1,min(50000,1+(50000-1)*(c['openingEndpoint']-1)/99))
  v=c['introVariation'];self.dips=tuple(MappingProxyType({'center':old_center+v*(a+b*_unit(self.seed,i,700)-old_center),'width':math.sqrt(old_den+v*((4+w*_unit(self.seed,i,701))**2-old_den)),'depth':old_depth+v*(d+e*_unit(self.seed,i,702)-old_depth)}) for i,a,b,w,d,e,old_center,old_den,old_depth in [(0,32,15,2,18,6,42,32,25),(1,62,20,3,35,13,71,28,38)])
  ridges=[]
  for i in range(FEATURES):
   direction=_normal([2*_unit(self.seed,i,20+j)-1 for j in range(16)])
   scale=(4+76*_unit(self.seed,i,1))*c['tempo'];base_phase=_unit(self.seed,i,2)*1024
   ridges.append(MappingProxyType({'index':i,'direction':direction,'scale':scale,'basePhase':base_phase,'phase':base_phase+_dot(direction,c['latent']),'slope':1/scale+_dot(direction,c['velocity'])/100,'guidePhase':base_phase+_dot(direction,c['latent'])/32,'guideSlope':(1/scale+_dot(direction,c['velocity'])/100)/32,'channel':i+200}))
  self._ridges=tuple(ridges)
  envelope=(2.2*(1-self._stabilization_weight)+.25*self._stabilization_weight)*c['strength']
  lower=-envelope if c['floor']=='none' else -1 if c['floor']=='zero' else 0 if c['floor']=='baseline' else -.25*c['strength']
  upper=envelope if c['ceilingMargin'] is None else (0 if c['floor']=='baseline' else .25*c['strength'])+c['ceilingMargin']
  self.bounds=MappingProxyType({'unconstrainedSpatialSlope':c['strength']*((1-self._stabilization_weight)*33*c['contrast']/4+self._stabilization_weight*15/256),'score':(-1,1),'deviation':(lower,upper),'multiplier':(1+lower,1+upper),'activity':(0,1),'positiveMultiplier':(math.exp(-.55),math.exp(.55))})
 def _clock(self,x):return x if self.config['openingEndpoint']==100 else 1+(x-1)*99/(self.config['openingEndpoint']-1)
 def _check(self,x):
  _coordinate(x)
  if x>self.domain[1]:raise ValueError('x exceeds the canonical clock domain')
 def _authored(self,t):
  old=baseline(t);c=self.config
  if c['introVariation']==0 or t>=100:return old
  fresh=t*math.exp(t/100)-sum(d['depth']*math.exp(-((t-d['center'])/d['width'])**2) for d in self.dips)
  return old+(fresh-old)*(1-smooth5((t-90)/10))
 def _noise(self,p,channel):
  k=math.floor(p);t=p-k;a=2*_unit(self.seed,k,channel)-1;b=2*_unit(self.seed,k+1,channel)-1
  return a+(b-a)*smooth5(t)
 def _limits(self,base,guide,gate):
  c=self.config;lo=None if c['floor']=='none' else 0 if c['floor']=='zero' else base if c['floor']=='baseline' else guide
  hi=None if c['ceilingMargin'] is None else (base if c['floor']=='baseline' else guide)+c['ceilingMargin']*base
  a=c['strength']*gate*c['boundarySoftness'];gap=(hi-lo)/base if lo is not None and hi is not None else None
  e=(0 if gap<=0 or a==0 else base*(a*gap/(gap+2*a))) if gap is not None else (0 if lo is None and hi is None else base*a)
  return {'floorValue':lo,'ceilingValue':hi,'boundaryWidth':e}
 def _apply(self,y,lim):
  lo=lim['floorValue'];hi=lim['ceilingValue'];e=lim['boundaryWidth']
  if lo is not None and hi is not None and hi<=lo:return lo
  y=y if lo is None else _floor_map(y,lo,e)
  return y if hi is None else _ceil_map(y,hi,e)
 def _result(self,x,score,guide_score):
  c=self.config;gate=smooth5((x-c['openingEndpoint'])/c['ramp']);raw=c['strength']*gate*2.2*math.tanh(c['contrast']*score);ug=c['strength']*gate*.25*math.tanh(2*guide_score);w=self._stabilization_weight;u=raw if w==0 else ug if w==1 else raw+(ug-raw)*w;base=self._authored(self._clock(x));uv=base+base*u;ugv=base+base*ug;lim=self._limits(base,ugv,gate);value=self._apply(uv,lim);gv=self._apply(ugv,lim);inactive=c['floor']=='none' and c['ceilingMargin'] is None;d=u if inactive else (value-base)/base;gd=ug if inactive else (gv-base)/base;pos=math.exp(.55*math.tanh(d))
  return {'x':x,'value':value,'base':base,'score':score,'guideScore':guide_score,'rawDeviation':raw,'guideDeviation':gd,'guideValue':gv,'stabilizationWeight':w,'deviation':d,'multiplier':1+d,'activity':.5+.5*math.tanh(d),'positiveMultiplier':pos,'positiveValue':base*pos,'gate':gate,'unconstrainedValue':uv,'unconstrainedDeviation':u,'unconstrainedGuideValue':ugv,'unconstrainedGuideDeviation':ug,**lim,'constraintApplied':value!=uv}
 def sample(self,x):
  self._check(x);score=guide_score=0
  if x>self.config['openingEndpoint']:
   u=self._clock(x)-100
   for r in self._ridges:
    score+=self._noise(r['phase']+u*r['slope'],r['channel'])/FEATURES
    guide_score+=self._noise(r['guidePhase']+u*r['guideSlope'],r['channel'])/FEATURES
  return self._result(x,score,guide_score)
 def at(self,x,h):return self._at_validated(x,_vector(h))
 def _at_validated(self,x,h):
  self._check(x);score=guide_score=0
  if x>self.config['openingEndpoint']:
   u=self._clock(x)-100
   for r in self._ridges:
    projection=_dot(r['direction'],h)
    score+=self._noise(r['basePhase']+projection+u/r['scale'],r['channel'])/FEATURES
    guide_score+=self._noise(r['basePhase']+projection/32+u/(32*r['scale']),r['channel'])/FEATURES
  return self._result(x,score,guide_score)
 def location(self,x):
  self._check(x);t=max(0,self._clock(x)-100)/100
  return tuple(a+t*b for a,b in zip(self.config['latent'],self.config['velocity']))
 def ridges(self):return tuple(dict(r) for r in self._ridges)
 def inspect(self,x):
  self._check(x)
  if x<=self.config['openingEndpoint']:return []
  u=self._clock(x)-100;out=[]
  for r in self._ridges:
   p=r['phase']+u*r['slope'];out.append({'index':r['index'],'scale':r['scale'],'coordinate':p,'cell':math.floor(p),'contribution':self._noise(p,r['channel'])/FEATURES,'guideContribution':self._noise(r['guidePhase']+u*r['guideSlope'],r['channel'])/FEATURES})
  return sorted(out,key=lambda r:abs(r['contribution']),reverse=True)

def _fork_seed(parent_seed,operation,at):
 import struct
 hi,lo=struct.unpack('>II',struct.pack('>d',at))
 return hash32(hash32(seed32(parent_seed),1 if isinstance(parent_seed,str) else 2,seed32(operation)),hi,lo)
_MIX_FIELDS=('x', 'value', 'base', 'score', 'guideScore', 'rawDeviation', 'guideValue', 'stabilizationWeight', 'gate', 'unconstrainedValue', 'unconstrainedGuideValue', 'boundaryWidth')
def _mix_samples(a,b,w):
 r=a.copy()
 for k in _MIX_FIELDS:r[k]=a[k]+(b[k]-a[k])*w
 for k in ('floorValue','ceilingValue'):
  if a[k] is not None:r[k]=a[k]+(b[k]-a[k])*w
 if r['floorValue'] is not None:r['value']=max(r['value'],r['floorValue'])
 if r['ceilingValue'] is not None:r['value']=min(r['value'],r['ceilingValue'])
 r['deviation']=(r['value']-r['base'])/r['base'];r['multiplier']=1+r['deviation'];r['activity']=.5+.5*math.tanh(r['deviation'])
 r['positiveMultiplier']=math.exp(.55*math.tanh(r['deviation']));r['positiveValue']=r['base']*r['positiveMultiplier']
 r['guideDeviation']=(r['guideValue']-r['base'])/r['base'];r['unconstrainedDeviation']=(r['unconstrainedValue']-r['base'])/r['base']
 r['unconstrainedGuideDeviation']=(r['unconstrainedGuideValue']-r['base'])/r['base'];r['constraintApplied']=r['value']!=r['unconstrainedValue']
 return r
class Vixen:
 """Immutable timeline. Use snapshot(), not config alone, to replay branches."""
 __slots__=('_leaf','_root_config','_lineage','_parent','_node','_sealed')
 def __init__(self,**options):self._initialize(_Leaf(**options))
 def __setattr__(self,name,value):
  if getattr(self,'_sealed',False):raise AttributeError('Vixen timelines are immutable')
  object.__setattr__(self,name,value)
 def _initialize(self,leaf,root_config=None,lineage=(),parent=None,node=None):
  self._leaf=leaf;self._root_config=leaf.config if root_config is None else root_config;self._lineage=lineage;self._parent=parent;self._node=node;self._sealed=True
 @property
 def algorithm(self):return ALGORITHM
 @property
 def config(self):return self._leaf.config
 @property
 def bounds(self):return self._leaf.bounds
 @property
 def domain(self):return self._leaf.domain
 @property
 def dips(self):return self._leaf.dips
 @property
 def lineage(self):return self._lineage
 def _evaluate(self,x,h=None):
  n=self._node
  if n is None or x>=n['at']+n['handoffWidth']:return self._leaf.sample(x) if h is None else self._leaf._at_validated(x,h)
  if x<=n['at']:return self._parent._evaluate(x,h)
  w=smooth5((x-n['at'])/n['handoffWidth']);a=self._parent._evaluate(x,h);b=self._leaf.sample(x) if h is None else self._leaf._at_validated(x,h)
  return _mix_samples(a,b,w)
 def sample(self,x):
  self._leaf._check(x);return self._evaluate(x)
 def at(self,x,h):
  self._leaf._check(x);return self._evaluate(x,_vector(h))
 def location(self,x):return self._leaf.location(x)
 def ridges(self):return self._leaf.ridges()
 def inspect(self,x):
  self._leaf._check(x);n=self._node
  if n is None or x>=n['at']+n['handoffWidth']:return self._leaf.inspect(x)
  if x<=n['at']:return self._parent.inspect(x)
  w=smooth5((x-n['at'])/n['handoffWidth'])
  return sorted([{**r,'sourceSeed':r.get('sourceSeed',source_seed),'contribution':r['contribution']*factor,'guideContribution':r['guideContribution']*factor} for rows,factor,source_seed in [(self._parent.inspect(x),1-w,self._parent.config['seed']),(self._leaf.inspect(x),w,self.config['seed'])] for r in rows],key=lambda r:abs(r['contribution']),reverse=True)
 def snapshot(self):
  return {'algorithm':ALGORITHM,'config':{k:list(v) if isinstance(v,tuple) else v for k,v in self._root_config.items()},'lineage':[{k:n[k] for k in ('at','operation','handoffWidth')} for n in self._lineage]}
 def fork(self,*,at,operation,handoffWidth=None):
  self._leaf._check(at)
  if not isinstance(operation,str) or not operation:raise TypeError('A nonempty operation string is required')
  seed32(operation)
  if len(self._lineage)>=32:raise ValueError('Maximum branch depth: 32')
  if self._node is not None and at<self._node['at']:raise ValueError('Nested forks cannot precede their parent fork')
  width=min(16,self.domain[1]-at) if handoffWidth is None else handoffWidth
  if isinstance(width,bool) or not isinstance(width,(int,float)) or not math.isfinite(width) or width<=0 or at+width<=at or at+width>self.domain[1]:raise ValueError('Handoff must have positive representable width inside the domain')
  seed=_fork_seed(self.config['seed'],operation,at);entry=MappingProxyType({'at':at,'operation':operation,'handoffWidth':width,'seed':seed});future=_Leaf(**{**self.config,'seed':seed})
  result=object.__new__(Vixen);result._initialize(future,self._root_config,(*self._lineage,entry),self,entry);return result

def restore(snapshot):
 if not isinstance(snapshot,dict) or not isinstance(snapshot.get('config'),(dict,MappingProxyType)):raise ValueError('Snapshot with configuration required')
 if set(snapshot)-{'algorithm','config','lineage'}:raise ValueError('Unknown snapshot field')
 cfg=snapshot['config'];algorithm=snapshot.get('algorithm')
 if algorithm==ALGORITHM:
  lineage=snapshot.get('lineage',[])
  if lineage is None:lineage=[]
  if not isinstance(lineage,(list,tuple)) or len(lineage)>32:raise ValueError('Invalid branch lineage')
  result=Vixen(**cfg)
  for entry in lineage:
   if not isinstance(entry,dict):raise ValueError('Invalid lineage entry')
   result=result.fork(**entry)
  return result
 if algorithm in ('vixen-hyperfield-c2/1','vixen-hyperfield-c2/2','vixen-hyperfield-c2/3'):
  if 'lineage' in snapshot or any(k in cfg for k in ('openingEndpoint','introVariation')):raise ValueError('Old snapshots cannot contain timeline features')
  if algorithm!='vixen-hyperfield-c2/3' and any(k in cfg for k in ('floor','ceilingMargin','boundarySoftness')):raise ValueError('Old snapshots cannot contain constraints')
  if algorithm=='vixen-hyperfield-c2/1' and 'stabilizer' in cfg:raise ValueError('Old snapshots cannot contain stabilizer')
  return Vixen(**(cfg if algorithm=='vixen-hyperfield-c2/3' else {**cfg,'floor':'none','ceilingMargin':None}))
 raise ValueError('Algorithm mismatch')
