"""Native extended-domain coherent roots, forks, and bounded checkpoint replay."""
import math
import re
import struct
from collections.abc import Mapping
from types import MappingProxyType, SimpleNamespace
from vixen_wide import Wide
from vixen_exponential import wide_exp
from vixen_scaled_state import apply_wide_policy, make_scaled_sample
from vixen_checkpoint import history_witness
from vixen_timeline import append as _append, before as _before, entries as _entries

EXTENDED_ALGORITHM = 'vixen-hyperfield-c2/6'
EXTENDED_TAIL_ALGORITHM = 'vixen-tail-c2/4'
MAX_DOMAIN_END = 2**40
_MIX_FIELDS = ('x','value','base','score','guideScore','rawDeviation','guideValue','stabilizationWeight','gate','unconstrainedValue','unconstrainedGuideValue','boundaryWidth')
_ABS_FIELDS = ('base','value','guideValue','positiveValue','unconstrainedValue','unconstrainedGuideValue')
def _extended_gate(t):return 0.0 if t<=0 else 1.0 if t>=1 else t*t*t*(10+t*(-15+6*t))

def _legacy_modules():
    from vixen import create_coherent, seed32, hash32
    from vixen_calculus import native_normalized
    return create_coherent, seed32, hash32, native_normalized

def _vector(v):
    if not isinstance(v,(list,tuple)) or len(v)!=16 or any(isinstance(n,bool) or not isinstance(n,(int,float)) or not math.isfinite(n) or abs(n)>1e6 for n in v):
        raise ValueError('Coordinates must be 16 finite numbers in [-1000000,1000000]')
    return tuple(v)
def _coordinate(x,end):
    if isinstance(x,bool) or not isinstance(x,(int,float)) or not math.isfinite(x) or not 1<=x<=end:
        raise ValueError(f'x must be finite in [1,{end}]')
def _source(config):
    create_coherent,_,_,_=_legacy_modules()
    curve=create_coherent(**dict(config))
    facade=SimpleNamespace(config=curve.config,dips=curve.dips,ridges=curve.ridges,domain=curve.domain,sample=curve.sample,at=curve.at)
    return SimpleNamespace(curve=curve,facade=facade,config=curve.config,domain=curve.domain)
def _fork_seed(parent_seed,operation,at):
    _,seed32,hash32,_=_legacy_modules()
    hi,lo=struct.unpack('>II',struct.pack('>d',at))
    return hash32(hash32(seed32(parent_seed),1 if isinstance(parent_seed,str) else 2,seed32(operation)),hi,lo)
def _raw_state(p):
    def w(k):return None if p[k] is None else Wide.from_number(p[k])
    return {**p,'base':w('base'),'value':w('value'),'guideValue':w('guideValue'),'positiveValue':w('positiveValue'),
            'unconstrainedValue':w('unconstrainedValue'),'unconstrainedGuideValue':w('unconstrainedGuideValue'),
            'floorValue':w('floorValue'),'ceilingValue':w('ceilingValue'),'boundaryWidth':w('boundaryWidth')}
def _wide_leaf(source,x,h):
    _,_,_,native_normalized=_legacy_modules()
    p=native_normalized(source.facade,x,h);c=source.config;endpoint=c['openingEndpoint']
    clock=x if endpoint==100 else 1+(x-1)*99/(endpoint-1)
    base=Wide.from_number(clock).mul(wide_exp(clock/100))
    lo=None if p['floorRatio'] is None else base.mul(Wide.from_number(p['floorRatio']))
    hi=None if p['ceilingRatio'] is None else base.mul(Wide.from_number(p['ceilingRatio']))
    width=base.mul(Wide.from_number(p['boundaryWidthRatio']))
    un=base.add(base.mul(Wide.from_number(p['unconstrainedDeviation'])))
    ung=base.add(base.mul(Wide.from_number(p['unconstrainedGuideDeviation'])))
    state={**p,'base':base,'floorValue':lo,'ceilingValue':hi,'boundaryWidth':width,
           'unconstrainedValue':un,'unconstrainedGuideValue':ung,'value':un,'guideValue':ung,
           'positiveValue':base.mul(Wide.from_number(p['positiveMultiplier']))}
    return apply_wide_policy(state)
def _ramp(t):return 0.0 if t<=0 else t if t>=1 else t*t*t*(6+t*(-8+3*t))
def _finite_policy(y,lo,hi,width):
    if lo is not None and hi is not None and hi<=lo:return lo
    if lo is not None:y=lo if y<=lo else y if width==0 or y>=lo+width else lo+width*_ramp((y-lo)/width)
    if hi is not None:y=hi if y>=hi else y if width==0 or y<=hi-width else hi-width*_ramp((hi-y)/width)
    return y
def _mix_finite(a,b,w):
    r=dict(a)
    for key in _MIX_FIELDS:r[key]=a[key]+(b[key]-a[key])*w
    for key in ('floorValue','ceilingValue'):
        if a[key] is not None:r[key]=a[key]+(b[key]-a[key])*w
    r['value']=_finite_policy(r['unconstrainedValue'],r['floorValue'],r['ceilingValue'],r['boundaryWidth'])
    r['guideValue']=_finite_policy(r['unconstrainedGuideValue'],r['floorValue'],r['ceilingValue'],r['boundaryWidth'])
    d=(r['value']-r['base'])/r['base'];gd=(r['guideValue']-r['base'])/r['base']
    r.update(deviation=d,multiplier=1+d,activity=.5+.5*math.tanh(d),positiveMultiplier=math.exp(.55*math.tanh(d)),
             positiveValue=r['base']*math.exp(.55*math.tanh(d)),guideDeviation=gd,
             unconstrainedDeviation=(r['unconstrainedValue']-r['base'])/r['base'],
             unconstrainedGuideDeviation=(r['unconstrainedGuideValue']-r['base'])/r['base'],constraintApplied=r['value']!=r['unconstrainedValue'])
    return r
def _blend(a,b,w):return a.add(b.sub(a).mul(Wide.from_number(w)))
def _mix_wide(a,b,w):
    r=dict(a)
    for key in ('base','unconstrainedValue','unconstrainedGuideValue','boundaryWidth'):r[key]=_blend(a[key],b[key],w)
    for key in ('floorValue','ceilingValue'):
        if (a[key] is None)!=(b[key] is None):raise TypeError('Fork boundary shape changed')
        r[key]=None if a[key] is None else _blend(a[key],b[key],w)
    for key in ('x','score','guideScore','rawDeviation','stabilizationWeight','gate'):r[key]=a[key]+(b[key]-a[key])*w
    r['value']=r['unconstrainedValue'];r['guideValue']=r['unconstrainedGuideValue']
    return apply_wide_policy(r)
def _events(tree):return list(_entries(tree))
def _route(state,x):
    if state['last'] is None or x>=state['last'][0]['at']+state['last'][0]['handoffWidth']:return state['latest'],[]
    active=[];base=state['rootLeaf']
    for event in _before(state['tree'],x):
        entry,source=event
        if x>=entry['at']+entry['handoffWidth']:
            base=source;break
        active.append(event)
    return base,active
def _evaluate(state,x,h=None):
    base,active=_route(state,x)
    if x<=state['rootLeaf'].domain[1]:
        p=base.curve.sample(x) if h is None else base.curve.at(x,h)
        for entry,source in reversed(active):
            q=source.curve.sample(x) if h is None else source.curve.at(x,h)
            p=_mix_finite(p,q,0.0 if x<=entry['at'] else 1.0 if x>=entry['at']+entry['handoffWidth'] else _extended_gate((x-entry['at'])/entry['handoffWidth']))
        out={**make_scaled_sample(_raw_state(p)),**p}
        for key in _ABS_FIELDS+('floorValue','ceilingValue','boundaryWidth'):
            out['scaled'+key[0].upper()+key[1:]]=None if p[key] is None else Wide.from_number(p[key]).to_json()
        out['logBase']=math.log(p['base'])
        out['signedLogValue']={'sign':0 if p['value']==0 else (1 if p['value']>0 else -1),'logAbs':None if p['value']==0 else math.log(abs(p['value']))}
        return MappingProxyType(out)
    from vixen import smooth5
    p=_wide_leaf(base,x,h)
    for entry,source in reversed(active):p=_mix_wide(p,_wide_leaf(source,x,h),smooth5((x-entry['at'])/entry['handoffWidth']))
    return make_scaled_sample(p)
def _config_json(config):return {k:list(v) if isinstance(v,tuple) else v for k,v in config.items()}
def _lineage(state):return [dict(event[0]) for event in _events(state['tree'])]
def _snapshot_lineage(state):return [{k:e[k] for k in ('at','operation','handoffWidth')} for e in _lineage(state)]
def _available(state,x):
    _coordinate(x,state['domainEnd'])
    if x<(state['checkpoint']['from'] if state['checkpoint'] else 1):raise ValueError('x precedes the available checkpoint')
def _inspect_leaf(source,x):
    if x<=source.config['openingEndpoint']:return []
    if x<=source.domain[1]:return source.curve.inspect(x)
    _,seed32,hash32,_=_legacy_modules();c=source.config;clock=x if c['openingEndpoint']==100 else 1+(x-1)*99/(c['openingEndpoint']-1);u=clock-100;s=seed32(c['seed'])
    from vixen import smooth5
    def noise(p,ch):
        k=math.floor(p);t=p-k;a=2*hash32(s,k,ch)/4294967296-1;b=2*hash32(s,k+1,ch)/4294967296-1
        return a+(b-a)*smooth5(t)
    return sorted(({'index':r['index'],'scale':r['scale'],'coordinate':p,'cell':math.floor(p),'contribution':noise(p,r['channel'])/48,
                   'guideContribution':noise(r['guidePhase']+u*r['guideSlope'],r['channel'])/48}
                  for r in source.curve.ridges() for p in (r['phase']+u*r['slope'],)),key=lambda q:abs(q['contribution']),reverse=True)

class ExtendedCurve:
    __slots__=('_state','_sealed')
    def __init__(self,state):object.__setattr__(self,'_state',state);object.__setattr__(self,'_sealed',True)
    def __setattr__(self,k,v):
        if getattr(self,'_sealed',False):raise AttributeError('Extended timelines are immutable')
        object.__setattr__(self,k,v)
    @property
    def algorithm(self):return EXTENDED_TAIL_ALGORITHM if self._state['checkpoint'] else EXTENDED_ALGORITHM
    @property
    def config(self):return self._state['latest'].config
    @property
    def domain(self):return (self._state['checkpoint']['from'] if self._state['checkpoint'] else 1,self._state['domainEnd'])
    @property
    def normalized_domain(self):return self.domain
    @property
    def available_from(self):return self.domain[0]
    @property
    def lineage(self):return tuple(MappingProxyType(dict(e)) for e in _lineage(self._state))
    def influence(self,target,**options):
        from vixen_extended_operations import with_extended_influence
        return with_extended_influence(self,target,options)
    def impulse(self,options=None,**kwargs):
        from vixen_extended_operations import with_extended_impulse
        return with_extended_impulse(self,{**dict(options or {}),**kwargs})
    def redistribute(self,window,options):
        from vixen_extended_operations import with_extended_redistribution
        return with_extended_redistribution(self,{'from':window[0],'until':window[1],**({'coefficients':options} if isinstance(options,(list,tuple)) else options)})
    def ridges(self):return self._state['latest'].curve.ridges()
    @property
    def dips(self):return self._state['latest'].curve.dips
    def sample_normalized(self,x):
        _available(self._state,x);return _evaluate(self._state,x)
    def at_normalized(self,x,h):
        _available(self._state,x);return _evaluate(self._state,x,_vector(h))
    def sample(self,x):
        p=self.sample_normalized(x)
        for key in _ABS_FIELDS:
            if p[key] is None:raise ValueError(f'Raw {key} is unavailable; use sample_normalized()')
        return p
    def at(self,x,h):
        p=self.at_normalized(x,h)
        for key in _ABS_FIELDS:
            if p[key] is None:raise ValueError(f'Raw {key} is unavailable; use at_normalized()')
        return p
    def location(self,x):
        _available(self._state,x);c=self.config
        if x<=self._state['latest'].domain[1]:return self._state['latest'].curve.location(x)
        clock=x if c['openingEndpoint']==100 else 1+(x-1)*99/(c['openingEndpoint']-1);t=max(0,clock-100)/100
        return tuple(a+t*b for a,b in zip(c['latent'],c['velocity']))
    def inspect(self,x):
        _available(self._state,x);base,active=_route(self._state,x);out=_inspect_leaf(base,x);seed=base.config['seed']
        from vixen import smooth5
        for entry,source in reversed(active):
            w=smooth5((x-entry['at'])/entry['handoffWidth'])
            out=sorted([{**r,'sourceSeed':r.get('sourceSeed',seed),'contribution':r['contribution']*(1-w),'guideContribution':r['guideContribution']*(1-w)} for r in out]+
                       [{**r,'sourceSeed':source.config['seed'],'contribution':r['contribution']*w,'guideContribution':r['guideContribution']*w} for r in _inspect_leaf(source,x)],key=lambda r:abs(r['contribution']),reverse=True)
            seed=source.config['seed']
        return tuple(MappingProxyType(r) for r in out)
    def fork(self,*,at,operation,handoffWidth=None):
        state=self._state;_available(state,at)
        if not isinstance(operation,str) or not operation:raise TypeError('A nonempty operation string is required')
        _,seed32,_,_=_legacy_modules();seed32(operation)
        if state['last'] is not None and at<state['last'][0]['at']:raise ValueError('Nested forks cannot precede their parent fork')
        width=min(16,state['domainEnd']-at) if handoffWidth is None else handoffWidth
        if isinstance(width,bool) or not isinstance(width,(int,float)) or not math.isfinite(width) or width<=0 or at+width<=at or at+width>state['domainEnd']:
            raise ValueError('Handoff must have positive representable width inside the domain')
        seed=_fork_seed(self.config['seed'],operation,at);entry=MappingProxyType({'at':at,'operation':operation,'handoffWidth':width,'seed':seed})
        source=_source({**dict(self.config),'seed':seed});event=(entry,source)
        return ExtendedCurve({**state,'latest':source,'tree':_append(state['tree'],event),'last':event})
    def snapshot(self):
        state=self._state;lineage=_snapshot_lineage(state)
        if state['checkpoint']:return {**dict(state['checkpoint']),'lineage':lineage}
        return {'algorithm':EXTENDED_ALGORITHM,'config':_config_json(state['rootConfig']),'domainEnd':state['domainEnd'],'lineage':lineage}
    def tail(self,from_):
        state=self._state;_available(state,from_);all_events=_events(state['tree']);cut=-1
        for i,(entry,source) in enumerate(all_events):
            if entry['at']+entry['handoffWidth']<=from_:cut=i
        folded=[e[0] for e in all_events[:cut+1]];base=state['rootLeaf'] if cut<0 else all_events[cut][1]
        prior=state['checkpoint']['witness'] if state['checkpoint'] else None
        witness=history_witness(prior,folded) if folded or prior is None else prior
        count=(state['checkpoint']['foldedCount'] if state['checkpoint'] else 0)+len(folded)
        if count>2**53-1:raise ValueError('Folded count exceeds the safe replay representation')
        return {'algorithm':EXTENDED_TAIL_ALGORITHM,'from':from_,'config':_config_json(state['rootConfig']),'domainEnd':state['domainEnd'],
                'seed':base.config['seed'],'leafSeed':_legacy_modules()[1](base.config['seed']),
                'foldedCount':count,'witness':witness,
                'lineage':[{k:e[k] for k in ('at','operation','handoffWidth')} for e,s in all_events[cut+1:]]}

def create_extended(**options):
    options=dict(options);end=options.pop('domainEnd',MAX_DOMAIN_END);source=_source(options)
    if isinstance(end,bool) or not isinstance(end,(int,float)) or not math.isfinite(end) or end<source.config['openingEndpoint'] or end>MAX_DOMAIN_END:
        raise ValueError('domainEnd must include the opening endpoint and be at most 2^40')
    state={'rootLeaf':source,'latest':source,'rootConfig':source.config,'domainEnd':end,'tree':None,'last':None,'checkpoint':None}
    return ExtendedCurve(state)

def _exact_fields(value,keys,label):
    if not isinstance(value,dict) or set(value)!=set(keys):raise ValueError(f'Invalid {label} fields')
def _restore_root(snapshot):
    keys=('algorithm','config','domainEnd','lineage');_exact_fields(snapshot,keys,'extended snapshot')
    if snapshot['algorithm']!=EXTENDED_ALGORITHM or not isinstance(snapshot['lineage'],(list,tuple)):raise ValueError('Invalid extended snapshot')
    api=create_extended(**{**snapshot['config'],'domainEnd':snapshot['domainEnd']})
    for entry in snapshot['lineage']:
        _exact_fields(entry,('at','operation','handoffWidth'),'lineage entry');api=api.fork(**entry)
    return api
def _restore_tail(snapshot):
    keys=('algorithm','from','config','domainEnd','seed','leafSeed','foldedCount','witness','lineage');_exact_fields(snapshot,keys,'extended checkpoint')
    _,seed32,_,_=_legacy_modules();count=snapshot['foldedCount'];leaf_seed=snapshot['leafSeed']
    if snapshot['algorithm']!=EXTENDED_TAIL_ALGORITHM or not isinstance(snapshot['lineage'],(list,tuple)) or not isinstance(snapshot['witness'],str) or re.fullmatch('[0-9a-f]{64}',snapshot['witness']) is None:
        raise ValueError('Invalid extended checkpoint data')
    if isinstance(count,bool) or not isinstance(count,int) or not 0<=count<=2**53-1 or isinstance(leaf_seed,bool) or not isinstance(leaf_seed,int) or not 0<=leaf_seed<=0xffffffff or seed32(snapshot['seed'])!=leaf_seed:
        raise ValueError('Invalid extended checkpoint data')
    root=_source(snapshot['config']);end=snapshot['domainEnd']
    if isinstance(end,bool) or not isinstance(end,(int,float)) or not math.isfinite(end) or end<root.config['openingEndpoint'] or end>MAX_DOMAIN_END or isinstance(snapshot['from'],bool) or not isinstance(snapshot['from'],(int,float)) or not 1<=snapshot['from']<=end:
        raise ValueError('Invalid checkpoint coordinate')
    if count==0 and (isinstance(snapshot['seed'],str)!=isinstance(root.config['seed'],str) or snapshot['seed']!=root.config['seed']):raise ValueError('Unfolded checkpoint must preserve the typed root seed')
    if count>0 and (isinstance(snapshot['seed'],bool) or not isinstance(snapshot['seed'],int)):raise ValueError('Folded checkpoint seed must be uint32')
    api=create_extended(**{**dict(root.config),'seed':snapshot['seed'],'domainEnd':end})
    for entry in snapshot['lineage']:
        _exact_fields(entry,('at','operation','handoffWidth'),'checkpoint lineage entry');api=api.fork(**entry)
    cp=MappingProxyType({k:snapshot[k] for k in keys if k!='lineage'})
    return ExtendedCurve({**api._state,'rootConfig':root.config,'checkpoint':cp})
def restore_extended(snapshot):
    if not isinstance(snapshot,dict):raise TypeError('Snapshot object required')
    if snapshot.get('algorithm')==EXTENDED_ALGORITHM:return _restore_root(snapshot)
    if snapshot.get('algorithm')==EXTENDED_TAIL_ALGORITHM:return _restore_tail(snapshot)
    raise ValueError('restore_extended accepts only vixen-hyperfield-c2/6 and vixen-tail-c2/4')
