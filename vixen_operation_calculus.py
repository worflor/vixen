import math
from vixen_calculus import Jet,jet_smooth5,apply_boundary_jets
from vixen_float import exact_mean
from vixen_jet_composition import finish_jet,derive_adapters,field


def operator_jet(parent,delta,sample):
    p=parent['sample'];f={k:field(parent,k) for k in p if isinstance(p[k],(int,float)) and not isinstance(p[k],bool)}
    u=f['base']*(1+f['unconstrainedDeviation']+Jet(*delta))
    lo=f.get('floorValue');hi=f.get('ceilingValue')
    v,hard=apply_boundary_jets(u,lo,hi,f['boundaryWidth'])
    projected,_=apply_boundary_jets(Jet(p['unconstrainedValue']),None if lo is None else Jet(lo.value),None if hi is None else Jet(hi.value),Jet(p['boundaryWidth']))
    if hard or delta[0]==0 and projected.value!=p['value']:v=Jet(v.value,math.nan,math.nan)
    f.update(value=v,unconstrainedValue=u)
    return finish_jet(sample,derive_adapters(f),'A contributing derivative is unavailable or a legacy constrained-parent operator seam is discontinuous.')


def coupling_weight_jet(start,end,strength,x):
    if x<=start or x>=end or strength==0:return Jet(0)
    mid=start+(end-start)/2
    z=Jet((x-start)/(mid-start),1/(mid-start)) if x<=mid else Jet((end-x)/(end-mid),-1/(end-mid))
    return strength*jet_smooth5(z)


def coupled_jets(parents,w,samples):
    if w.value==0 and w.d1==0 and w.d2==0:return tuple(parents)
    ds=[field(p,'deviation') for p in parents]
    def mean(name):
        values=[getattr(q,name) for q in ds]
        return exact_mean(values) if all(math.isfinite(v) for v in values) else math.nan
    m=Jet(mean('value'),mean('d1'),mean('d2'))
    def output(p,i):
        f={k:field(p,k) for k in p['sample'] if isinstance(p['sample'][k],(int,float)) and not isinstance(p['sample'][k],bool)}
        d=(1-w)*ds[i]+w*m
        f['value']=f['unconstrainedValue']=f['base']*(1+d)
        return finish_jet(samples[i],derive_adapters(f))
    return tuple(output(p,i) for i,p in enumerate(parents))
