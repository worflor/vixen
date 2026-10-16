"""Dependency-free C2 output operations for Vixen curves."""
import math
from vixen_operation_calculus import operator_jet,coupled_jets,coupling_weight_jet
from vixen_api import (CurveCapabilities, evaluate_curve, evaluate_curve_jet, register_unary_curve, register_multi_curve,
    latest_write_time, curve_origin, curve_lineage, curve_config, curve_domain,
    curve_influence_head, materialize_influences, snapshot_curve, fork_curve)
from types import MappingProxyType
from vixen_influence import with_influence
from vixen_float import exact_mean,sum_products

OPERATION_ALGORITHM = 'vixen-operation-c2/1'
_MAX_COEFFICIENTS = 64
_MAX_CURVES = 32
_GROUPS = set()
_CERTIFICATE = MappingProxyType({'kind': 'exact-real-endpoint-integral', 'potential': 't^4*(1-t)^4*P(t)', 'perturbation': 'd/dt potential', 'integral': 'Q(1)-Q(0)=0'})
_FACTOR = (0, 0, 0, 0, 1, -4, 6, -4, 1)


def _finite(value, name):
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise TypeError(name + ' must be a finite number')
    try:
        value = float(value)
    except (OverflowError, ValueError):
        raise ValueError(name + ' must be finite') from None
    if not math.isfinite(value):
        raise ValueError(name + ' must be finite')
    return value


def _fields(value, allowed, required, name):
    if not isinstance(value, dict) or set(value) - set(allowed) or set(required) - set(value):
        raise TypeError('Invalid ' + name + ' fields')


def _domain(curve):
    if not all(hasattr(curve, name) for name in ('sample', 'at', 'snapshot', 'tail', 'fork', 'domain')):
        raise TypeError('A Vixen curve is required')
    if len(curve.domain) != 2:
        raise ValueError('Curve domain must contain two endpoints')
    lo, hi = map(lambda v: _finite(v, 'domain endpoint'), curve.domain)
    if hi <= lo:
        raise ValueError('Curve domain must have positive width')
    return lo, hi


def _support(at, attack, relaxation):
    rise = at + attack
    end = rise + relaxation
    if not math.isfinite(rise) or not math.isfinite(end) or rise <= at or end <= rise:
        raise ValueError('Operation support must have positive representable widths')
    return rise, end


def _smooth5(t):
    if t <= 0:
        return 0.0
    if t >= 1:
        return 1.0
    return t * t * t * (10 + t * (-15 + 6 * t))


def _smooth5_jet(t):
    if t <= 0 or t >= 1:
        return _smooth5(t), 0.0, 0.0
    return _smooth5(t), 30 * t * t * (1 - t) ** 2, 60 * t * (1 - t) * (1 - 2 * t)


def _derivative(coefficients, order):
    out = list(coefficients)
    for _ in range(order):
        out = [v * (i + 1) for i, v in enumerate(out[1:])]
    return out or [0.0]


def _horner(coefficients, t):
    value = 0.0
    for coefficient in reversed(coefficients):
        value = value * t + coefficient
    return value


def _poly_jet(coefficients, t):
    return tuple(_horner(_derivative(coefficients, order), t) for order in range(4))


def _product(a, b):
    out = [0.0] * (len(a) + len(b) - 1)
    for i, av in enumerate(a):
        for j, bv in enumerate(b):
            out[i + j] += av * bv
    if any(not math.isfinite(v) for v in out):
        raise ValueError('Polynomial coefficients exceed the finite numeric range')
    return out


def _recipe_redistribution(value):
    _fields(value, ('type', 'from', 'until', 'coefficients', 'magnitude', 'measure', 'certificate'), ('type', 'from', 'until', 'coefficients', 'magnitude', 'measure'), 'redistribution recipe')
    if value['type'] != 'redistribution':
        raise TypeError('Invalid redistribution recipe')
    start = _finite(value['from'], 'window start')
    end = _finite(value['until'], 'window end')
    magnitude = _finite(value['magnitude'], 'magnitude')
    if end <= start or start + (end - start) <= start:
        raise ValueError('Redistribution window must have positive representable width')
    coefficients = value['coefficients']
    if not isinstance(coefficients, (list, tuple)) or not 1 <= len(coefficients) <= _MAX_COEFFICIENTS:
        raise ValueError('Shape must contain 1 to 64 coefficients')
    coefficients = tuple(_finite(v, 'shape coefficient') for v in coefficients)
    measure = value['measure']
    if measure not in ('actualDeviation', 'unconstrainedDeviation'):
        raise TypeError('Invalid redistribution measure')
    certificate = value.get('certificate')
    if certificate is not None and dict(certificate) != dict(_CERTIFICATE):
        raise TypeError('Invalid algebra certificate descriptor')
    q = _product(_FACTOR, coefficients)
    radius = abs(magnitude) * sum(abs(v) for v in _derivative(q, 1))
    if not math.isfinite(radius):
        raise ValueError('Redistribution bound exceeds the finite numeric range')
    return MappingProxyType({'type': 'redistribution', 'from': start, 'until': end, 'coefficients': coefficients, 'magnitude': magnitude, 'measure': measure, 'certificate': _CERTIFICATE})


def _jet(recipe, x):
    if recipe['magnitude']==0:return 0,0,0
    x = _finite(x, 'jet coordinate')
    if recipe['type'] == 'impulse':
        at, magnitude = recipe['at'], recipe['magnitude']
        attack, relaxation = recipe['attack'], recipe['relaxation']
        rise, end = _support(at, attack, relaxation)
        if x <= at or x >= end:
            return 0.0, 0.0, 0.0
        if x <= rise:
            t = (x - at) / attack
            v, d, dd = _smooth5_jet(t)
            return magnitude * v, magnitude * d / attack, magnitude * dd / attack / attack
        t = (x - rise) / relaxation
        v, d, dd = _smooth5_jet(t)
        return magnitude * (1 - v), -magnitude * d / relaxation, -magnitude * dd / relaxation / relaxation
    if recipe['type'] == 'redistribution':
        start, end = recipe['from'], recipe['until']
        if x <= start or x >= end:
            return 0.0, 0.0, 0.0
        width = end - start
        t = (x - start) / width
        f = _poly_jet(_FACTOR, t)
        p = _poly_jet(recipe['coefficients'], t)
        magnitude = recipe['magnitude']
        value = magnitude * (f[1] * p[0] + f[0] * p[1])
        first = magnitude * (f[2] * p[0] + 2 * f[1] * p[1] + f[0] * p[2]) / width
        second = magnitude * (f[3] * p[0] + 3 * f[2] * p[1] + 3 * f[1] * p[2] + f[0] * p[3]) / (width * width)
        if not all(math.isfinite(v) for v in (value, first, second)):
            raise ValueError('Perturbation jet exceeds the finite numeric range')
        return value, first, second
    raise TypeError('Unknown perturbation recipe')


def perturbation_jet(options, x):
    x=_finite(x,'sample coordinate')
    if not isinstance(options, dict):
        raise TypeError('Perturbation options are required')
    if options.get('type') == 'impulse':
        _fields(options, ('type', 'at', 'magnitude', 'relaxation', 'attack'), ('type', 'at', 'magnitude', 'relaxation'), 'impulse recipe')
        at = _finite(options['at'], 'at')
        magnitude = _finite(options['magnitude'], 'magnitude')
        relaxation = _finite(options['relaxation'], 'relaxation')
        attack = _finite(options['attack'], 'attack') if 'attack' in options else relaxation / 8
        if attack <= 0 or relaxation <= 0:
            raise ValueError('Attack and relaxation must be positive')
        _support(at,attack,relaxation)
        recipe = {'type': 'impulse', 'at': at, 'magnitude': magnitude, 'attack': attack, 'relaxation': relaxation}
    elif options.get('type') == 'redistribution':
        recipe = _recipe_redistribution(options)
    else:
        raise TypeError('Perturbation type must be impulse or redistribution')
    value, first, second = _jet(recipe, x)
    if not all(math.isfinite(v) for v in (value,first,second)):raise ValueError('Perturbation derivatives exceed the finite numeric range')
    return {'value': value, 'first': first, 'second': second}


def _normalize_impulse(options, domain, carried=False):
    _fields(options, ('at', 'magnitude', 'relaxation', 'attack'), ('at', 'magnitude', 'relaxation'), 'impulse options')
    at = _finite(options['at'], 'at')
    magnitude = _finite(options['magnitude'], 'magnitude')
    relaxation = _finite(options['relaxation'], 'relaxation')
    attack = _finite(options['attack'], 'attack') if 'attack' in options else relaxation / 8
    if attack <= 0 or relaxation <= 0:
        raise ValueError('Attack and relaxation must be positive')
    rise, end = _support(at, attack, relaxation)
    lo, hi = domain
    if (at < (1 if carried else lo)) or rise > hi or end > hi or end <= lo:
        raise ValueError('Impulse support must lie inside the available domain')
    return MappingProxyType({'type': 'impulse', 'at': at, 'magnitude': magnitude, 'relaxation': relaxation, 'attack': attack})


def _normalize_redistribution(curve, window, shape, carried=False):
    lo, hi = _domain(curve)
    if not isinstance(window, (list, tuple)) or len(window) != 2:
        raise TypeError('window must be [from, until]')
    start, end = (_finite(v, 'window endpoint') for v in window)
    if end <= start or start + (end - start) <= start:
        raise ValueError('Redistribution window must have positive representable width')
    if (start < (1 if carried else lo)) or end > hi or (carried and end <= lo):
        raise ValueError('Redistribution window must lie inside the available domain')
    magnitude, measure = 1.0, 'actualDeviation'
    if isinstance(shape, (list, tuple)):
        coefficients = shape
    else:
        _fields(shape, ('coefficients', 'magnitude', 'measure'), ('coefficients',), 'shape options')
        coefficients = shape['coefficients']
        if 'magnitude' in shape:
            magnitude = _finite(shape['magnitude'], 'magnitude')
        if 'measure' in shape:
            measure = shape['measure']
    return _recipe_redistribution({'type': 'redistribution', 'from': start, 'until': end, 'coefficients': coefficients, 'magnitude': magnitude, 'measure': measure})


def _policy(value, sample):
    lo, hi, e = sample['floorValue'], sample['ceilingValue'], sample['boundaryWidth']
    if lo is not None and hi is not None and hi <= lo:
        return lo
    if lo is not None:
        if value <= lo:
            value = lo
        elif e > 0 and value < lo + e:
            t = (value - lo) / e
            value = lo + e * (0 if t <= 0 else 1 if t >= 1 else t ** 3 * (6 + t * (-8 + 3 * t)))
    if hi is not None:
        if value >= hi:
            value = hi
        elif e > 0 and value > hi - e:
            t = (hi - value) / e
            value = hi - e * (0 if t <= 0 else 1 if t >= 1 else t ** 3 * (6 + t * (-8 + 3 * t)))
    return value


def _finite_sample(sample, deviation):
    base = _finite(sample['base'], 'sample base')
    if base <= 0:
        raise ValueError('Sample base must be positive')
    unconstrained = base + base * deviation
    if not math.isfinite(unconstrained):
        raise ValueError('Transformed value exceeds the finite numeric range')
    constrained = sample['floorValue'] is not None or sample['ceilingValue'] is not None
    value = _policy(unconstrained, sample) if constrained else unconstrained
    actual = (value - base) / base if constrained else deviation
    positive = math.exp(.55 * math.tanh(actual))
    if not all(math.isfinite(v) for v in (value, actual, positive)):
        raise ValueError('Transformed sample exceeds the finite numeric range')
    return {**sample, 'value': value, 'unconstrainedValue': unconstrained, 'unconstrainedDeviation': deviation, 'deviation': actual, 'multiplier': 1 + actual, 'activity': .5 + .5 * math.tanh(actual), 'positiveMultiplier': positive, 'positiveValue': base * positive, 'constraintApplied': value != unconstrained}


def _plain(value):
    if hasattr(value, 'items'):
        return {k: _plain(v) for k, v in value.items()}
    if isinstance(value, (tuple, list)):
        return [_plain(v) for v in value]
    return value


def _bounds(curve, radius):
    bounds = getattr(curve, 'bounds', None)
    if not bounds or 'deviation' not in bounds:
        return bounds
    lo, hi = bounds['deviation']
    deviation = (lo - radius, hi + radius)
    return MappingProxyType({**bounds, 'deviation': deviation, 'multiplier': (1 + deviation[0], 1 + deviation[1])})


def _range(curve, start, end, count, sampler):
    lo, hi = _domain(curve)
    start, end = _finite(start, 'range start'), _finite(end, 'range end')
    if isinstance(count, bool) or not isinstance(count, int) or not 2 <= count <= 100001 or start < lo or end > hi or end < start:
        raise ValueError('Invalid sample range')
    return tuple(sampler(start + (end - start) * i / (count - 1)) for i in range(count))


def _normalized_sample(p):
    out=dict(p);v=p['value'];b=p['base']
    out.update(logBase=math.log(b),signedLogValue={'sign':0 if v==0 else (-1 if v<0 else 1),'logAbs':None if v==0 else math.log(abs(v))},floorRatio=None if p['floorValue'] is None else p['floorValue']/b,ceilingRatio=None if p['ceilingValue'] is None else p['ceilingValue']/b,boundaryWidthRatio=p['boundaryWidth']/b)
    return out


class _OperationCurve(CurveCapabilities):
    __slots__ = ('_curve', '_recipe', '_restore', '_bounds', '_sealed', '_evaluation_state')

    def __setattr__(self, name, value):
        if getattr(self, '_sealed', False):
            raise AttributeError('Operation curves are immutable')
        object.__setattr__(self, name, value)

    def __init__(self, curve, recipe, restore=None):
        self._curve, self._recipe, self._restore = curve, recipe, restore
        if recipe['type'] == 'impulse':
            radius = abs(recipe['magnitude'])
        else:
            q = _product(_FACTOR, recipe['coefficients'])
            radius = abs(recipe['magnitude']) * sum(abs(v) for v in _derivative(q, 1))
        self._bounds = _bounds(curve, radius)
        start = recipe.get('at', recipe.get('from'))
        end = start + recipe['attack'] + recipe['relaxation'] if recipe['type'] == 'impulse' else recipe['until']
        register_unary_curve(self, curve, domain=curve_domain(curve),latest=max(latest_write_time(curve),start),
            snapshot_frame=lambda p:dict(algorithm=OPERATION_ALGORITHM,operation=recipe['type'],curve=p[0],recipe=_plain(recipe)),
            rebuild=lambda p:_OperationCurve(p[0],recipe,restore),
            interval=(start,end), always_identity=recipe['magnitude']==0,
            identity_at=lambda x:x<=start or x>=end,
            discard_on_tail=lambda x:x>=end,
            transform=lambda parent,x,h,explicit:self._apply(parent,x),
            jet_transform=lambda parent,x,h,explicit:operator_jet(parent,_jet(recipe,x),self._apply(parent['sample'],x)))
        self._sealed = True

    @property
    def algorithm(self): return OPERATION_ALGORITHM
    @property
    def config(self): return curve_config(self)
    @property
    def domain(self): return curve_domain(self)
    @property
    def bounds(self): return self._bounds
    @property
    def lineage(self): return curve_lineage(self)
    @property
    def influences(self): return materialize_influences(curve_influence_head(self))
    @property
    def dips(self): return curve_origin(self).dips

    def _apply(self, sample, x):
        delta = _jet(self._recipe, x)[0]
        if delta == 0:
            return sample
        current = _finite(sample['unconstrainedDeviation'], 'unconstrainedDeviation')
        deviation = current + delta
        if not math.isfinite(deviation):
            raise ValueError('Transformed deviation exceeds the finite numeric range')
        return sample if deviation == current else _finite_sample(sample, deviation)

    def sample(self, x): return evaluate_curve(self,x)
    def at(self, x, h): return evaluate_curve(self,x,h)
    def location(self, x): return curve_origin(self).location(x)
    def inspect(self, x): return curve_origin(self).inspect(x)
    def ridges(self): return curve_origin(self).ridges()
    def sample_range(self, start, end, count=129): return _range(self, start, end, count, self.sample)
    def snapshot(self): return snapshot_curve(self)
    def tail(self, from_): return snapshot_curve(self,from_)
    def fork(self, *, at, operation, handoffWidth=None):
        return fork_curve(self,dict(at=at,operation=operation,handoffWidth=handoffWidth))
    def redistribute(self, window, shape): return with_redistribution(self, window, shape, self._restore)
    def influence(self, target, **options): return with_influence(self, target, **options)
    slope = CurveCapabilities.slope
    curvature = CurveCapabilities.curvature
    jet = CurveCapabilities.jet
    derivative = CurveCapabilities.slope
    second_derivative = CurveCapabilities.curvature
    @property
    def normalized_domain(self):return self.domain
    def sample_normalized(self,x):return _normalized_sample(self.sample(x))
    def at_normalized(self,x,h):return _normalized_sample(self.at(x,h))
    normalized=sample_normalized
    normalised=sample_normalized


def with_impulse(curve, options, restore=None):
    return _OperationCurve(curve, _normalize_impulse(options, _domain(curve)), restore)


def with_redistribution(curve, window, shape, restore=None):
    recipe = _normalize_redistribution(curve, window, shape)
    if recipe['measure'] == 'actualDeviation' and recipe['magnitude'] != 0 and (curve.config['floor'] != 'none' or curve.config['ceilingMargin'] is not None):
        raise ValueError('Actual-deviation redistribution requires an unconstrained source; choose measure: unconstrainedDeviation explicitly')
    return _OperationCurve(curve, recipe, restore)


def _coupling_domain(curves, strength, window, carried=False):
    if not isinstance(curves, (list, tuple)) or not 2 <= len(curves) <= _MAX_CURVES:
        raise ValueError('Coupling requires 2 to 32 curves')
    domains = [_domain(curve) for curve in curves]
    lo, hi = domains[0]
    if any(domain != (lo, hi) for domain in domains):
        raise ValueError('Coupled curves must have identical finite domains')
    strength = _finite(strength, 'strength')
    if not 0 <= strength <= 1:
        raise ValueError('strength must lie in [0,1]')
    if not isinstance(window, (list, tuple)) or len(window) != 2:
        raise TypeError('window must be [from, until]')
    start, end = (_finite(v, 'window endpoint') for v in window)
    if end <= start or start + (end - start) <= start or start < (1 if carried else lo) or end > hi:
        raise ValueError('Coupling window must have positive width inside the available domain')
    if strength != 0 and any(c.config['floor'] != 'none' or c.config['ceilingMargin'] is not None for c in curves):
        raise ValueError('Nontrivial coupling requires sources without nonlinear floor or ceiling policies')
    return lo, hi, start, end, strength


def _gate(x, start, end):
    if x <= start or x >= end:
        return 0.0
    middle = start + (end - start) / 2
    return _smooth5((x - start) / (middle - start)) if x <= middle else _smooth5((end - x) / (end - middle))


def _derive_unconstrained(sample, deviation):
    base = _finite(sample['base'], 'sample base')
    value = base + base * deviation
    if base <= 0 or not math.isfinite(value):
        raise ValueError('Coupled value exceeds the finite numeric range')
    positive = math.exp(.55 * math.tanh(deviation))
    return {**sample, 'value': value, 'unconstrainedValue': value, 'unconstrainedDeviation': deviation, 'deviation': deviation, 'multiplier': 1 + deviation, 'activity': .5 + .5 * math.tanh(deviation), 'positiveMultiplier': positive, 'positiveValue': base * positive, 'constraintApplied': False}


class _CouplingGroup:
    __slots__ = ('_curves', 'strength', 'window', 'domain', 'curves', '_sealed', '_evaluation_state')

    def __setattr__(self, name, value):
        if getattr(self, '_sealed', False):
            raise AttributeError('Coupling groups are immutable')
        object.__setattr__(self, name, value)

    def __init__(self, curves, strength, window, carried=False):
        lo, hi, start, end, strength = _coupling_domain(curves, strength, window, carried)
        self._curves = tuple(curves)
        self.strength = strength
        self.window = (start, end)
        self.domain = (lo, hi)
        register_multi_curve(self,curves,domain=self.domain,
            snapshot_frame=lambda p:dict(algorithm=OPERATION_ALGORITHM,operation='coupling',curves=list(p),strength=strength,window=list(self.window)),
            compute=lambda parents,x,h,explicit:self._compute(parents,x),
            jet_compute=lambda parents,x,h,explicit:coupled_jets(parents,coupling_weight_jet(start,end,strength,x),self._compute(tuple(p['sample'] for p in parents),x)))
        self.curves = tuple(_CouplingView(self, i) for i in range(len(curves)))
        _GROUPS.add(id(self))
        self._sealed = True

    @property
    def algorithm(self): return OPERATION_ALGORITHM

    def _compute(self, parents, x):
        x = _finite(x, 'sample coordinate')
        if not self.domain[0] <= x <= self.domain[1]:
            raise ValueError('x outside coupled domain')
        amount = self.strength * _gate(x, *self.window)
        if amount == 0:
            return parents
        deviations = tuple(_finite(p['deviation'], 'deviation') for p in parents)
        mean = exact_mean(deviations)
        def output(p,d):
            if d==mean:return p
            mixed=(1-amount)*d+amount*mean
            if not math.isfinite(mixed):mixed=sum_products([[1-amount,d],[amount,mean]])
            return _derive_unconstrained(p,mixed)
        return tuple(output(p,d) for p,d in zip(parents,deviations))

    def sample(self, x): return evaluate_curve(self,x)
    def at(self, x, h): return evaluate_curve(self,x,h)
    def jet(self,x,h=None):return evaluate_curve_jet(self,x) if h is None else evaluate_curve_jet(self,x,h)
    def sample_range(self, start, end, count=129): return _range(self, start, end, count, self.sample)
    def snapshot(self): return snapshot_curve(self)
    def tail(self, from_): return snapshot_curve(self,from_)
    def fork(self, **kwargs): raise ValueError('Forking a coupling group has ambiguous branch semantics')


class _CouplingView(CurveCapabilities):
    __slots__ = ('_group', '_index', '_bounds', '_evaluation_state')

    def __init__(self, group, index):
        object.__setattr__(self, '_group', group)
        object.__setattr__(self, '_index', index)
        source=group._curves[index]
        bounds=getattr(source,'bounds',None)
        ranges=[getattr(c,'bounds',None) for c in group._curves]
        if bounds is not None and all(b is not None and 'deviation' in b for b in ranges):
            lo=min(b['deviation'][0] for b in ranges)
            hi=max(b['deviation'][1] for b in ranges)
            bounds=MappingProxyType({**bounds,'deviation':(lo,hi),'multiplier':(1+lo,1+hi)})
        object.__setattr__(self,'_bounds',bounds)
        register_unary_curve(self,group,domain=group.domain,
            latest=latest_write_time(source),origin=curve_origin(source),
            influence_head=curve_influence_head(source),
            snapshot_frame=lambda p:dict(algorithm=OPERATION_ALGORITHM,operation='couplingView',group=p[0],index=index),
            tail_source=lambda x:source if group.strength==0 or x>=group.window[1] else None,
            transform=lambda samples,x,h,explicit:samples[index],
            jet_transform=lambda samples,x,h,explicit:samples[index])

    def __setattr__(self, name, value): raise AttributeError('Coupling projections are immutable')
    @property
    def algorithm(self): return OPERATION_ALGORITHM
    @property
    def config(self): return curve_config(self)
    @property
    def domain(self): return self._group.domain
    @property
    def bounds(self): return self._bounds
    @property
    def lineage(self): return curve_lineage(self)
    @property
    def influences(self): return materialize_influences(curve_influence_head(self))
    @property
    def dips(self): return curve_origin(self).dips
    def sample(self, x): return evaluate_curve(self,x)
    def at(self, x, h): return evaluate_curve(self,x,h)
    def location(self, x): return curve_origin(self).location(x)
    def inspect(self, x): return curve_origin(self).inspect(x)
    def ridges(self): return curve_origin(self).ridges()
    def sample_range(self, start, end, count=129): return _range(self, start, end, count, self.sample)
    def snapshot(self): return snapshot_curve(self)
    def tail(self, from_): return snapshot_curve(self,from_)
    def fork(self, **kwargs): raise ValueError('Forking a coupled projection has ambiguous branch semantics')
    def redistribute(self, window, shape): return with_redistribution(self, window, shape)
    def influence(self, target, **options): return with_influence(self, target, **options)
    slope = CurveCapabilities.slope
    curvature = CurveCapabilities.curvature
    jet = CurveCapabilities.jet
    derivative = CurveCapabilities.slope
    second_derivative = CurveCapabilities.curvature
    @property
    def normalized_domain(self):return self.domain
    def sample_normalized(self,x):return _normalized_sample(self.sample(x))
    def at_normalized(self,x,h):return _normalized_sample(self.at(x,h))
    normalized=sample_normalized
    normalised=sample_normalized


def couple(curves, strength, window):
    return _CouplingGroup(curves, strength, window)


def _validate_snapshot_tree(root):
    active = set()
    stack = [(root, False, 0)]
    nodes = 0
    while stack:
        value, leave, depth = stack.pop()
        if leave:
            active.remove(id(value))
            continue
        if value is None or isinstance(value, (str, bool)):
            continue
        if isinstance(value, (int, float)):
            if isinstance(value, bool) or not math.isfinite(value):
                raise ValueError('Snapshot contains nonfinite numeric data')
            continue
        if not isinstance(value, (dict, list, tuple, MappingProxyType)):
            raise TypeError('Snapshot contains unsupported or excessive data')
        nodes += 1
        if nodes > 100000 or id(value) in active:
            raise ValueError('Snapshot cycles or excessive data are not allowed')
        active.add(id(value))
        stack.append((value, True, depth))
        children = list(value.values()) if isinstance(value, (dict, MappingProxyType)) else value
        stack.extend((child, False, depth + 1) for child in reversed(children))


def restore_operation(snapshot, restore):
    _validate_snapshot_tree(snapshot)
    if not callable(restore) or not isinstance(snapshot, dict) or snapshot.get('algorithm') != OPERATION_ALGORITHM:
        raise TypeError('Invalid operation snapshot or root restore callback')
    operation = snapshot.get('operation')
    if operation in ('impulse', 'redistribution'):
        _fields(snapshot, ('algorithm', 'operation', 'curve', 'recipe'), ('algorithm', 'operation', 'curve', 'recipe'), 'operation snapshot')
        curve = restore(snapshot['curve'])
        if operation == 'impulse':
            recipe = snapshot['recipe']
            _fields(recipe, ('type', 'at', 'magnitude', 'relaxation', 'attack'), ('type', 'at', 'magnitude', 'relaxation', 'attack'), 'impulse recipe')
            if recipe['type'] != 'impulse': raise TypeError('Operation and recipe disagree')
            normalized = _normalize_impulse({k: recipe[k] for k in ('at','magnitude','relaxation','attack')}, _domain(curve), True)
        else:
            normalized = _recipe_redistribution(snapshot['recipe'])
            lo, hi = _domain(curve)
            if normalized['from'] < 1 or normalized['until'] <= lo or normalized['until'] > hi:
                raise ValueError('Redistribution window must lie inside the available domain')
            if normalized['measure'] == 'actualDeviation' and normalized['magnitude'] != 0 and (curve.config['floor'] != 'none' or curve.config['ceilingMargin'] is not None):
                raise ValueError('Actual-deviation redistribution requires an unconstrained source')
        return _OperationCurve(curve, normalized, restore)
    if operation == 'coupling':
        _fields(snapshot, ('algorithm', 'operation', 'curves', 'strength', 'window'), ('algorithm', 'operation', 'curves', 'strength', 'window'), 'coupling snapshot')
        if not isinstance(snapshot['curves'], list) or not 2 <= len(snapshot['curves']) <= _MAX_CURVES:
            raise ValueError('Invalid coupling source count')
        curves = [restore(item) for item in snapshot['curves']]
        return _CouplingGroup(curves, _finite(snapshot['strength'], 'strength'), snapshot['window'], True)
    if operation == 'couplingView':
        _fields(snapshot, ('algorithm', 'operation', 'group', 'index'), ('algorithm', 'operation', 'group', 'index'), 'coupling view snapshot')
        group = restore(snapshot['group'])
        index = snapshot['index']
        if not isinstance(group, _CouplingGroup) or isinstance(index, bool) or not isinstance(index, int) or not 0 <= index < len(group.curves):
            raise TypeError('Invalid coupling view')
        return group.curves[index]
    raise TypeError('Unknown operation snapshot kind')
