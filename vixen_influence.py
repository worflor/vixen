"""Replayable external influences over a Vixen timeline."""
import math
from types import MappingProxyType
from vixen_guide import compile_guide,capture_guide
INFLUENCE_ALGORITHM='vixen-influence-c2/1'
def _finite(v,lo,hi):
 if isinstance(v,bool) or not isinstance(v,(int,float)) or not lo<=v<=hi or not math.isfinite(v):raise ValueError('Influence number outside its finite domain')
def _gate(t):return 0 if t<=0 else 1 if t>=1 else t*t*t*(10+t*(-15+6*t))
def _ramp(t):return 0 if t<=0 else t if t>=1 else t*t*t*(6+t*(-8+3*t))
def _policy(y,p):
 lo,hi,e=p['floorValue'],p['ceilingValue'],p['boundaryWidth']
 if lo is not None and hi is not None and hi<=lo:return lo
 if lo is not None:
  if y<=lo:y=lo
  elif e>0 and y<lo+e:y=lo+e*_ramp((y-lo)/e)
 if hi is not None:
  if y>=hi:y=hi
  elif e>0 and y>hi-e:y=hi-e*_ramp((hi-y)/e)
 return y
def _affected(p,target,a,cfg):
 if a==0:return p
 extra=p['base']*a*cfg['boundarySoftness'];lo=p['floorValue'];hi=p['ceilingValue'];width=p['boundaryWidth']
 if lo is not None and hi is not None:
  room=max(0,(hi-lo)/2-width)
  if extra>0 and room>0:width+=extra*room/(room+extra)
 elif lo is not None or hi is not None:width+=extra
 p={**p,'boundaryWidth':width};guide=_policy(p['unconstrainedGuideValue'],p)
 u=target if a==1 else (1-a)*p['unconstrainedValue']+a*target;value=_policy(u,p);d=(value-p['base'])/p['base'];positive=math.exp(.55*math.tanh(d))
 return {**p,'guideValue':guide,'guideDeviation':(guide-p['base'])/p['base'],'value':value,'unconstrainedValue':u,'unconstrainedDeviation':(u-p['base'])/p['base'],'deviation':d,'multiplier':1+d,'activity':.5+.5*math.tanh(d),'positiveMultiplier':positive,'positiveValue':p['base']*positive,'constraintApplied':value!=u}
def _latest(curve):return max([1,*[n['at'] for n in curve.lineage],*[n['at'] for n in getattr(curve,'influences',())]])
def _plain(v):
 if hasattr(v,'items'):return {k:_plain(x) for k,x in v.items()}
 if isinstance(v,(list,tuple)):return [_plain(x) for x in v]
 return v
class Influenced:
 __slots__=('_curve','_compiled','_entry','_domain','_influences','_bounds','_sealed')
 def __setattr__(self,k,v):
  if getattr(self,'_sealed',False):raise AttributeError('Influence timelines are immutable')
  object.__setattr__(self,k,v)
 def __init__(self,curve,target,options,carried=False):
  if set(options)-{'at','amount','handoffWidth','count'}:raise TypeError('Influence options: at, amount, handoffWidth, count')
  if len(curve.lineage)+len(getattr(curve,'influences',()))>=32:raise ValueError('Maximum timeline composition depth: 32')
  at=options.get('at',max(curve.config['openingEndpoint'],_latest(curve)));amount=options.get('amount',.5);_finite(at,*curve.domain);_finite(amount,0,1)
  if not carried and at<_latest(curve):raise ValueError('Influence cannot precede existing timeline events')
  if callable(target):target=capture_guide(target,domain=[at,curve.domain[1]],count=options.get('count',129))
  elif 'count' in options:raise TypeError('count applies only to callback capture')
  compiled=compile_guide(target);end=min(curve.domain[1],compiled.domain[1]);width=options.get('handoffWidth',min(16,end-at));_finite(width,0,50000)
  if width<=0 or compiled.domain[0]>at or at+width<=at or at+width>end:raise ValueError('Guide must cover a representable handoff inside the domain')
  self._curve=curve;self._compiled=compiled;self._domain=(curve.domain[0],end);self._entry=MappingProxyType({'at':at,'amount':amount,'handoffWidth':width,'guide':compiled.spec});self._influences=(*getattr(curve,'influences',()),self._entry)
  c=curve.config;lower=-1e280 if c['floor']=='none' else -1 if c['floor']=='zero' else 0 if c['floor']=='baseline' else -.25*c['strength'];upper=1e280 if c['ceilingMargin'] is None else (0 if c['floor']=='baseline' else .25*c['strength'])+c['ceilingMargin'];self._bounds=MappingProxyType({**curve.bounds,'deviation':(lower,upper),'multiplier':(1+lower,1+upper)});self._sealed=True
 @property
 def algorithm(self):return INFLUENCE_ALGORITHM
 @property
 def config(self):return self._curve.config
 @property
 def bounds(self):return self._bounds
 @property
 def domain(self):return self._domain
 @property
 def lineage(self):return self._curve.lineage
 @property
 def influences(self):return self._influences
 @property
 def dips(self):return self._curve.dips
 def _weight(self,x):return self._entry['amount']*_gate((x-self._entry['at'])/self._entry['handoffWidth'])
 def _adjust(self,p,x):
  a=self._weight(x);return p if a==0 else _affected(p,self._compiled.evaluate(x),a,self.config)
 def sample(self,x):_finite(x,*self.domain);return self._adjust(self._curve.sample(x),x)
 def at(self,x,h):_finite(x,*self.domain);return self._adjust(self._curve.at(x,h),x)
 def location(self,x):_finite(x,*self.domain);return self._curve.location(x)
 def inspect(self,x):_finite(x,*self.domain);return self._curve.inspect(x)
 def ridges(self):return self._curve.ridges()
 def influence_at(self,x):
  _finite(x,*self.domain);w=self._weight(x);target=None if w==0 else self._compiled.evaluate(x)
  return {'weight':w,'targetValue':target,'sourceValue':self._curve.sample(x)['value'],'followingError':None if target is None else self.sample(x)['value']-target}
 def snapshot(self):return {'algorithm':INFLUENCE_ALGORITHM,'curve':self._curve.snapshot(),**_plain(self._entry)}
 def fork(self,*,at,operation,handoffWidth=None):
  _finite(at,max(self.domain[0],_latest(self)),self.domain[1]);width=min(16,self.domain[1]-at) if handoffWidth is None else handoffWidth;_finite(width,0,50000)
  if width<=0 or at+width>self.domain[1]:raise ValueError('Fork exceeds guide domain')
  parent=self._curve.fork(at=at,operation=operation,handoffWidth=width);return Influenced(parent,self._compiled.spec,{k:self._entry[k] for k in ('at','amount','handoffWidth')},True)
 def influence(self,target,**options):return Influenced(self,target,options)
def with_influence(curve,target,**options):return Influenced(curve,target,options)
def restore_influence(snapshot,restore):
 frames=[];seen=set();s=snapshot
 while isinstance(s,dict) and s.get('algorithm')==INFLUENCE_ALGORITHM:
  if id(s) in seen or len(frames)>=32 or set(s)!={'algorithm','curve','guide','at','amount','handoffWidth'}:raise ValueError('Invalid influence snapshot fields or composition')
  seen.add(id(s));frames.append(s);s=s['curve']
 result=restore(s)
 for f in reversed(frames):result=Influenced(result,f['guide'],{k:f[k] for k in ('at','amount','handoffWidth')},True)
 return result
