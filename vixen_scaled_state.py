"""Wide-valued constraint policy and immutable scaled sample adapters."""
import math
from collections.abc import Mapping
from types import MappingProxyType

from vixen_wide import Wide

_ZERO = Wide.from_number(0)
_ONE = Wide.from_number(1)
_HALF = Wide.from_ratio(1, 2)
_SIX = Wide.from_number(6)
_NEGATIVE_EIGHT = Wide.from_number(-8)
_THREE = Wide.from_number(3)
_FOUR = Wide.from_number(4)
_LN2 = math.log(2.0)


def _require_wide(value, name):
    if not isinstance(value, Wide):
        raise TypeError(f'{name} must be a Wide')
    return value


def _require_width(value):
    width = _require_wide(value, 'boundaryWidth')
    if width.cmp(_ZERO) < 0:
        raise ValueError('boundaryWidth must be nonnegative')
    return width


def _ramp_near_zero(t):
    t2 = t.mul(t)
    t3 = t2.mul(t)
    return t3.mul(_SIX.add(t.mul(_NEGATIVE_EIGHT.add(_THREE.mul(t)))))


def floor_wide(y, lo, e):
    y = _require_wide(y, 'y')
    lo = _require_wide(lo, 'lo')
    e = _require_width(e)
    if y.cmp(lo) <= 0:
        return lo
    if e.cmp(_ZERO) == 0:
        return y
    edge = lo.add(e)
    if y.cmp(edge) >= 0:
        return y

    t = y.sub(lo).div(e)
    if t.cmp(_HALF) <= 0:
        return lo.add(e.mul(_ramp_near_zero(t)))

    one_minus_t = _ONE.sub(t)
    correction = e.mul(t).mul(one_minus_t.pow(3)).mul(_ONE.add(_THREE.mul(t)))
    return y.sub(correction)


def ceil_wide(y, hi, e):
    y = _require_wide(y, 'y')
    hi = _require_wide(hi, 'hi')
    e = _require_width(e)
    if y.cmp(hi) >= 0:
        return hi
    if e.cmp(_ZERO) == 0:
        return y
    lower_buffer = hi.sub(e)
    if y.cmp(lower_buffer) <= 0:
        return y

    s = hi.sub(y).div(e)
    if s.cmp(_HALF) <= 0:
        return hi.sub(e.mul(_ramp_near_zero(s)))

    one_minus_s = _ONE.sub(s)
    correction = e.mul(one_minus_s).mul(s.pow(3)).mul(_FOUR.sub(_THREE.mul(s)))
    return y.add(correction)


def _apply_wide_value_policy(y, lo, hi, e):
    y = _require_wide(y, 'y')
    if lo is not None:
        lo = _require_wide(lo, 'lo')
    if hi is not None:
        hi = _require_wide(hi, 'hi')
    e = _require_width(e)
    if lo is not None and hi is not None and hi.cmp(lo) <= 0:
        return lo

    value = y if lo is None else floor_wide(y, lo, e)
    if hi is not None:
        value = ceil_wide(value, hi, e)
    return value


def _scalar(state, key):
    value = state[key]
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise TypeError(f'{key} must be a finite number')
    try:
        value = float(value)
    except OverflowError:
        raise ValueError(f'{key} must be a finite number') from None
    if not math.isfinite(value):
        raise ValueError(f'{key} must be a finite number')
    return value


def _raw_number(value):
    try:
        result = value.to_number()
    except (OverflowError, ValueError):
        return None
    if result is None or not math.isfinite(result) or (result == 0 and value.sign != 0):
        return None
    return float(result)


def _log_abs(value):
    if value.sign == 0:
        return None
    try:
        exponent2 = float(value.e + 52)
    except OverflowError:
        return None
    if not math.isfinite(exponent2):
        return None
    result = math.log(abs(value.m) / (2 ** 52)) + exponent2 * _LN2
    return result if math.isfinite(result) else None


def _signed_log(value):
    return MappingProxyType({'sign': value.sign, 'logAbs': _log_abs(value)})


def _tanh_wide(value):
    if value.sign == 0:
        return 0.0
    raw = _raw_number(value)
    if raw is not None:
        return math.tanh(raw)
    return float(value.sign) if value.e + 52 > 1023 else 0.0


def _scaled(value):
    return MappingProxyType(value.to_json())


def _optional_raw(value):
    return None if value is None else _raw_number(value)


def make_scaled_sample(state):
    if not isinstance(state, Mapping):
        raise TypeError('state must be a mapping')

    base = _require_wide(state['base'], 'base')
    if base.cmp(_ZERO) <= 0:
        raise ValueError('base must be positive')
    unconstrained_value = _require_wide(state['unconstrainedValue'], 'unconstrainedValue')
    unconstrained_guide_value = _require_wide(state['unconstrainedGuideValue'], 'unconstrainedGuideValue')
    floor_value = state['floorValue']
    if floor_value is not None:
        floor_value = _require_wide(floor_value, 'floorValue')
    ceiling_value = state['ceilingValue']
    if ceiling_value is not None:
        ceiling_value = _require_wide(ceiling_value, 'ceilingValue')
    boundary_width = _require_width(state['boundaryWidth'])
    score = _scalar(state, 'score')
    guide_score = _scalar(state, 'guideScore')
    gate = _scalar(state, 'gate')
    raw_deviation = _scalar(state, 'rawDeviation')
    stabilization_weight = _scalar(state, 'stabilizationWeight')
    x = _scalar(state, 'x')

    value = apply_wide_policy(unconstrained_value, floor_value, ceiling_value, boundary_width)
    guide_value = apply_wide_policy(unconstrained_guide_value, floor_value, ceiling_value, boundary_width)
    ratio = value.div(base)
    guide_ratio = guide_value.div(base)
    unconstrained_ratio = unconstrained_value.div(base)
    unconstrained_guide_ratio = unconstrained_guide_value.div(base)
    deviation = ratio.sub(_ONE)
    guide_deviation = guide_ratio.sub(_ONE)
    unconstrained_deviation = unconstrained_ratio.sub(_ONE)
    unconstrained_guide_deviation = unconstrained_guide_ratio.sub(_ONE)
    bounded_deviation = _tanh_wide(deviation)
    positive_multiplier = math.exp(0.55 * bounded_deviation)
    positive_value = base.mul(Wide.from_number(positive_multiplier))
    floor_ratio = None if floor_value is None else floor_value.div(base)
    ceiling_ratio = None if ceiling_value is None else ceiling_value.div(base)
    boundary_width_ratio = boundary_width.div(base)

    result = {
        'x': x,
        'logBase': _log_abs(base),
        'score': score,
        'guideScore': guide_score,
        'gate': gate,
        'rawDeviation': raw_deviation,
        'unconstrainedDeviation': _raw_number(unconstrained_deviation),
        'unconstrainedGuideDeviation': _raw_number(unconstrained_guide_deviation),
        'deviation': _raw_number(deviation),
        'guideDeviation': _raw_number(guide_deviation),
        'multiplier': _raw_number(ratio),
        'activity': 0.5 + 0.5 * bounded_deviation,
        'positiveMultiplier': positive_multiplier,
        'signedLogValue': _signed_log(value),
        'value': _raw_number(value),
        'base': _raw_number(base),
        'guideValue': _raw_number(guide_value),
        'unconstrainedValue': _raw_number(unconstrained_value),
        'unconstrainedGuideValue': _raw_number(unconstrained_guide_value),
        'positiveValue': _raw_number(positive_value),
        'stabilizationWeight': stabilization_weight,
        'floorRatio': _optional_raw(floor_ratio),
        'ceilingRatio': _optional_raw(ceiling_ratio),
        'floorValue': _optional_raw(floor_value),
        'ceilingValue': _optional_raw(ceiling_value),
        'boundaryWidthRatio': _raw_number(boundary_width_ratio),
        'boundaryWidth': _raw_number(boundary_width),
        'constraintApplied': value.cmp(unconstrained_value) != 0,
        'scaledValue': _scaled(value),
        'scaledBase': _scaled(base),
        'scaledGuideValue': _scaled(guide_value),
        'scaledUnconstrainedValue': _scaled(unconstrained_value),
        'scaledUnconstrainedGuideValue': _scaled(unconstrained_guide_value),
        'scaledPositiveValue': _scaled(positive_value),
        'scaledFloorValue': None if floor_value is None else _scaled(floor_value),
        'scaledCeilingValue': None if ceiling_value is None else _scaled(ceiling_value),
        'scaledBoundaryWidth': _scaled(boundary_width),
        'scaledDeviation': _scaled(deviation),
        'scaledGuideDeviation': _scaled(guide_deviation),
        'scaledUnconstrainedDeviation': _scaled(unconstrained_deviation),
        'scaledUnconstrainedGuideDeviation': _scaled(unconstrained_guide_deviation),
    }
    return MappingProxyType(result)

def apply_wide_policy(value,lo=None,hi=None,width=None):
    if isinstance(value,Wide):return _apply_wide_value_policy(value,lo,hi,width)
    record=make_scaled_sample(value)
    state={**value,**record}
    for key in ('base','value','guideValue','positiveValue','unconstrainedValue','unconstrainedGuideValue','floorValue','ceilingValue','boundaryWidth'):
        encoded=record['scaled'+key[0].upper()+key[1:]]
        state[key]=None if encoded is None else Wide.decode(encoded)
    return MappingProxyType(state)
