"""Analytic composition of sample jets."""
import math
from types import MappingProxyType
from vixen_calculus import Jet,jet_tanh,jet_exp,apply_boundary_jets
_MIX=('x','value','base','score','guideScore','rawDeviation','guideValue','stabilizationWeight','gate','unconstrainedValue','unconstrainedGuideValue','boundaryWidth')
def field(q,key):
    f=q['fields'].get(key)
    return Jet(q['sample'][key],float('nan'),float('nan')) if f is None else Jet(f['value'],f['d1'] if f['d1'] is not None else float('nan'),f['d2'] if f['d2'] is not None else float('nan'))
def finish_jet(sample,fields,reason=None):
    out={}
    for key,value in sample.items():
        if isinstance(value,bool) or not isinstance(value,(int,float)):continue
        q=fields.get(key)
        out[key]=MappingProxyType({'value':value,'d1':q.d1 if q is not None and math.isfinite(q.d1) else None,'d2':q.d2 if q is not None and math.isfinite(q.d2) else None})
    first='value' in out and out['value']['d1'] is not None
    second='value' in out and out['value']['d2'] is not None
    valid=first and second
    return MappingProxyType({'sample':sample,'slope':out['value']['d1'] if first else None,'curvature':out['value']['d2'] if second else None,'fields':MappingProxyType(out),'availability':MappingProxyType({'slope':first,'curvature':second}),'differentiability':MappingProxyType({'value':valid,'sample':all(f['d1'] is not None and f['d2'] is not None for f in out.values()),'rationale':None if valid else reason or 'A contributing value derivative is unavailable.'})})
def derive_adapters(fields):
    r=dict(fields);r['deviation']=(r['value']-r['base'])/r['base'];r['multiplier']=1+r['deviation'];r['activity']=.5+.5*jet_tanh(r['deviation'])
    r['positiveMultiplier']=jet_exp(.55*jet_tanh(r['deviation']));r['positiveValue']=r['base']*r['positiveMultiplier']
    for target,source in [('guideDeviation','guideValue'),('unconstrainedDeviation','unconstrainedValue'),('unconstrainedGuideDeviation','unconstrainedGuideValue')]:r[target]=(r[source]-r['base'])/r['base']
    return r
def blend_jets(a,b,weight,sample,coherent=False):
    r={key:field(a,key)+(field(b,key)-field(a,key))*weight for key in _MIX}
    for key in ('floorValue','ceilingValue'):
        if sample[key] is not None:r[key]=field(a,key)+(field(b,key)-field(a,key))*weight
    if coherent:
        mapped,hard=apply_boundary_jets(r['unconstrainedValue'],r.get('floorValue'),r.get('ceilingValue'),r['boundaryWidth'])
        guide,guide_hard=apply_boundary_jets(r['unconstrainedGuideValue'],r.get('floorValue'),r.get('ceilingValue'),r['boundaryWidth'])
        r['value']=Jet(sample['value'],math.nan,math.nan) if hard else mapped
        r['guideValue']=Jet(sample['guideValue'],math.nan,math.nan) if guide_hard else guide
    else:
        if 'floorValue' in r and r['value'].value<r['floorValue'].value:r['value']=r['floorValue']
        if 'ceilingValue' in r and r['value'].value>r['ceilingValue'].value:r['value']=r['ceilingValue']
    return finish_jet(sample,derive_adapters(r))
