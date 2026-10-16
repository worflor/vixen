import math
from vixen_wide import Wide
LN2=22400441642467859577488739781209226459403626065886660720528601240145527492420725336291667021425889702299778972254785424771724351736870381509034525077594234322856729913233103191693932917751924172625994619620400660039442608528415575721354420972082577163443760602448842212312094033841292568983687855542170368247819631661533385799671122421173221385317065110553753226746392818491381768398893626906223910200995167915538026273569853823698607422800781837962733502154433664101279894325772997090164915246519231150831430415653842396495805010817059154674077638814850832366084056018734798876353421653877927763165084799176609677707
BITS=2048

def _nearest(n,d):
    q,r=divmod(n,d)
    return q+int(2*r>d or (2*r==d and q&1))
def wide_exp(value):
    if isinstance(value,bool) or not isinstance(value,(int,float)):raise TypeError('A finite binary64 exponential argument is required')
    try:value=float(value)
    except OverflowError:raise ValueError('A finite binary64 exponential argument is required') from None
    if not math.isfinite(value):raise ValueError('A finite binary64 exponential argument is required')
    x=Wide.from_number(value);numerator=x.m<<x.e if x.e>=0 else x.m;denominator=1 if x.e>=0 else 1<<-x.e
    k=_nearest(abs(numerator)<<BITS,denominator*LN2)
    if numerator<0:k=-k
    residual=Wide.from_ratio((numerator<<BITS)-k*LN2*denominator,denominator<<BITS);r=residual.to_number()
    if r is None:
        if residual.e+52>-1075:raise ValueError('Unrepresentable exponential residual')
        r=0.0
    if abs(r)>.36:raise ValueError('Exponential range reduction failed')
    return Wide.from_number(math.exp(r)).scale(k)
