"""Shared, non-joining coupling of extended Vixen signals."""
import math
from collections.abc import Mapping
from types import MappingProxyType
from vixen_api import register_multi_curve,register_unary_curve,evaluate_curve,snapshot_curve,latest_write_time,curve_domain,curve_origin,curve_config
from vixen_extended_operations import extended_proxy,extended_public
from vixen_wide import Wide
from vixen_scaled_state import make_scaled_sample

EXTENDED_COUPLING_ALGORITHM='vixen-extended-coupling/1'
_ABSOLUTE=('base','value','guideValue','positiveValue','unconstrainedValue','unconstrainedGuideValue')
_WIDE_FIELDS=_ABSOLUTE+('floorValue','ceilingValue','boundaryWidth')
def _number(x):
 if isinstance(x,bool) or not isinstance(x,(int,float)) or not math.isfinite(x):raise TypeError('Finite number required')
 return x
def _vector(h):
 if not isinstance(h,(list,tuple)) or len(h)!=16:raise TypeError('Sixteen coordinates required')
 if any(abs(_number(v))>1e6 for v in h):raise ValueError('Coordinate outside latent domain')
 return tuple(h)
def _gate(x,a,b):
 if x<=a or x>=b:return 0
 mid=a+(b-a)/2;t=(x-a)/(mid-a) if x<=mid else (b-x)/(b-mid)
 return max(0,min(1,t*t*t*(10+t*(-15+6*t))))
def _state(p):
 out=dict(p)
 for k in _WIDE_FIELDS:
  v=p['scaled'+k[0].upper()+k[1:]];out[k]=None if v is None else Wide.decode(v)
 return out
def _refuse(*args):raise TypeError('Forking a coupled projection has ambiguous branch semantics')
def _raw(rows):
 for p in rows:
  for k in _ABSOLUTE:
   if p[k] is None:raise ValueError('Raw '+k+' unavailable; use sample_normalized()')
 return rows
class _View:
 __slots__=('domain','config','algorithm','_evaluation_state')
 def __init__(self,group,index):
  self.domain=group.domain;self.config=curve_config(group.sources[index]);self.algorithm=EXTENDED_COUPLING_ALGORITHM
  register_unary_curve(self,group,domain=self.domain,latest=latest_write_time(group),origin=curve_origin(group.sources[index]),interval=(-math.inf,math.inf),transform=lambda rows,x,h,explicit:rows[index],snapshot_frame=lambda p:{'algorithm':EXTENDED_COUPLING_ALGORITHM,'operation':'view','group':p[0],'index':index},rebuild=_refuse,tail_source=lambda start:group.sources[index] if start>=group.window[1] else None)
class ExtendedGroup:
 __slots__=('sources','strength','window','domain','algorithm','curves','_evaluation_state','_sealed')
 def __setattr__(self,key,value):
  if getattr(self,'_sealed',False):raise AttributeError('Extended coupling is immutable')
  object.__setattr__(self,key,value)
 def __init__(self,curves,strength,window,restoring):
  if not isinstance(curves,(list,tuple)) or not 2<=len(curves)<=32:raise TypeError('Coupling requires 2..32 curves')
  self.sources=tuple(extended_proxy(c) for c in curves);self.strength=_number(strength)
  if not 0<=strength<=1:raise ValueError('Strength must be in [0,1]')
  if not isinstance(window,(list,tuple)) or len(window)!=2:raise TypeError('Window requires two endpoints')
  a,b=map(_number,window);self.domain=(max(curve_domain(c)[0] for c in self.sources),min(curve_domain(c)[1] for c in self.sources))
  if a>=b or b>self.domain[1] or (not restoring and (a<self.domain[0] or any(a<latest_write_time(c) for c in self.sources))):raise ValueError('Coupling window violates domain or chronology')
  if strength!=0 and any(curve_config(c)['floor']!='none' or curve_config(c)['ceilingMargin'] is not None for c in self.sources):raise ValueError('Coupling requires sources without nonlinear bounds')
  self.window=(a,b);self.algorithm=EXTENDED_COUPLING_ALGORITHM
  register_multi_curve(self,self.sources,domain=self.domain,latest=max(a,*(latest_write_time(c) for c in self.sources)),compute=lambda parents,x,h,explicit:self._combine(parents,x),snapshot_frame=lambda p:{'algorithm':EXTENDED_COUPLING_ALGORITHM,'operation':'group','curves':list(p),'strength':strength,'window':list(self.window)},rebuild=_refuse)
  self.curves=tuple(extended_public(_View(self,i)) for i in range(len(self.sources)));self._sealed=True
 def _check(self,x):
  _number(x)
  if not self.domain[0]<=x<=self.domain[1]:raise ValueError('Coordinate outside shared domain')
 def _combine(self,rows,x):
  weight=self.strength*_gate(x,*self.window)
  if weight==0:return tuple(rows)
  states=tuple(_state(p) for p in rows);ratios=tuple(p['unconstrainedValue'].div(p['base']) for p in states);mean=Wide.from_number(0)
  for r in ratios:mean=mean.add(r)
  mean=mean.div(len(rows));out=[]
  for i,p in enumerate(states):
   if ratios[i].cmp(mean)==0:out.append(rows[i]);continue
   u=p['unconstrainedValue'].mul(1-weight).add(p['base'].mul(mean).mul(weight))
   out.append(make_scaled_sample({**p,'unconstrainedValue':u}))
  return tuple(out)
 def sample_normalized(self,x):self._check(x);return evaluate_curve(self,x)
 def at_normalized(self,x,h):self._check(x);return evaluate_curve(self,x,_vector(h))
 def sample(self,x):return _raw(self.sample_normalized(x))
 def at(self,x,h):return _raw(self.at_normalized(x,h))
 def snapshot(self):return snapshot_curve(self)
 def tail(self,start):self._check(start);return snapshot_curve(self,start)
def couple_extended(curves,strength=1,window=None,restoring=False):
 if not isinstance(restoring,bool):raise TypeError('Invalid restore flag')
 return ExtendedGroup(curves,strength,window,restoring)
def _fields(s,keys):
 if not isinstance(s,Mapping) or set(s)!=set(keys):raise TypeError('Invalid extended coupling frame')
def restore_extended_coupling(s,read):
 if isinstance(s,Mapping) and s.get('operation')=='group':
  _fields(s,('algorithm','operation','curves','strength','window'))
  if s['algorithm']!=EXTENDED_COUPLING_ALGORITHM or not isinstance(s['curves'],(list,tuple)):raise TypeError('Invalid group frame')
  return couple_extended([read(p) for p in s['curves']],s['strength'],s['window'],True)
 _fields(s,('algorithm','operation','group','index'))
 if s['algorithm']!=EXTENDED_COUPLING_ALGORITHM or s['operation']!='view' or isinstance(s['index'],bool) or not isinstance(s['index'],int):raise TypeError('Invalid view frame')
 group=read(s['group']);i=s['index']
 if not isinstance(group,ExtendedGroup) or not 0<=i<len(group.curves):raise TypeError('Invalid group projection')
 return group.curves[i]
