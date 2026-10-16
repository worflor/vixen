import math
from vixen_calculus import Jet,jet_smooth5,apply_boundary_jets
from vixen_guide import guide_jet
from vixen_jet_composition import finish_jet,derive_adapters,field


def _weight_jet(r,x):
    if x<=r['at'] or r['windowed'] and x>=r['releaseEnd']:return Jet(0)
    if x<r['at']+r['handoffWidth']:
        return r['amount']*jet_smooth5(Jet((x-r['at'])/r['handoffWidth'],1/r['handoffWidth']))
    if not r['windowed'] or x<=r['until']:return Jet(r['amount'])
    return r['amount']*(1-jet_smooth5(Jet((x-r['until'])/r['releaseWidth'],1/r['releaseWidth'])))


def influence_jet(parent,compiled,r,config,sample,x,policy):
    p=parent['sample'];f={k:field(parent,k) for k in p if isinstance(p[k],(int,float)) and not isinstance(p[k],bool)}
    w=_weight_jet(r,x);g=guide_jet(compiled,x);b=f['base']
    increment=w*g if r['reference']=='value' else b*(w*g)
    if r['mode']=='add':u=f['unconstrainedValue']+increment
    else:
        target=g if r['reference']=='value' else b*(1+g) if r['reference']=='baseline' else f['unconstrainedGuideValue']+b*g
        u=target if w.value==1 and w.d1==0 and w.d2==0 else (1-w)*f['unconstrainedValue']+w*target
    e=f['boundaryWidth'];lo=f.get('floorValue');hi=f.get('ceilingValue')
    if lo is not None or hi is not None:
        extra=b*w*config['boundarySoftness']
        if lo is not None and hi is not None:
            room=(hi-lo)/2-e
            if room.value>0 and extra.value>0:
                extra=room/(1+room/extra) if extra.value>room.value else extra/(1+extra/room)
            else:extra=Jet(0,math.nan if room.value==0 and room.d1!=0 else 0,math.nan if room.value==0 and room.d2!=0 else 0)
        e=e+extra
    v,hard=apply_boundary_jets(u,lo,hi,e);gv,ghard=apply_boundary_jets(f['unconstrainedGuideValue'],lo,hi,e)
    edge=x==r['at'] or r['windowed'] and x==r['releaseEnd']
    if hard or edge and policy(p['unconstrainedValue'],p)!=p['value']:v=Jet(v.value,math.nan,math.nan)
    if ghard or edge and policy(p['unconstrainedGuideValue'],p)!=p['guideValue']:gv=Jet(gv.value,math.nan,math.nan)
    f.update(value=v,unconstrainedValue=u,guideValue=gv,boundaryWidth=e)
    return finish_jet(sample,derive_adapters(f),'A contributing derivative is unavailable or a legacy constrained-parent influence seam is discontinuous.')
