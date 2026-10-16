"""Wide-valued unary operations for native extended Vixen curves."""
import math
import weakref
from collections.abc import Mapping
from types import MappingProxyType
from vixen_api import (evaluate_curve, register_root_curve, register_unary_curve, latest_write_time,
    curve_origin, curve_lineage, curve_config, curve_domain, curve_influence_head,
    append_influence, materialize_influences, snapshot_curve, fork_curve)
from vixen_wide import Wide
from vixen_scaled_state import apply_wide_policy, make_scaled_sample
from vixen_guide import compile_guide

EXTENDED_OPERATION_ALGORITHM = 'vixen-extended-operations/1'
_MAX = 2**40
_ABS = ('base','value','guideValue','positiveValue','unconstrainedValue','unconstrainedGuideValue')
_WIDE_FIELDS = ('base','value','guideValue','positiveValue','unconstrainedValue','unconstrainedGuideValue','floorValue','ceilingValue','boundaryWidth')
_CONSERVATION = MappingProxyType({'kind':'finite-precision-tolerance','potential':'t^4*(1-t)^4*P(t)','perturbation':'d/dt potential','claim':'Finite-precision conservation is tolerance-qualified; no exact-real claim'})
_ONE, _ZERO = Wide.from_number(1), Wide.from_number(0)

def _finite(v, name):
    if isinstance(v,bool) or not isinstance(v,(int,float)) or not math.isfinite(v): raise ValueError(name+' must be finite')
    return float(v)
def _fields(v, allowed, required=(), name='record'):
    if not isinstance(v,Mapping) or set(v)-set(allowed) or set(required)-set(v): raise TypeError('Invalid '+name+' fields')
def _domain(v, low=1, high=_MAX):
    if not isinstance(v,(list,tuple)) or len(v)!=2: raise TypeError('Domain requires two endpoints')
    a,b=(_finite(x,'domain endpoint') for x in v)
    if a<low or b>high or a>=b: raise ValueError('Domain is outside its allowed interval')
    return (a,b)
def extended_polynomial_guide(coefficients, options=None):
    options={} if options is None else options
    _fields(options,('center','scale','domain'))
    if not isinstance(coefficients,(list,tuple)) or not 1<=len(coefficients)<=65: raise ValueError('Polynomial requires 1..65 coefficients')
    c=tuple(_finite(x,'coefficient') for x in coefficients)
    if any(abs(x)>1e250 for x in c): raise ValueError('Guide coefficient outside finite bounds')
    center=_finite(options.get('center',0),'center');scale=_finite(options.get('scale',1),'scale')
    if abs(center)>_MAX or not 1e-6<=scale<=1e6: raise ValueError('Invalid extended guide center or scale')
    return MappingProxyType({'format':'vixen-extended-guide/1','kind':'polynomial','coefficients':c,'center':center,'scale':scale,'domain':_domain(options.get('domain',(1,_MAX)))} )
def _compile_target(target):
    if not isinstance(target,Mapping): raise TypeError('Numeric guide data is required')
    if target.get('format')=='vixen-extended-guide/1':
        keys=('format','kind','coefficients','center','scale','domain');_fields(target,keys,keys,'extended guide')
        if target['kind']!='polynomial': raise TypeError('Unsupported extended guide kind')
        spec=extended_polynomial_guide(target['coefficients'],{'center':target['center'],'scale':target['scale'],'domain':target['domain']})
        def evaluate(x):
            x=_finite(x,'guide coordinate')
            if not spec['domain'][0]<=x<=spec['domain'][1]: raise ValueError('Guide coordinate outside its domain')
            z=Wide.from_number(x-spec['center']).div(Wide.from_number(spec['scale']));y=_ZERO
            for c in reversed(spec['coefficients']): y=y.mul(z).add(Wide.from_number(c))
            return y
        return spec, spec['domain'], all(x==0 for x in spec['coefficients']), evaluate
    g=compile_guide(target);spec=g.spec
    zero=all(x==0 for x in spec['coefficients']) if spec['kind']=='polynomial' else all(k['y']==k['d1']==k['d2']==0 for k in spec['knots'])
    return spec,tuple(g.domain),zero,lambda x:Wide.from_number(g.evaluate(x))
def _vector(v):
    if not isinstance(v,(list,tuple)) or len(v)!=16 or any(isinstance(x,bool) or not isinstance(x,(int,float)) or not math.isfinite(x) or abs(x)>1e6 for x in v): raise ValueError('Coordinates must be 16 finite numbers in [-1000000,1000000]')
    return tuple(v)
def _read_wide(p):
    r=dict(p)
    for k in _WIDE_FIELDS:
        key='scaled'+k[0].upper()+k[1:]
        if key not in p: raise TypeError('Missing '+key)
        r[k]=None if p[key] is None else Wide.decode(p[key])
    return r
def _scaled(state): return make_scaled_sample(apply_wide_policy(state))
def _smooth5(t): return 0.0 if t<=0 else 1.0 if t>=1 else t*t*t*(10+t*(-15+6*t))
def _ramp(t): return 0.0 if t<=0 else 1.0 if t>=1 else t*t*t*(6+t*(-8+3*t))
def _weight(x,r):
    at,amount,width=r['at'],r['amount'],r['handoffWidth']
    if x<=at:return 0.0
    if not r['windowed']:return amount*_smooth5((x-at)/width)
    if x<at+width:return amount*_smooth5((x-at)/width)
    if r['releaseWidth']==0:return amount if x<r['until'] else 0.0
    if x<=r['until']:return amount
    end=r['until']+r['releaseWidth']
    return amount*(1-_smooth5((x-r['until'])/r['releaseWidth'])) if x<end else 0.0
def _normalize_influence(parent,target,options,restoring):
    _fields(options,('at','amount','handoffWidth','count','until','releaseWidth','reference','mode'))
    if 'count' in options:raise TypeError('Callback capture is not supported by numeric extended operations')
    lo,hi=curve_domain(parent);cfg=curve_config(parent);last=latest_write_time(parent)
    at=_finite(options.get('at',max(cfg['openingEndpoint'],last,lo)),'activation')
    if at<(1 if restoring else lo) or at>hi or (not restoring and at<last):raise ValueError('Influence activation violates chronology or domain')
    amount=_finite(options.get('amount',.5),'amount')
    if not 0<=amount<=1:raise ValueError('Influence amount must lie in [0,1]')
    mode=options.get('mode','blend');reference=options.get('reference','baseline' if mode=='add' else 'value')
    if mode not in ('blend','add') or reference not in ('value','baseline','backbone'):raise TypeError('Invalid influence mode or reference')
    spec,gd,zero,guide=_compile_target(target);windowed='until' in options or 'releaseWidth' in options
    if windowed and not ('until' in options and 'releaseWidth' in options):raise TypeError('Window requires until and releaseWidth')
    limit=min(hi,gd[1]);width=_finite(options.get('handoffWidth',min(16,limit-at)),'handoffWidth');end=at+width
    if width<=0 or width>50000 or end<=at or end>hi or gd[0]>at or end>limit:raise ValueError('Guide must cover the influence handoff')
    until=release=release_end=None
    if windowed:
        until=_finite(options['until'],'until');release=_finite(options['releaseWidth'],'releaseWidth')
        if release<=0 or release>50000 or until<end or until>hi:raise ValueError('Invalid influence release window')
        release_end=until+release
        if release_end<=until or release_end>hi or gd[1]<release_end:raise ValueError('Guide must cover active window through release')
    recipe=MappingProxyType({'at':at,'amount':amount,'handoffWidth':width,'windowed':windowed,'until':until,'releaseWidth':release,'releaseEnd':release_end,'reference':reference,'mode':mode,'guide':spec})
    return recipe,guide,zero and mode=='add',(lo,hi if windowed else limit)
def _affected(p,target,amount,cfg,reference,mode):
    if amount==0:return p
    q=_read_wide(p);base=q['base'];extra=base.mul(Wide.from_number(amount)).mul(Wide.from_number(cfg['boundarySoftness']));width=q['boundaryWidth'];lo,hi=q['floorValue'],q['ceilingValue']
    if lo is not None and hi is not None:
        room=hi.sub(lo).scale(-1).sub(width)
        if room.cmp(_ZERO)>0 and extra.cmp(_ZERO)>0:width=width.add(extra.mul(room).div(room.add(extra)))
    elif lo is not None or hi is not None:width=width.add(extra)
    u=q['unconstrainedValue']
    if mode=='add':u=u.add(target.mul(Wide.from_number(amount)) if reference=='value' else base.mul(Wide.from_number(amount)).mul(target))
    elif reference=='value':u=target if amount==1 else u.mul(Wide.from_number(1-amount)).add(target.mul(Wide.from_number(amount)))
    elif reference=='baseline':
        t=base.mul(_ONE.add(target));u=t if amount==1 else u.mul(Wide.from_number(1-amount)).add(t.mul(Wide.from_number(amount)))
    else:
        t=q['unconstrainedGuideValue'].add(base.mul(target));u=t if amount==1 else u.mul(Wide.from_number(1-amount)).add(t.mul(Wide.from_number(amount)))
    return _scaled({**q,'boundaryWidth':width,'unconstrainedValue':u})
_FACTOR=(0,0,0,0,1,-4,6,-4,1)
def _product(a,b):
    out=[0.0]*(len(a)+len(b)-1)
    for i,x in enumerate(a):
        for j,y in enumerate(b):out[i+j]+=x*y
    if any(not math.isfinite(x) for x in out):raise ValueError('Redistribution coefficients exceed finite range')
    return out
def _derivative(c):
    out=[x*(i+1) for i,x in enumerate(c[1:])]
    return out or [0.0]
def _horner(c,t):
    y=0.0
    for x in reversed(c):y=y*t+x
    return y
def _normalize_impulse(inp,d,restoring=False):
    _fields(inp,('type','at','magnitude','relaxation','attack'),('at','magnitude','relaxation'),'impulse recipe')
    if inp.get('type','impulse')!='impulse':raise TypeError('Operation and recipe disagree')
    at=_finite(inp['at'],'at');mag=_finite(inp['magnitude'],'magnitude');relax=_finite(inp['relaxation'],'relaxation');attack=_finite(inp.get('attack',relax/8),'attack');rise=at+attack;end=rise+relax
    if attack<=0 or relax<=0 or rise<=at or end<=rise or not math.isfinite(end) or at<(1 if restoring else d[0]) or rise>d[1] or end>d[1] or end<=d[0]:raise ValueError('Impulse support must lie inside the available domain')
    return MappingProxyType({'type':'impulse','at':at,'magnitude':mag,'relaxation':relax,'attack':attack})
def _normalize_redistribution(inp,d,restoring=False):
    _fields(inp,('type','from','until','coefficients','magnitude','measure','certificate','conservation'),('from','until','coefficients'),'redistribution recipe')
    if inp.get('type','redistribution')!='redistribution':raise TypeError('Invalid redistribution recipe')
    start=_finite(inp['from'],'from');end=_finite(inp['until'],'until');mag=_finite(inp.get('magnitude',1),'magnitude');measure=inp.get('measure','actualDeviation');c=inp['coefficients']
    if not isinstance(c,(list,tuple)) or not 1<=len(c)<=64:raise ValueError('Shape requires 1..64 coefficients')
    c=tuple(_finite(x,'shape coefficient') for x in c)
    if end<=start or start+(end-start)<=start or start<(1 if restoring else d[0]) or end>d[1] or (restoring and end<=d[0]):raise ValueError('Redistribution window must lie inside available domain')
    if measure not in ('actualDeviation','unconstrainedDeviation'):raise TypeError('Invalid redistribution measure')
    if 'conservation' in inp and inp['conservation']!=_CONSERVATION:raise TypeError('Invalid redistribution conservation metadata')
    radius=abs(mag)*sum(abs(x) for x in _derivative(_product(_FACTOR,c)))
    if not math.isfinite(radius):raise ValueError('Redistribution bound exceeds finite range')
    return MappingProxyType({'type':'redistribution','from':start,'until':end,'coefficients':c,'magnitude':mag,'measure':measure,'conservation':_CONSERVATION})
def _delta(r,x):
    if r['magnitude']==0:return 0.0
    if r['type']=='impulse':
        rise=r['at']+r['attack'];end=rise+r['relaxation']
        if x<=r['at'] or x>=end:return 0.0
        return r['magnitude']*_smooth5((x-r['at'])/r['attack']) if x<=rise else r['magnitude']*(1-_smooth5((x-rise)/r['relaxation']))
    if x<=r['from'] or x>=r['until']:return 0.0
    t=(x-r['from'])/(r['until']-r['from']);f=_horner(_derivative(_FACTOR),t);p=_horner(r['coefficients'],t)
    return r['magnitude']*(f*p+_horner(_FACTOR,t)*_horner(_derivative(r['coefficients']),t))
def _check_constraints(parent,r):
    cfg=curve_config(parent)
    if r['type']=='redistribution' and r['measure']=='actualDeviation' and r['magnitude']!=0 and (cfg['floor']!='none' or cfg['ceilingMargin'] is not None):raise ValueError('Actual-deviation redistribution requires an unconstrained source')
class _NativeProxy:
    __slots__=('source','config','domain','lineage','algorithm','available_from','_evaluation_state','__weakref__')
    def __init__(self,source):
        self.source=source;self.config=source.config;self.domain=tuple(source.domain);self.lineage=source.lineage;self.algorithm=source.algorithm;self.available_from=self.domain[0]
        latest=1
        for e in source.lineage:latest=max(latest,e['at'] if isinstance(e,Mapping) else e.at)
        for e in getattr(source,'influences',()):latest=max(latest,e['at'] if isinstance(e,Mapping) else e.at)
        register_root_curve(self,self.domain,latest)
    def sample(self,x):return self.source.sample_normalized(x)
    def at(self,x,h):return self.source.at_normalized(x,h)
    def snapshot(self):return self.source.snapshot()
    def tail(self,x):return self.source.tail(x)
    def fork(self,**o):return _root_proxy(self.source.fork(**o))
    def location(self,x):return self.source.location(x)
    def inspect(self,x):return self.source.inspect(x)
    def ridges(self):return self.source.ridges()
    @property
    def dips(self):return self.source.dips
class _OperationNode:
    __slots__=('domain','config','algorithm','_evaluation_state')
    def __init__(self,parent,kind,recipe,domain,latest,start,end,identity,transform,rebuild):
        self.domain=domain;self.config=curve_config(parent);self.algorithm=EXTENDED_OPERATION_ALGORITHM
        register_unary_curve(self,parent,domain=domain,latest=latest,origin=curve_origin(parent),influence_head=curve_influence_head(parent),interval=(start,end),always_identity=identity,identity_at=lambda x:identity or x<=start or x>=end,
            transform=lambda p,x,h,explicit:transform(p,x),snapshot_frame=lambda p:{'algorithm':EXTENDED_OPERATION_ALGORITHM,'operation':kind,'curve':p[0],'recipe':_plain(recipe)},discard_on_tail=lambda x:end!=math.inf and end<=x,rebuild=lambda p:rebuild(p[0]))
    def sample(self,x):return evaluate_curve(self,x)
    def at(self,x,h):return evaluate_curve(self,x,h)
    def snapshot(self):return snapshot_curve(self)
    def tail(self,x):return snapshot_curve(self,x)
    def fork(self,**o):return _public_for(fork_curve(self,o))
def _plain(v):
    if isinstance(v,Mapping):return {k:_plain(x) for k,x in v.items()}
    if isinstance(v,(tuple,list)):return [_plain(x) for x in v]
    return v
_NATIVE=weakref.WeakValueDictionary();_PUBLIC=weakref.WeakKeyDictionary();_PRIVATE=weakref.WeakValueDictionary()
def _root_proxy(source):
    try:return _NATIVE[source]
    except (KeyError,TypeError):pass
    if not hasattr(source,'sample_normalized') or not hasattr(source,'at_normalized'):raise TypeError('A native extended curve is required')
    p=_NativeProxy(source);_NATIVE[source]=p;return p
def extended_proxy(curve):return _PUBLIC[_api_for(curve)]
def extended_public(node):return _public_for(node)
def _api_for(curve):return curve if curve in _PUBLIC else _public_for(_root_proxy(curve),curve)
def _public_for(node,source=None):
    try:return _PRIVATE[node]
    except (KeyError,TypeError):pass
    api=ExtendedOperations(node,source);_PRIVATE[node]=api;_PUBLIC[api]=node;return api
def _make_influence(parent,target,options,restoring=False):
    r,g,zero,dom=_normalize_influence(parent,target,options,restoring);cfg=curve_config(parent);entry={'at':r['at'],'amount':r['amount'],'handoffWidth':r['handoffWidth'],'guide':r['guide'],'reference':r['reference'],'mode':r['mode']}
    if r['windowed']:entry.update(until=r['until'],releaseWidth=r['releaseWidth'])
    append_influence(parent,MappingProxyType(entry));end=r['releaseEnd'] if r['windowed'] else math.inf
    def run(p,x):
        a=_weight(x,r)
        return p if a==0 or zero else _affected(p,g(x),a,cfg,r['reference'],r['mode'])
    options2={'at':r['at'],'amount':r['amount'],'handoffWidth':r['handoffWidth'],'reference':r['reference'],'mode':r['mode']}
    if r['windowed']:options2.update(until=r['until'],releaseWidth=r['releaseWidth'])
    return _OperationNode(parent,'influence',{'guide':r['guide'],**options2},dom,max(latest_write_time(parent),r['at']),r['at'],end,r['amount']==0 or zero,run,lambda p:_make_influence(p,r['guide'],options2,True))
def _make_operation(parent,r):
    _check_constraints(parent,r);start=r['at'] if r['type']=='impulse' else r['from'];end=start+r['attack']+r['relaxation'] if r['type']=='impulse' else r['until']
    def run(p,x):
        d=_delta(r,x)
        if d==0:return p
        q=_read_wide(p);return _scaled({**q,'unconstrainedValue':q['unconstrainedValue'].add(q['base'].mul(Wide.from_number(d)))})
    return _OperationNode(parent,r['type'],r,curve_domain(parent),max(latest_write_time(parent),start),start,end,r['magnitude']==0,run,lambda p:_make_operation(p,r))
class ExtendedOperations:
    __slots__=('_node','_source','__weakref__')
    def __init__(self,node,source=None):self._node=node;self._source=source
    @property
    def algorithm(self):return getattr(self._source,'algorithm',EXTENDED_OPERATION_ALGORITHM)
    @property
    def config(self):return curve_config(self._node)
    @property
    def domain(self):return curve_domain(self._node)
    @property
    def normalized_domain(self):return self.domain
    @property
    def available_from(self):return self.domain[0]
    @property
    def lineage(self):return curve_lineage(self._node)
    @property
    def influences(self):return materialize_influences(curve_influence_head(self._node))
    def _check(self,x):
        if isinstance(x,bool) or not isinstance(x,(int,float)) or not math.isfinite(x) or not self.domain[0]<=x<=self.domain[1]:raise ValueError('Coordinate outside available domain')
    def sample_normalized(self,x):self._check(x);return evaluate_curve(self._node,x)
    def at_normalized(self,x,h):self._check(x);return evaluate_curve(self._node,x,_vector(h))
    def sample(self,x):
        p=self.sample_normalized(x)
        for k in _ABS:
            if p[k] is None:raise ValueError('Raw '+k+' unavailable; use sample_normalized()')
        return p
    def at(self,x,h):
        p=self.at_normalized(x,h)
        for k in _ABS:
            if p[k] is None:raise ValueError('Raw '+k+' unavailable; use at_normalized()')
        return p
    def influence(self,target,**options):return with_extended_influence(self,target,options)
    def impulse(self,recipe):return with_extended_impulse(self,recipe)
    def redistribute(self,window,shape):
        recipe={'from':window[0],'until':window[1],**({'coefficients':shape} if isinstance(shape,(list,tuple)) else dict(shape))}
        return with_extended_redistribution(self,recipe)
    def fork(self,*,at,operation,handoffWidth=None):return _public_for(fork_curve(self._node,{'at':at,'operation':operation,'handoffWidth':handoffWidth}))
    def snapshot(self):return snapshot_curve(self._node)
    def tail(self,from_):self._check(from_);return snapshot_curve(self._node,from_)
    def location(self,x):self._check(x);return curve_origin(self._node).location(x)
    def inspect(self,x):self._check(x);return curve_origin(self._node).inspect(x)
    def ridges(self):return curve_origin(self._node).ridges()
    @property
    def dips(self):return curve_origin(self._node).dips

def with_extended_influence(curve,target,options=None,restoring=False):
    if not isinstance(restoring,bool):raise TypeError('restoring must be boolean')
    api=_api_for(curve);return _public_for(_make_influence(_PUBLIC[api],target,{} if options is None else options,restoring))
def with_extended_impulse(curve,recipe,restoring=False):
    if not isinstance(restoring,bool):raise TypeError('restoring must be boolean')
    api=_api_for(curve);node=_PUBLIC[api];r=_normalize_impulse(recipe,curve_domain(node),restoring)
    if not restoring and r['at']<latest_write_time(node):raise ValueError('Operation precedes the latest authored coordinate')
    return _public_for(_make_operation(node,r))
def with_extended_redistribution(curve,recipe,restoring=False):
    if not isinstance(restoring,bool):raise TypeError('restoring must be boolean')
    api=_api_for(curve);node=_PUBLIC[api];r=_normalize_redistribution(recipe,curve_domain(node),restoring)
    if not restoring and r['from']<latest_write_time(node):raise ValueError('Operation precedes the latest authored coordinate')
    return _public_for(_make_operation(node,r))
def _validate_tree(root):
    active=set();stack=[(root,False,0)];count=0
    while stack:
        v,leave,depth=stack.pop()
        if leave:active.remove(id(v));continue
        if v is None or isinstance(v,(str,bool)):continue
        if isinstance(v,(int,float)):
            if isinstance(v,bool) or not math.isfinite(v):raise ValueError('Snapshot contains invalid number')
            continue
        if not isinstance(v,(Mapping,list,tuple)) or depth>128 or count>100000:raise TypeError('Snapshot contains unsupported or excessive data')
        count+=1;ident=id(v)
        if ident in active:raise ValueError('Snapshot cycles are not allowed')
        active.add(ident);stack.append((v,True,depth));children=list(v.values()) if isinstance(v,Mapping) else v
        stack.extend((x,False,depth+1) for x in reversed(children))
def restore_extended_operation(snapshot,read):
    _validate_tree(snapshot)
    if not callable(read):raise TypeError('A parent read function is required')
    _fields(snapshot,('algorithm','operation','curve','recipe'),('algorithm','operation','curve','recipe'),'operation snapshot')
    if snapshot['algorithm']!=EXTENDED_OPERATION_ALGORITHM:raise TypeError('Unsupported extended operation algorithm')
    parent=_api_for(read(snapshot['curve']));r=snapshot['recipe']
    if snapshot['operation']=='influence':
        _fields(r,('guide','at','amount','handoffWidth','reference','mode','until','releaseWidth'),('guide','at','amount','handoffWidth','reference','mode'),'influence recipe')
        opts={k:r[k] for k in ('at','amount','handoffWidth','reference','mode')}
        if 'until' in r and 'releaseWidth' in r:opts.update(until=r['until'],releaseWidth=r['releaseWidth'])
        elif 'until' in r or 'releaseWidth' in r:raise TypeError('Incomplete influence window')
        return with_extended_influence(parent,r['guide'],opts,True)
    if snapshot['operation']=='impulse':
        _fields(r,('type','at','magnitude','relaxation','attack'),('type','at','magnitude','relaxation','attack'),'impulse recipe')
        if r['type']!='impulse':raise TypeError('Operation and recipe disagree')
        return with_extended_impulse(parent,r,True)
    if snapshot['operation']=='redistribution':
        _fields(r,('type','from','until','coefficients','magnitude','measure','conservation'),('type','from','until','coefficients','magnitude','measure','conservation'),'redistribution recipe')
        return with_extended_redistribution(parent,r,True)
    raise TypeError('Unknown extended operation kind')
