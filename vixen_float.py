"""Correctly rounded finite-operand binary64 means and product sums."""
import math
from fractions import Fraction


def _ratio(value):
    if isinstance(value,bool) or not isinstance(value,(int,float)) or not math.isfinite(value):
        raise ValueError('Finite binary64 operand required')
    return Fraction.from_float(float(value))


def exact_mean(values):
    if not values:
        raise ValueError('A nonempty numeric sequence is required')
    terms=tuple(_ratio(v) for v in values)
    if all(v==values[0] and math.copysign(1,v)==math.copysign(1,values[0]) for v in values):
        return float(values[0])
    return float(sum(terms,Fraction())/len(terms))


def sum_products(terms,divisors=()):
    total=Fraction()
    for factors in terms:
        product=Fraction(1)
        for factor in factors:product*=_ratio(factor)
        total+=product
    for divisor in divisors:total/=_ratio(divisor)
    try:return float(total)
    except OverflowError:return math.inf if total>0 else -math.inf
