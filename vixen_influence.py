"""Replayable external influences over a Vixen timeline."""
import math
from vixen_influence_calculus import influence_jet
import sys
from vixen_api import CurveCapabilities,evaluate_curve,register_unary_curve,latest_write_time,curve_origin,curve_lineage,curve_config,append_influence,materialize_influences,snapshot_curve,fork_curve
from collections.abc import Mapping
from types import MappingProxyType

from vixen_guide import capture_guide, compile_guide
from vixen_float import sum_products

INFLUENCE_ALGORITHM = 'vixen-influence-c2/1'
INFLUENCE_ALGORITHM_V2 = 'vixen-influence-c2/2'
INFLUENCE_ALGORITHM_V3 = 'vixen-influence-c2/3'
_OPTION_KEYS = {'at', 'amount', 'handoffWidth', 'count', 'until', 'releaseWidth', 'reference', 'mode'}
_SNAPSHOT_V1_KEYS = {'algorithm', 'curve', 'guide', 'at', 'amount', 'handoffWidth'}
_SNAPSHOT_V2_BASE_KEYS = {'algorithm', 'curve', 'guide', 'at', 'amount', 'handoffWidth', 'reference'}
_SNAPSHOT_V2_WINDOW_KEYS = _SNAPSHOT_V2_BASE_KEYS | {'until', 'releaseWidth'}
_MISSING = object()


def is_influence_algorithm(value):
    return value in (INFLUENCE_ALGORITHM, INFLUENCE_ALGORITHM_V2, INFLUENCE_ALGORITHM_V3)


def _finite(value, lo, hi, name='Influence number'):
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or value < lo or value > hi:
        raise ValueError(f'{name} outside its finite domain')
    return value


def _gate(t):
    return 0 if t <= 0 else 1 if t >= 1 else t * t * t * (10 + t * (-15 + 6 * t))


def _ramp(t):
    return 0 if t <= 0 else 1 if t >= 1 else t * t * t * (6 + t * (-8 + 3 * t))


def _policy(value, p):
    lo, hi, width = p['floorValue'], p['ceilingValue'], p['boundaryWidth']
    if lo is not None and hi is not None and hi <= lo:
        return lo
    if lo is not None:
        if value <= lo:
            value = lo
        elif width > 0 and value < lo + width:
            value = lo + width * _ramp((value - lo) / width)
    if hi is not None:
        if value >= hi:
            value = hi
        elif width > 0 and value > hi - width:
            value = hi - width * _ramp((hi - value) / width)
    return value


def _affected(p, target, amount, config, reference, mode):
    if amount == 0:
        return p
    extra = p['base'] * amount * config['boundarySoftness']
    lo, hi, width = p['floorValue'], p['ceilingValue'], p['boundaryWidth']
    if lo is not None and hi is not None:
        room = max(0, (hi - lo) / 2 - width)
        if extra > 0 and room > 0:
            increment=extra*room/(room+extra)
            if not math.isfinite(increment):increment=extra/(1+extra/room) if extra<=room else room/(1+room/extra)
            width+=increment
    elif lo is not None or hi is not None:
        width += extra
    p = {**p, 'boundaryWidth': width}
    guide = _policy(p['unconstrainedGuideValue'], p)

    if mode == 'add':
        increment = amount * target if reference == 'value' else p['base'] * (amount * target)
        unconstrained_value = p['unconstrainedValue'] + increment
    elif reference == 'value':
        unconstrained_value = target if amount == 1 else (1 - amount) * p['unconstrainedValue'] + amount * target
    elif reference == 'baseline':
        target_value = p['base'] * (1 + target)
        unconstrained_value = target_value if amount == 1 else (1 - amount) * p['unconstrainedValue'] + amount * target_value
    else:
        target_value = p['unconstrainedGuideValue'] + p['base'] * target
        unconstrained_value = target_value if amount == 1 else (1 - amount) * p['unconstrainedValue'] + amount * target_value

    if not math.isfinite(unconstrained_value):
        prior=[[p['unconstrainedValue']]] if mode=='add' else [[1-amount,p['unconstrainedValue']]]
        if reference=='value':terms=[[amount,target]]
        elif mode=='add':terms=[[p['base'],amount,target]]
        elif reference=='baseline':terms=[[p['base'],amount,1+target]]
        else:terms=[[amount,p['unconstrainedGuideValue']],[p['base'],amount,target]]
        unconstrained_value=sum_products(prior+terms)
    if not math.isfinite(unconstrained_value) or not math.isfinite(width):
        raise ValueError('Influence sample exceeds the finite numeric range')
    value = _policy(unconstrained_value, p)
    deviation = (value - p['base']) / p['base']
    positive_multiplier = math.exp(.55 * math.tanh(deviation))
    result = {
        **p,
        'guideValue': guide,
        'guideDeviation': (guide - p['base']) / p['base'],
        'value': value,
        'unconstrainedValue': unconstrained_value,
        'unconstrainedDeviation': (unconstrained_value - p['base']) / p['base'],
        'deviation': deviation,
        'multiplier': 1 + deviation,
        'activity': .5 + .5 * math.tanh(deviation),
        'positiveMultiplier': positive_multiplier,
        'positiveValue': p['base'] * positive_multiplier,
        'constraintApplied': value != unconstrained_value,
    }
    if any(isinstance(v,(int,float)) and not math.isfinite(v) for v in result.values()):
        raise ValueError('Influence sample exceeds the finite numeric range')
    return MappingProxyType(result)


def _latest(curve):
    return latest_write_time(curve)


def _plain(value):
    if isinstance(value, Mapping):
        return {key: _plain(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [_plain(item) for item in value]
    return value


class _FrozenDict(dict):
    """JSON-serializable immutable dictionary used by replay snapshots."""
    def __init__(self, value=()):
        dict.__init__(self, value)

    @staticmethod
    def _immutable(*args, **kwargs):
        raise TypeError('Snapshot mappings are read-only')

    __setitem__ = _immutable
    __delitem__ = _immutable
    clear = _immutable
    pop = _immutable
    popitem = _immutable
    setdefault = _immutable
    update = _immutable
    __ior__ = _immutable


def _freeze_json(value):
    if isinstance(value, Mapping):
        return _FrozenDict({key: _freeze_json(item) for key, item in value.items()})
    if isinstance(value, dict):
        return _FrozenDict({key: _freeze_json(item) for key, item in value.items()})
    if isinstance(value, (list, tuple)):
        return tuple(_freeze_json(item) for item in value)
    return value


def _weight(x, recipe):
    at, amount, width = recipe['at'], recipe['amount'], recipe['handoffWidth']
    if x <= at:
        return 0
    if not recipe['windowed']:
        return amount * _gate((x - at) / width)
    handoff_end = at + width
    if x < handoff_end:
        return amount * _gate((x - at) / width)
    until, release_width = recipe['until'], recipe['releaseWidth']
    if release_width == 0:
        return amount if x < until else 0
    if x <= until:
        return amount
    release_end = until + release_width
    if x < release_end:
        return amount * (1 - _gate((x - until) / release_width))
    return 0


def _recipe_options(recipe):
    options = {'at': recipe['at'], 'amount': recipe['amount'], 'handoffWidth': recipe['handoffWidth']}
    if recipe['windowed']:
        options['until'] = recipe['until']
        options['releaseWidth'] = recipe['releaseWidth']
    if recipe['reference'] != 'value':
        options['reference'] = recipe['reference']
    if recipe['mode'] == 'add':
        options['mode'] = recipe['mode']
    return options


class Influenced(CurveCapabilities):
    __slots__ = ('_curve', '_compiled', '_recipe', '_entry', '_domain', '_influences', '_bounds', '_schema2', '_evaluation_state', '_sealed')

    def __setattr__(self, key, value):
        if getattr(self, '_sealed', False):
            raise AttributeError('Influence timelines are immutable')
        object.__setattr__(self, key, value)

    def __init__(self, curve, target, options, carried=False):
        if not isinstance(options, Mapping) or set(options) - _OPTION_KEYS:
            raise TypeError('Influence options: at, amount, handoffWidth, count, until, releaseWidth, reference, mode')
        if 'count' in options and (not callable(target)):
            raise TypeError('count applies only to callback capture')

        domain_lo, domain_hi = curve.domain
        at = options['at'] if 'at' in options else max(curve.config['openingEndpoint'], _latest(curve), domain_lo)
        _finite(at, 1 if carried else domain_lo, domain_hi, 'Influence activation')
        amount = options['amount'] if 'amount' in options else .5
        _finite(amount, 0, 1, 'Influence amount')
        if not carried and at < _latest(curve):
            raise ValueError('Influence cannot precede existing timeline events')

        if callable(target):
            count = options['count'] if 'count' in options else 129
            target = capture_guide(target, domain=(at, domain_hi), count=count)
        compiled = compile_guide(target)
        windowed = 'until' in options or 'releaseWidth' in options
        mode = options.get('mode', 'blend')
        if mode not in ('blend', 'add'):
            raise TypeError('mode must be blend or add')
        reference = options['reference'] if 'reference' in options else 'baseline' if mode == 'add' else 'value'
        if reference not in ('value', 'baseline', 'backbone'):
            raise TypeError('reference must be value, baseline, or backbone')

        handoff_limit = min(domain_hi, compiled.domain[1])
        width = options['handoffWidth'] if 'handoffWidth' in options else min(16, handoff_limit - at)
        _finite(width, math.nextafter(0.0, 1.0), 50000, 'Handoff width')
        handoff_end = at + width
        if not math.isfinite(handoff_end) or handoff_end <= at or handoff_end > domain_hi or compiled.domain[0] > at or handoff_end > handoff_limit:
            raise ValueError('Guide must cover a representable handoff inside the domain')

        if windowed:
            if 'until' not in options or 'releaseWidth' not in options:
                raise TypeError('Window requires until and positive releaseWidth')
            release_width = options['releaseWidth']
            _finite(release_width, math.ulp(0.0), 50000, 'Release width')
            until = options['until'] if 'until' in options else domain_hi - release_width
            _finite(until, handoff_end, domain_hi, 'Release start')
            release_end = until + release_width
            if not math.isfinite(release_end) or (release_width > 0 and release_end <= until) or release_end > domain_hi:
                raise ValueError('Windowed influence exceeds the representable domain')
            if compiled.domain[1] < release_end:
                raise ValueError('Guide must cover the active window through its release endpoint')
        else:
            until = release_width = release_end = None
            if compiled.domain[0] > at or handoff_end > handoff_limit:
                raise ValueError('Guide must cover a representable handoff inside the domain')

        recipe = MappingProxyType({
            'at': at,
            'amount': amount,
            'handoffWidth': width,
            'windowed': windowed,
            'until': until,
            'releaseWidth': release_width,
            'releaseEnd': release_end,
            'reference': reference,
            'mode': mode,
        })
        zero_guide = mode == 'add' and (all(c == 0 for c in compiled.spec['coefficients']) if compiled.spec['kind'] == 'polynomial'
            else all(k['y'] == 0 and k['d1'] == 0 and k['d2'] == 0 for k in compiled.spec['knots']))
        schema2 = mode == 'add' or windowed or reference != 'value'
        if schema2:
            entry = {'at': at, 'amount': amount, 'handoffWidth': width, 'guide': compiled.spec, 'reference': reference}
            if windowed:
                entry.update(until=until, releaseWidth=release_width)
        else:
            entry = {'at': at, 'amount': amount, 'handoffWidth': width, 'guide': compiled.spec}
        if mode == 'add':
            entry['mode'] = mode
        entry = MappingProxyType(entry)
        domain = (domain_lo, domain_hi) if windowed else (domain_lo, min(domain_hi, compiled.domain[1]))
        config = curve.config
        lower = -sys.float_info.max if config['floor'] == 'none' else -1 if config['floor'] == 'zero' else 0 if config['floor'] == 'baseline' else -.25 * config['strength']
        upper = sys.float_info.max if config['ceilingMargin'] is None else (0 if config['floor'] == 'baseline' else .25 * config['strength']) + config['ceilingMargin']

        object.__setattr__(self, '_curve', curve)
        object.__setattr__(self, '_compiled', compiled)
        object.__setattr__(self, '_recipe', recipe)
        object.__setattr__(self, '_entry', entry)
        object.__setattr__(self, '_domain', domain)
        influence_head = append_influence(curve, entry)
        object.__setattr__(self, '_influences', influence_head)
        object.__setattr__(self, '_bounds', MappingProxyType({
            **curve.bounds,
            'deviation': (lower, upper),
            'multiplier': (1 + lower, 1 + upper),
        }))
        object.__setattr__(self, '_schema2', schema2)
        register_unary_curve(
            self, curve, domain=domain, latest=max(latest_write_time(curve), at),
            influence_head=influence_head, validate=self._check,
            interval=(at, release_end if windowed else math.inf),
            always_identity=amount == 0 or zero_guide,
            identity_at=lambda x: amount == 0 or x <= at or (windowed and x >= release_end),
            snapshot_frame=lambda parents:self._snapshot_with(parents[0]),
            rebuild=lambda parents:Influenced(parents[0], compiled.spec, _recipe_options(recipe), carried=True),
            discard_on_tail=lambda from_:windowed and release_end <= from_,
            transform=lambda parent, x, h, explicit: parent if zero_guide or _weight(x, recipe) == 0 else _affected(parent, compiled.evaluate(x), _weight(x, recipe), config, reference, mode),
            jet_transform=lambda parent,x,h,explicit:influence_jet(parent,compiled,recipe,config,parent['sample'] if zero_guide or _weight(x,recipe)==0 else _affected(parent['sample'],compiled.evaluate(x),_weight(x,recipe),config,reference,mode),x,_policy),
        )
        object.__setattr__(self, '_sealed', True)

    @property
    def algorithm(self):
        return INFLUENCE_ALGORITHM_V3 if self._recipe['mode'] == 'add' else INFLUENCE_ALGORITHM_V2 if self._schema2 else INFLUENCE_ALGORITHM

    @property
    def config(self):
        return curve_config(self._curve)

    @property
    def bounds(self):
        return self._bounds

    @property
    def domain(self):
        return self._domain

    @property
    def lineage(self):
        return curve_lineage(self._curve)

    @property
    def influences(self):
        return materialize_influences(self._influences)

    @property
    def dips(self):
        return curve_origin(self._curve).dips

    def _check(self, x):
        _finite(x, *self.domain, 'Influence coordinate')

    def _sample(self, x, latent=_MISSING):
        return evaluate_curve(self, x) if latent is _MISSING else evaluate_curve(self, x, latent)

    def sample(self, x):
        return self._sample(x)

    def at(self, x, latent):
        return self._sample(x, latent)

    def location(self, x):
        self._check(x)
        return curve_origin(self._curve).location(x)

    def inspect(self, x):
        self._check(x)
        return curve_origin(self._curve).inspect(x)

    def ridges(self):
        return curve_origin(self._curve).ridges()

    def influence_at(self, x):
        self._check(x)
        weight = _weight(x, self._recipe)
        parent = evaluate_curve(self._curve, x)
        output = None if weight == 0 else self._compiled.evaluate(x)
        reference = self._recipe['reference']
        if self._recipe['mode'] == 'add':
            increment = 0 if output is None else weight * output if reference == 'value' else parent['base'] * (weight * output)
            return MappingProxyType({'mode': 'add', 'weight': weight, 'targetValue': None, 'sourceValue': parent['value'],
                'followingError': None, 'increment': increment, 'deviationIncrement': increment / parent['base']})
        target = None if output is None else output if reference == 'value' else parent['base'] * (1 + output) if reference == 'baseline' else parent['unconstrainedGuideValue'] + parent['base'] * output
        return MappingProxyType({
            'weight': weight, 'targetValue': target, 'sourceValue': parent['value'],
            'followingError': None if target is None else self.sample(x)['value'] - target,
        })

    def _snapshot_with(self, parent_snapshot):
        recipe = self._recipe
        if not self._schema2:
            value = {
                'algorithm': INFLUENCE_ALGORITHM,
                'curve': parent_snapshot,
                'guide': self._compiled.spec,
                'at': recipe['at'],
                'amount': recipe['amount'],
                'handoffWidth': recipe['handoffWidth'],
            }
        else:
            value = {
                'algorithm': self.algorithm,
                'curve': parent_snapshot,
                'guide': self._compiled.spec,
                'at': recipe['at'],
                'amount': recipe['amount'],
                'handoffWidth': recipe['handoffWidth'],
                'reference': recipe['reference'],
            }
            if recipe['mode'] == 'add':
                value['mode'] = recipe['mode']
            if recipe['windowed']:
                value['until'] = recipe['until']
                value['releaseWidth'] = recipe['releaseWidth']
        return _freeze_json(_plain(value))

    def snapshot(self):
        return snapshot_curve(self)

    def tail(self, from_):
        self._check(from_)
        return snapshot_curve(self,from_)

    def fork(self, *, at, operation, handoffWidth=_MISSING):
        earliest = max(self.domain[0], latest_write_time(self))
        _finite(at, earliest, self.domain[1], 'Fork activation')
        width = min(16, self.domain[1] - at) if handoffWidth is _MISSING else handoffWidth
        _finite(width, math.nextafter(0.0, 1.0), 50000, 'Fork handoff width')
        end = at + width
        if not math.isfinite(end) or end <= at or end > self.domain[1]:
            raise ValueError('Fork exceeds influence domain')
        return fork_curve(self,dict(at=at,operation=operation,handoffWidth=width))

    def influence(self, target, **options):
        return Influenced(self, target, options)


def with_influence(curve, target, **options):
    return Influenced(curve, target, options)


def _validate_snapshot(snapshot):
    if not isinstance(snapshot, Mapping):
        raise TypeError('Invalid influence snapshot')
    keys = set(snapshot)
    algorithm = snapshot.get('algorithm')
    if algorithm == INFLUENCE_ALGORITHM:
        if keys != _SNAPSHOT_V1_KEYS:
            raise TypeError('Invalid schema 1 influence snapshot fields')
        options = {key: snapshot[key] for key in ('at', 'amount', 'handoffWidth')}
        return snapshot['guide'], options
    schema3 = algorithm == INFLUENCE_ALGORITHM_V3
    if not schema3 and algorithm != INFLUENCE_ALGORITHM_V2:
        raise TypeError('Unsupported influence algorithm')
    if schema3 and snapshot.get('mode') != 'add':
        raise TypeError('Schema 3 requires additive mode')
    has_until = 'until' in snapshot
    has_release_width = 'releaseWidth' in snapshot
    if has_until != has_release_width:
        raise TypeError('Window snapshots require both until and releaseWidth')
    expected = (_SNAPSHOT_V2_WINDOW_KEYS if has_until else _SNAPSHOT_V2_BASE_KEYS) | ({'mode'} if schema3 else set())
    if keys != expected:
        raise TypeError('Invalid schema 2 influence snapshot fields')
    options = {key: snapshot[key] for key in ('at', 'amount', 'handoffWidth', 'reference')}
    if schema3:
        options['mode'] = snapshot['mode']
    if has_until:
        options['until'] = snapshot['until']
        options['releaseWidth'] = snapshot['releaseWidth']
    elif not schema3 and snapshot['reference'] == 'value':
        raise TypeError('Schema 2 requires an extended influence recipe')
    return snapshot['guide'], options


def restore_influence(snapshot, restore):
    if not callable(restore):
        raise TypeError('A parent restore function is required')
    frames = []
    seen = set()
    current = snapshot
    while isinstance(current, Mapping) and is_influence_algorithm(current.get('algorithm')):
        identity = id(current)
        if identity in seen:
            raise ValueError('Cyclic influence composition')
        seen.add(identity)
        frames.append(_validate_snapshot(current))
        current = current.get('curve')
    result = restore(current)
    for guide, options in reversed(frames):
        result = Influenced(result, guide, options, carried=True)
    return result
