"""Finite replayable guide data."""
import math
from types import MappingProxyType
GUIDE_FORMAT='vixen-guide/1'
def _guide_number(v,lo=-1e250,hi=1e250):
 if isinstance(v,bool) or not isinstance(v,(int,float)) or not lo<=v<=hi or not math.isfinite(v):raise ValueError('Guide number outside finite bounds')
 return v
def _guide_record(v,keys):
 if not hasattr(v,'items') or set(v)-set(keys):raise TypeError('Invalid guide record fields')
def _guide_domain(v):
 if not isinstance(v,(list,tuple)) or len(v)!=2:raise TypeError('Guide domain requires two endpoints')
 a,b=(_guide_number(x,-1e6,1e6) for x in v)
 if a>=b:raise ValueError('Guide domain must increase')
 return (a,b)
def polynomial_guide(coefficients,*,center=0,scale=1,domain=(1,50000)):
 if not isinstance(coefficients,(list,tuple)) or not 1<=len(coefficients)<=65:raise ValueError('Polynomial requires 1..65 coefficients')
 c=tuple(_guide_number(x) for x in coefficients);center=_guide_number(center,-1e6,1e6);scale=_guide_number(scale,1e-6,1e6);domain=_guide_domain(domain)
 return MappingProxyType({'format':GUIDE_FORMAT,'kind':'polynomial','coefficients':c,'center':center,'scale':scale,'domain':domain})
class CompiledGuide:
 __slots__=('spec','domain','_evaluate','_sealed')
 def __setattr__(self,k,v):
  if getattr(self,'_sealed',False):raise AttributeError('Compiled guides are immutable')
  object.__setattr__(self,k,v)
 def __init__(self,spec,evaluate,domain=None):self.spec=spec;self.domain=spec['domain'] if domain is None else domain;self._evaluate=evaluate;self._sealed=True
 def evaluate(self,x):_guide_number(x,*self.domain);return _guide_number(self._evaluate(x))
def compile_guide(data):
 if hasattr(data,'get') and data.get('kind')=='quintic':return _compile_quintic(data)
 keys={'format','kind','coefficients','center','scale','domain'};_guide_record(data,keys)
 if set(data)!=keys or data['format']!=GUIDE_FORMAT or data['kind']!='polynomial':raise TypeError('Invalid polynomial guide')
 spec=polynomial_guide(data['coefficients'],center=data['center'],scale=data['scale'],domain=data['domain'])
 def evaluate(x):
  z=(x-spec['center'])/spec['scale'];y=0
  for c in reversed(spec['coefficients']):y=y*z+c
  return y
 qmax=max(abs((x-spec['center'])/spec['scale']) for x in spec['domain']);bound=0
 for c in reversed(spec['coefficients']):bound=bound*qmax+abs(c)
 _guide_number(bound,0,1e250)
 return CompiledGuide(spec,evaluate)
def taylor_guide(derivatives,*,center=0,domain=(1,50000)):
 p=polynomial_guide(derivatives,center=center,domain=domain);c=[];factorial=1
 for i,d in enumerate(p['coefficients']):
  if i:factorial*=i
  c.append(d/factorial)
 return polynomial_guide(c,center=center,domain=domain)
def sampled_guide(knots):
 if not isinstance(knots,(list,tuple)) or not 2<=len(knots)<=2049:raise ValueError('Sampled guide requires 2..2049 jets')
 out=[];previous=-math.inf
 for p in knots:
  _guide_record(p,{'x','y','d1','d2'})
  if set(p)!={'x','y','d1','d2'}:raise TypeError('Complete knot jets required')
  x=_guide_number(p['x'],-1e6,1e6)
  if x<=previous:raise ValueError('Knots must strictly increase')
  previous=x;out.append(MappingProxyType({'x':x,'y':_guide_number(p['y']),'d1':_guide_number(p['d1']),'d2':_guide_number(p['d2'])}))
 return MappingProxyType({'format':GUIDE_FORMAT,'kind':'quintic','knots':tuple(out)})
def _compile_quintic(data):
 _guide_record(data,{'format','kind','knots'})
 if set(data)!={'format','kind','knots'} or data['format']!=GUIDE_FORMAT:raise TypeError('Invalid quintic guide')
 spec=sampled_guide(data['knots']);k=spec['knots'];segments=[]
 for a,b in zip(k,k[1:]):
  h=b['x']-a['x'];A=a['y'];B=h*a['d1'];C=h*h*a['d2']/2;U=b['y']-(A+B+C);V=h*b['d1']-(B+2*C);W=h*h*b['d2']-2*C
  c=[A,B,C,10*U-4*V+W/2,-15*U+7*V-W,6*U-3*V+W/2]
  for v in c:_guide_number(v,-1e280,1e280)
  _guide_number(sum(abs(v) for v in c),0,1e250)
  segments.append(c)
 def evaluate(x):
  lo=0;hi=len(k)-1
  while lo<hi:
   m=(lo+hi+1)//2
   if k[m]['x']<=x:lo=m
   else:hi=m-1
  if x==k[lo]['x']:return k[lo]['y']
  t=(x-k[lo]['x'])/(k[lo+1]['x']-k[lo]['x']);y=0
  for c in reversed(segments[lo]):y=y*t+c
  return y
 return CompiledGuide(spec,evaluate,(k[0]['x'],k[-1]['x']))
def capture_guide(fn,*,domain=(1,50000),count=129,derivative=None,curvature=None):
 if not callable(fn) or any(f is not None and not callable(f) for f in (derivative,curvature)):raise TypeError('Trusted numeric callbacks required')
 domain=_guide_domain(domain)
 if isinstance(count,bool) or not isinstance(count,int) or not 2<=count<=2049:raise ValueError('Capture count must be 2..2049')
 knots=[]
 for i in range(count):
  x=domain[1] if i==count-1 else domain[0]+(domain[1]-domain[0])*i/(count-1)
  if i and x<=knots[-1]['x']:raise ValueError('Capture grid is not representable')
  knots.append({'x':x,'y':_guide_number(fn(x)),'d1':0,'d2':0})
 for i,p in enumerate(knots):
  if count==2:d1=(knots[1]['y']-knots[0]['y'])/(knots[1]['x']-knots[0]['x']);d2=0
  else:
   j=max(0,min(count-3,i-1));a,b,c=knots[j:j+3];q0=(b['y']-a['y'])/(b['x']-a['x']);q1=(c['y']-b['y'])/(c['x']-b['x']);q2=(q1-q0)/(c['x']-a['x']);d1=q0+q2*((p['x']-a['x'])+(p['x']-b['x']));d2=2*q2
  p['d1']=_guide_number(derivative(p['x']) if derivative is not None else d1);p['d2']=_guide_number(curvature(p['x']) if curvature is not None else d2)
 spec=sampled_guide(knots);compile_guide(spec);return spec
def evaluate_guide(spec,x):return compile_guide(spec).evaluate(x)
