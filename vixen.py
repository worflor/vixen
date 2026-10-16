import vixen_interval as _I
from vixen_analysis import next_crossing,make_lipschitz_enclosure,calibrate_occupancy,validate_occupancy,restore_occupancy,validate_correlation_matrix,create_correlated_channels,measure_channel_correlation
"""Vixen hyperfield reference port; standard library, no RNG state."""
from vixen_calculus import leaf_jet,native_normalized,Jet,jet_smooth5
from vixen_jet_composition import blend_jets
from types import SimpleNamespace
import math
from vixen_wide import Wide,WIDE_FORMAT
from vixen_exponential import wide_exp
from vixen_extended_operations import extended_polynomial_guide,with_extended_influence,with_extended_impulse,with_extended_redistribution,restore_extended_operation,EXTENDED_OPERATION_ALGORITHM
from vixen_api import CurveCapabilities,query_controls,register_root_curve,GRAPH_ALGORITHM,restore_curve_graph
from vixen_operations import with_impulse,with_redistribution,couple,perturbation_jet,restore_operation,OPERATION_ALGORITHM
import re
from vixen_checkpoint import history_witness as _history_witness
TAIL_ALGORITHM="vixen-tail-c2/2"
from vixen_timeline import append as _history_append, before as _history_before, entries as _history_entries
from types import MappingProxyType
from vixen_guide import polynomial_guide,taylor_guide,sampled_guide,capture_guide,compile_guide,evaluate_guide
from vixen_influence import with_influence,restore_influence,INFLUENCE_ALGORITHM,INFLUENCE_ALGORITHM_V2,INFLUENCE_ALGORITHM_V3,is_influence_algorithm
ALGORITHM='vixen-hyperfield-c2/4'
COHERENT_ALGORITHM='vixen-hyperfield-c2/5'
COHERENT_TAIL_ALGORITHM='vixen-tail-c2/3'
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
  self.algorithm=ALGORITHM
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

def _versioned_leaf(options,algorithm):
 leaf=_Leaf(**options);leaf.algorithm=algorithm;return leaf

def _fork_seed(parent_seed,operation,at):
 import struct
 hi,lo=struct.unpack('>II',struct.pack('>d',at))
 return hash32(hash32(seed32(parent_seed),1 if isinstance(parent_seed,str) else 2,seed32(operation)),hi,lo)
_MIX_FIELDS=('x', 'value', 'base', 'score', 'guideScore', 'rawDeviation', 'guideValue', 'stabilizationWeight', 'gate', 'unconstrainedValue', 'unconstrainedGuideValue', 'boundaryWidth')
def _sample_policy(y,p):
 e,lo,hi=p['boundaryWidth'],p['floorValue'],p['ceilingValue']
 def ramp(t):return 0 if t<=0 else t if t>=1 else t*t*t*(6+t*(-8+3*t))
 if lo is not None and hi is not None and hi<=lo:return lo
 if lo is not None:y=lo if y<=lo else y if e==0 or y>=lo+e else lo+e*ramp((y-lo)/e)
 if hi is not None:y=hi if y>=hi else y if e==0 or y<=hi-e else hi-e*ramp((hi-y)/e)
 return y

def _mix_samples(a,b,w,coherent=False):
 r=a.copy()
 for k in _MIX_FIELDS:r[k]=a[k]+(b[k]-a[k])*w
 for k in ('floorValue','ceilingValue'):
  if a[k] is not None:r[k]=a[k]+(b[k]-a[k])*w
 if coherent:r['value']=_sample_policy(r['unconstrainedValue'],r);r['guideValue']=_sample_policy(r['unconstrainedGuideValue'],r)
 else:
  if r['floorValue'] is not None:r['value']=max(r['value'],r['floorValue'])
  if r['ceilingValue'] is not None:r['value']=min(r['value'],r['ceilingValue'])
 r['deviation']=(r['value']-r['base'])/r['base'];r['multiplier']=1+r['deviation'];r['activity']=.5+.5*math.tanh(r['deviation'])
 r['positiveMultiplier']=math.exp(.55*math.tanh(r['deviation']));r['positiveValue']=r['base']*r['positiveMultiplier']
 r['guideDeviation']=(r['guideValue']-r['base'])/r['base'];r['unconstrainedDeviation']=(r['unconstrainedValue']-r['base'])/r['base']
 r['unconstrainedGuideDeviation']=(r['unconstrainedGuideValue']-r['base'])/r['base'];r['constraintApplied']=r['value']!=r['unconstrainedValue']
 return r
class Vixen(CurveCapabilities):
 """Immutable, indexed timeline. Snapshots retain its decision history."""
 __slots__=('_leaf','_root_config','_root_leaf','_tree','_node','_checkpoint','_query_knots','_evaluation_state','_sealed')
 def __init__(self,**options):self._initialize(_Leaf(**options))
 def __setattr__(self,name,value):
  if getattr(self,'_sealed',False):raise AttributeError('Vixen timelines are immutable')
  object.__setattr__(self,name,value)
 def _initialize(self,leaf,root_config=None,tree=None,root_leaf=None,node=None,checkpoint=None):
  self._leaf=leaf;self._root_config=leaf.config if root_config is None else root_config;self._root_leaf=leaf if root_leaf is None else root_leaf;self._tree=tree;self._node=node;self._checkpoint=checkpoint;register_root_curve(self,self.domain,max(1,node['at'] if node else 1));self._sealed=True
 @property
 def algorithm(self):return self._checkpoint['algorithm'] if self._checkpoint else self._root_leaf.algorithm
 @property
 def config(self):return self._leaf.config
 @property
 def bounds(self):return self._leaf.bounds
 @property
 def domain(self):return (self._checkpoint['from'],self._leaf.domain[1]) if self._checkpoint else self._leaf.domain
 @property
 def dips(self):return self._leaf.dips
 @property
 def lineage(self):return tuple(e[0] for e in _history_entries(self._tree))
 def _route(self,x):
  if self._node is None or x>=self._node['at']+self._node['handoffWidth']:return self._leaf,[]
  active=[];base=self._root_leaf
  for entry,leaf in _history_before(self._tree,x):
   if x>=entry['at']+entry['handoffWidth']:
    base=leaf;break
   active.append((entry,leaf))
  return base,active
 def _evaluate(self,x,h=None):
  base,active=self._route(x)
  sample=lambda leaf:leaf.sample(x) if h is None else leaf._at_validated(x,h)
  p=sample(base)
  for entry,leaf in reversed(active):p=_mix_samples(p,sample(leaf),smooth5((x-entry['at'])/entry['handoffWidth']),self._root_leaf.algorithm==COHERENT_ALGORITHM)
  return p
 def sample(self,x):
  self._check_tail(x);self._leaf._check(x);return self._evaluate(x)
 def at(self,x,h):
  self._check_tail(x);self._leaf._check(x);return self._evaluate(x,_vector(h))
 @property
 def normalized_domain(self):return (self.domain[0],2**40)
 def sample_normalized(self,x):return self._sample_normalized(x)
 def at_normalized(self,x,h):return self._sample_normalized(x,_vector(h))
 def _sample_normalized(self,x,h=None):
  if isinstance(x,bool) or not isinstance(x,(int,float)) or not math.isfinite(x) or not self.domain[0]<=x<=2**40:raise ValueError('Normalized coordinate outside its finite domain')
  facade=SimpleNamespace(config=self.config,dips=self.dips,ridges=self.ridges,domain=self._leaf.domain,sample=self.sample,at=self.at)
  return native_normalized(facade,x,h)
 def enclose_deviation(self,lo,hi,h=None):
  from bisect import bisect_right,bisect_left
  self._check_tail(lo);self._check_tail(hi);self._leaf._check(lo);self._leaf._check(hi)
  if lo>hi:raise ValueError('Enclosure bounds must be ordered')
  coords=None if h is None else _vector(h)
  knots=getattr(self,'_query_knots',None)
  if knots is None:
   knots=tuple(sorted({self.config['openingEndpoint']}|{v for entry,_ in _history_entries(self._tree) for v in (entry['at'],entry['at']+entry['handoffWidth'])}));object.__setattr__(self,'_query_knots',knots)
  cuts=[lo]+list(knots[bisect_right(knots,lo):bisect_left(knots,hi)])+([] if lo==hi else [hi]);result=None
  for j in range(max(1,len(cuts)-1)):
   a=cuts[j];b=cuts[j+1] if j+1<len(cuts) else a;base,active=self._route(a/2+b/2);memo={}
   if b<=self.config['openingEndpoint']:out=_I.interval(0)
   elif not active:out=_I.nativeDeviationEnclosure(base,a,b,coords)
   else:
    def native(leaf):
     if leaf not in memo:memo[leaf]=_I.nativeConstraintState(leaf,a,b,coords)
     return memo[leaf]
    coherent=self._root_leaf.algorithm==COHERENT_ALGORITHM;state=native(base)
    for entry,future in reversed(active):
     w=_I.smooth5(_I.divide(_I.subtract(_I.interval(a,b),_I.interval(entry['at'])),_I.interval(entry['handoffWidth'])))
     state=_I.mixConstraintStates(state,native(future),w,coherent)
    out=_I.constraintDeviation(state) if coherent else _I.appliedDeviation(state)
   result=out if result is None else _I.hull(result,out)
  return (result.lo,result.hi)
 def next_crossing(self,threshold,from_,horizon,options=None):
  controls=query_controls(self,from_,options)
  return next_crossing(self,threshold,from_,horizon,controls)
 def jet(self,x,h=None):
  self._check_tail(x);self._leaf._check(x);coords=None if h is None else _vector(h);base,active=self._route(x);out=leaf_jet(base,x,coords)
  for entry,future in reversed(active):
   b=leaf_jet(future,x,coords);weight=jet_smooth5(Jet((x-entry['at'])/entry['handoffWidth'],1/entry['handoffWidth'],0))
   out=blend_jets(out,b,weight,_mix_samples(out['sample'],b['sample'],weight.value,self._root_leaf.algorithm==COHERENT_ALGORITHM),self._root_leaf.algorithm==COHERENT_ALGORITHM)
  return out
 def slope(self,x,h=None):return self.jet(x,h)['slope']
 def curvature(self,x,h=None):return self.jet(x,h)['curvature']
 def location(self,x):self._check_tail(x);return self._leaf.location(x)
 def ridges(self):return self._leaf.ridges()
 def inspect(self,x):
  self._check_tail(x);self._leaf._check(x);base,active=self._route(x);rows=base.inspect(x);source_seed=base.config['seed']
  for entry,leaf in reversed(active):
   w=smooth5((x-entry['at'])/entry['handoffWidth'])
   rows=sorted([{**r,'sourceSeed':r.get('sourceSeed',seed),'contribution':r['contribution']*factor,'guideContribution':r['guideContribution']*factor} for data,factor,seed in [(rows,1-w,source_seed),(leaf.inspect(x),w,leaf.config['seed'])] for r in data],key=lambda r:abs(r['contribution']),reverse=True)
   source_seed=leaf.config['seed']
  return rows
 def snapshot(self):
  rows=[{k:n[k] for k in ('at','operation','handoffWidth')} for n in self.lineage]
  cfg={k:list(v) if isinstance(v,tuple) else v for k,v in self._root_config.items()}
  return {**self._checkpoint,'config':cfg,'lineage':rows} if self._checkpoint else {'algorithm':self._root_leaf.algorithm,'config':cfg,'lineage':rows}
 def _check_tail(self,x):
  _coordinate(x)
  if x<self.domain[0]:raise ValueError('x precedes the available checkpoint')
 def tail(self,from_):
  self._check_tail(from_);self._leaf._check(from_)
  events=list(_history_entries(self._tree));cut=-1
  for i,(entry,leaf) in enumerate(events):
   if entry['at']+entry['handoffWidth']<=from_:cut=i
  folded=[e[0] for e in events[:cut+1]];base=self._root_leaf if cut<0 else events[cut][1];prior=self._checkpoint['witness'] if self._checkpoint else None
  witness=_history_witness(prior,folded) if folded or prior is None else prior
  return {'algorithm':COHERENT_TAIL_ALGORITHM if self._root_leaf.algorithm==COHERENT_ALGORITHM else TAIL_ALGORITHM,'from':from_,'config':{k:list(v) if isinstance(v,tuple) else v for k,v in self._root_config.items()},'seed':base.config['seed'],'leafSeed':seed32(base.config['seed']),'foldedCount':(self._checkpoint['foldedCount'] if self._checkpoint else 0)+len(folded),'witness':witness,'lineage':[{k:e[k] for k in ('at','operation','handoffWidth')} for e,leaf in events[cut+1:]]}
 def influence(self,target,**options):return with_influence(self,target,**options)
 def fork(self,*,at,operation,handoffWidth=None):
  self._check_tail(at);self._leaf._check(at)
  if not isinstance(operation,str) or not operation:raise TypeError('A nonempty operation string is required')
  seed32(operation)
  if self._node is not None and at<self._node['at']:raise ValueError('Nested forks cannot precede their parent fork')
  width=min(16,self.domain[1]-at) if handoffWidth is None else handoffWidth
  if isinstance(width,bool) or not isinstance(width,(int,float)) or not math.isfinite(width) or width<=0 or at+width<=at or at+width>self.domain[1]:raise ValueError('Handoff must have positive representable width inside the domain')
  seed=_fork_seed(self.config['seed'],operation,at);entry=MappingProxyType({'at':at,'operation':operation,'handoffWidth':width,'seed':seed});future=_versioned_leaf({**self.config,'seed':seed},self._root_leaf.algorithm)
  result=object.__new__(Vixen);result._initialize(future,self._root_config,_history_append(self._tree,(entry,future)),self._root_leaf,entry,self._checkpoint);return result

def _create_version(options,algorithm):
 result=object.__new__(Vixen);result._initialize(_versioned_leaf(options,algorithm));return result

def create_coherent(**options):return _create_version(options,COHERENT_ALGORITHM)

def _restore_tail(snapshot):
 keys={'algorithm','from','config','seed','leafSeed','foldedCount','witness','lineage'}
 if not isinstance(snapshot,dict) or set(snapshot)!=keys:raise ValueError('Invalid checkpoint fields')
 algorithm=COHERENT_ALGORITHM if snapshot['algorithm']==COHERENT_TAIL_ALGORITHM else ALGORITHM
 _coordinate(snapshot['from']);root=_create_version(snapshot['config'],algorithm)
 count=snapshot['foldedCount'];leaf_seed=snapshot['leafSeed'];witness=snapshot['witness']
 if snapshot['from']>root.domain[1] or isinstance(count,bool) or not isinstance(count,int) or not 0<=count<=9007199254740991 or isinstance(leaf_seed,bool) or not isinstance(leaf_seed,int) or not 0<=leaf_seed<=MASK or seed32(snapshot['seed'])!=leaf_seed or not isinstance(witness,str) or re.fullmatch('[0-9a-f]{64}',witness) is None or not isinstance(snapshot['lineage'],(list,tuple)):raise ValueError('Invalid checkpoint data')
 if count==0 and (isinstance(snapshot['seed'],str)!=isinstance(root.config['seed'],str) or snapshot['seed']!=root.config['seed']):raise ValueError('Unfolded checkpoint must preserve the typed root seed')
 if count>0 and (isinstance(snapshot['seed'],bool) or not isinstance(snapshot['seed'],int)):raise ValueError('Folded checkpoint seed must be uint32')
 result=_create_version({**snapshot['config'],'seed':snapshot['seed']},algorithm)
 for entry in snapshot['lineage']:
  if not isinstance(entry,dict):raise ValueError('Invalid checkpoint lineage entry')
  result=result.fork(**entry)
 checkpoint=MappingProxyType({k:v for k,v in snapshot.items() if k!='lineage'})
 out=object.__new__(Vixen);out._initialize(result._leaf,root.config,result._tree,result._root_leaf,result._node,checkpoint)
 return out

def restore(snapshot):
 return _restore_frame(snapshot,restore)

def _restore_frame(snapshot,reader):
 if isinstance(snapshot,dict) and snapshot.get('algorithm')==EXTENDED_COUPLING_ALGORITHM:return restore_extended_coupling(snapshot,reader)
 if isinstance(snapshot,dict) and snapshot.get('algorithm')==EXTENDED_OPERATION_ALGORITHM:return restore_extended_operation(snapshot,reader)
 if isinstance(snapshot,dict) and snapshot.get('algorithm') in (EXTENDED_ALGORITHM,EXTENDED_TAIL_ALGORITHM):return restore_extended(snapshot)
 if isinstance(snapshot,dict) and snapshot.get('algorithm')==GRAPH_ALGORITHM:return restore_curve_graph(snapshot,_restore_frame)
 if isinstance(snapshot,dict) and snapshot.get('algorithm')==OPERATION_ALGORITHM:return restore_operation(snapshot,reader)
 if isinstance(snapshot,dict) and snapshot.get('algorithm') in (TAIL_ALGORITHM,COHERENT_TAIL_ALGORITHM):return _restore_tail(snapshot)
 if isinstance(snapshot,dict) and is_influence_algorithm(snapshot.get('algorithm')):return restore_influence(snapshot,reader)
 if not isinstance(snapshot,dict) or not isinstance(snapshot.get('config'),(dict,MappingProxyType)):raise ValueError('Snapshot with configuration required')
 if set(snapshot)-{'algorithm','config','lineage'}:raise ValueError('Unknown snapshot field')
 cfg=snapshot['config'];algorithm=snapshot.get('algorithm')
 if algorithm in (ALGORITHM,COHERENT_ALGORITHM):
  lineage=snapshot.get('lineage',[])
  if lineage is None:lineage=[]
  if not isinstance(lineage,(list,tuple)):raise ValueError('Invalid branch lineage')
  result=_create_version(cfg,algorithm)
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

from vixen_extended import create_extended,restore_extended,EXTENDED_ALGORITHM,EXTENDED_TAIL_ALGORITHM,MAX_DOMAIN_END

from vixen_extended_coupling import couple_extended,restore_extended_coupling,EXTENDED_COUPLING_ALGORITHM
