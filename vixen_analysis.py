from vixen_correlation import exact_correlation_factor
import vixen_interval as _I
"""Dependency-free analysis helpers for Vixen curves."""
import math
from types import MappingProxyType
from collections.abc import Mapping


def _finite(value):
    return not isinstance(value, bool) and isinstance(value, (int, float)) and math.isfinite(value)


def _pair(value, label):
    if not isinstance(value, (list, tuple)) or len(value) != 2 or not all(_finite(x) for x in value) or value[0] > value[1]:
        raise ValueError(f'{label} must be an ordered pair of finite numbers')
    return (value[0], value[1])


def _options(options, allowed):
    if not isinstance(options, dict) or set(options) - allowed:
        raise TypeError('Invalid options object or unknown field')


def _crossing_config(options):
    _options(options, {'enclose', 'enclosePoint', 'evaluate', 'measure', 'domain', 'continuous', 'tolerance', 'maxIntervals', 'maxIterations', 'maxDepth'})
    tolerance = options.get('tolerance', 1e-6)
    if not _finite(tolerance) or tolerance <= 0:
        raise ValueError('tolerance must be finite and positive')
    limits = {}
    for name, default in (('maxIntervals', 10000), ('maxIterations', 10000), ('maxDepth', 64)):
        value = options.get(name, default)
        if isinstance(value, bool) or not isinstance(value, int) or value < 1:
            raise ValueError(f'{name} must be a positive integer')
        limits[name] = value
    measure = options.get('measure', 'deviation')
    if not isinstance(measure, str) or not measure:
        raise TypeError('measure must be a nonempty string')
    if 'continuous' in options and not isinstance(options['continuous'], bool):
        raise TypeError('continuous must be boolean')
    return tolerance, limits, measure


def _read_result(result, measure):
    if isinstance(result, Mapping):
        value = result.get(measure)
    else:
        value = result
    if not _finite(value):
        raise ValueError(f'Curve output {measure} must be finite')
    return value


def _checked_enclosure(provider, lo, hi):
    return _pair(provider(lo, hi), 'enclosure')


def _outstanding(items, reason):
    return [{'interval': (item[0], item[1]), 'reason': reason} for item in items]


def next_crossing(curve, threshold, from_, horizon, options=None):
    """Return found, absent, or unresolved for an enclosed first crossing query."""
    options = {} if options is None else options
    if not all(_finite(v) for v in (threshold, from_, horizon)) or from_ > horizon:
        raise ValueError('threshold and ordered query bounds must be finite')
    tolerance, limits, measure = _crossing_config(options)
    domain_value = options.get('domain', getattr(curve, 'domain', None))
    if domain_value is None:
        return {'status': 'unresolved', 'reason': 'domain-unavailable', 'interval': (from_, horizon), 'unresolved': [{'interval': (from_, horizon), 'reason': 'domain-unavailable'}]}
    domain = _pair(domain_value, 'domain')
    if from_ < domain[0] or horizon > domain[1]:
        raise ValueError('Query must stay inside the curve domain')
    provider = options.get('enclose')
    if provider is not None and not callable(provider):
        raise TypeError('enclose must be callable')
    if provider is None and measure == 'deviation' and callable(getattr(curve, 'enclose_deviation', None)):
        provider = curve.enclose_deviation
    if provider is None:
        provider = getattr(curve, 'enclosure_provider', None) or getattr(curve, 'enclose', None)
    if provider is not None and not callable(provider):
        raise TypeError('Curve enclosure provider must be callable')
    if provider is None:
        return {'status': 'unresolved', 'reason': 'enclosure-unavailable', 'interval': (from_, horizon), 'unresolved': [{'interval': (from_, horizon), 'reason': 'enclosure-unavailable'}]}
    evaluator = options.get('evaluate')
    if evaluator is not None and not callable(evaluator):
        raise TypeError('evaluate must be callable')
    if evaluator is None:
        evaluator = curve if callable(curve) else getattr(curve, 'sample', None)
    if evaluator is None:
        return {'status': 'unresolved', 'reason': 'evaluator-unavailable', 'interval': (from_, horizon), 'unresolved': [{'interval': (from_, horizon), 'reason': 'evaluator-unavailable'}]}
    point_provider = options.get('enclosePoint', getattr(curve, 'enclose_point', None))
    if point_provider is not None and not callable(point_provider):
        raise TypeError('Point enclosure provider must be callable')
    point_cache={};checks=0;point_checks=0;stack=[(from_,horizon,0)]
    continuous=options.get('continuous',False)
    def point(x):
        nonlocal point_checks
        if x not in point_cache:
            interval=_pair(point_provider(x),'point enclosure') if point_provider is not None else _checked_enclosure(provider,x,x)
            point_checks+=1
            point_cache[x]={'interval':interval,'value':_read_result(evaluator(x),measure)}
        return point_cache[x]
    def exact(p):return p['interval'][0]==threshold==p['interval'][1]
    def opposite(a,b):
        return (a['interval'][1]<threshold<b['interval'][0]) or (a['interval'][0]>threshold>b['interval'][1])
    def unresolved(q,reason):
        pending=[{'interval':(q[0],q[1]),'reason':reason}]+_outstanding(stack,'not-visited')
        pending.sort(key=lambda item:item['interval'][0])
        return {'status':'unresolved','reason':reason,'interval':(q[0],q[1]),'certifiedBefore':(from_,q[0]),'certifiedBeforeUpperExclusive':True,'unresolved':pending,'enclosureChecks':checks,'pointChecks':point_checks}
    def found(lo,hi,a,b):
        return {'status':'found','interval':(lo,hi),'endpointValues':(a['value'],b['value']),'endpointEnclosures':(a['interval'],b['interval']),'certifiedBefore':(from_,lo),'certifiedBeforeUpperExclusive':True,'unresolved':[],'enclosureChecks':checks,'pointChecks':point_checks,'continuousAssumed':continuous,'pointIsolated':lo==hi}
    while stack:
        q=stack.pop();lo,hi,depth=q
        if checks>=min(limits['maxIntervals'],limits['maxIterations']):return unresolved(q,'budget')
        box=_checked_enclosure(provider,lo,hi);checks+=1
        if threshold<box[0] or threshold>box[1]:continue
        left=point(lo)
        if exact(left):return found(lo,lo,left,left)
        width=hi-lo;right=point(hi)
        if width<=tolerance/2:
            if exact(right) or (continuous and opposite(left,right)):return found(lo,hi,left,right)
            a=max(from_,lo-tolerance/4);b=min(horizon,hi+tolerance/4)
            if continuous and b-a<=tolerance:
                pa,pb=point(a),point(b)
                if opposite(pa,pb):return found(a,b,pa,pb)
            if not continuous:return unresolved(q,'continuity-required')
        mid=lo/2+hi/2
        if depth>=limits['maxDepth'] or mid==lo or mid==hi:return unresolved(q,'depth' if depth>=limits['maxDepth'] else 'machine-spacing')
        stack.extend([(mid,hi,depth+1),(lo,mid,depth+1)])
    return {'status':'absent','interval':(from_,horizon),'certifiedAbsent':(from_,horizon),'unresolved':[],'enclosureChecks':checks,'pointChecks':point_checks}


def make_lipschitz_enclosure(evaluate, bound, point_error=0):
    """Build a conditional enclosure from caller-certified Lipschitz assumptions."""
    if not callable(evaluate):
        raise TypeError('evaluate must be callable')
    if not _finite(bound) or bound < 0 or not _finite(point_error) or point_error < 0:
        raise ValueError('bound and point_error must be finite and nonnegative')

    def enclose(lo, hi):
        if not _finite(lo) or not _finite(hi) or lo > hi:
            raise ValueError('Invalid enclosure interval')
        mid=max(lo,min(hi,lo/2+hi/2))
        value = evaluate(mid)
        if not _finite(value):
            raise ValueError('Point evaluation must be finite')
        distance=max(_I.subtract(_I.interval(mid),_I.interval(lo)).hi,_I.subtract(_I.interval(hi),_I.interval(mid)).hi)
        radius=_I.add(_I.multiply(_I.interval(bound),_I.interval(distance)),_I.interval(point_error)).hi
        result=_I.add(_I.interval(value),_I.interval(-radius,radius))
        return (result.lo,result.hi)
    return enclose


def _population(population):
    if not isinstance(population, (list, tuple)) or not population:
        raise ValueError('population must be a nonempty sequence')
    rows = []
    for index, item in enumerate(population):
        if _finite(item):
            deviation, weight = item, 1.0
        elif isinstance(item, dict) and set(item) == {'deviation', 'weight'}:
            deviation, weight = item['deviation'], item['weight']
        else:
            raise TypeError(f'Invalid population entry at {index}')
        if not _finite(deviation) or not _finite(weight) or weight <= 0:
            raise ValueError(f'Invalid population value or weight at {index}')
        rows.append((float(deviation), float(weight)))
    return rows


def _freeze(value):
    if isinstance(value, dict):
        return MappingProxyType({key: _freeze(item) for key, item in value.items()})
    if isinstance(value, (list, tuple)):
        return tuple(_freeze(item) for item in value)
    return value


def _plain(value):
    if isinstance(value, MappingProxyType):
        return {key: _plain(item) for key, item in value.items()}
    if isinstance(value, tuple):
        return [_plain(item) for item in value]
    return value


def _occupancy_value(recipe, deviation):
    if not _finite(deviation):
        raise ValueError('deviation must be finite')
    t=recipe['outputThreshold']
    if deviation==recipe['cutoff']:return t
    z=2*((recipe['slope']/recipe['scale'])*(deviation-recipe['cutoff']))
    if z>=0:
        e=math.exp(-z);return t/(t+(1-t)*e)
    e=math.exp(z);return (t*e)/(1-t+t*e)


class OccupancyCalibration:
    __slots__ = ('recipe', 'target_fraction', 'achieved_fraction', 'difference', 'cutoff', 'tie_handling', 'total_population', 'total_weight')

    def __init__(self, recipe):
        self.recipe = _freeze(dict(recipe))
        self.target_fraction = recipe['targetFraction']
        self.achieved_fraction = recipe['achievedFraction']
        self.difference = self.achieved_fraction - self.target_fraction
        self.cutoff = recipe['cutoff']
        self.tie_handling = recipe['tieHandling']
        self.total_population = recipe['totalPopulation']
        self.total_weight = recipe['totalWeight']

    def evaluate(self, deviation):
        return _occupancy_value(self.recipe, deviation)

    def serialize(self):
        return _plain(self.recipe)


def _next_up(value):
    return math.nextafter(value, math.inf)


def calibrate_occupancy(population, options):
    _options(options, {'targetFraction', 'outputThreshold', 'slope', 'scale'})
    target = options.get('targetFraction')
    threshold = options.get('outputThreshold')
    slope = options.get('slope', 1.0)
    scale = options.get('scale', 1.0)
    if not _finite(target) or not 0 <= target <= 1:
        raise ValueError('targetFraction must be in [0,1]')
    if not _finite(threshold) or not 0 < threshold < 1:
        raise ValueError('outputThreshold must be strictly inside (0,1)')
    if not _finite(slope) or slope <= 0 or not _finite(scale) or scale <= 0 or not _finite(slope / scale) or slope / scale == 0:
        raise ValueError('slope and scale must have a finite positive ratio')
    rows = _population(population)
    total_weight = math.fsum(weight for _, weight in rows)
    if not math.isfinite(total_weight) or total_weight <= 0:
        raise ValueError('Total population weight must be finite and positive')
    values = sorted(set(value for value, _ in rows))
    if target == 1:
        candidates = [values[0]]
    elif target == 0:
        candidates = [_next_up(values[-1])]
    else:
        candidates = []
        for value in values:
            candidates.append(value)
            above = _next_up(value)
            if math.isfinite(above):
                candidates.append(above)
    scored = []
    for cutoff in candidates:
        if not math.isfinite(cutoff):
            continue
        candidate={'outputThreshold':threshold,'slope':slope,'scale':scale,'cutoff':cutoff}
        included = math.fsum(weight for value, weight in rows if _occupancy_value(candidate,value)>=threshold)
        achieved = included / total_weight
        scored.append((abs(achieved - target), achieved > target, cutoff, achieved))
    if not scored:
        raise ValueError('Requested endpoint fraction has no finite representable cutoff')
    _, _, cutoff, achieved = min(scored)
    q = .5*(math.log(threshold)-math.log1p(-threshold))
    center = cutoff - q / (slope / scale)
    if not math.isfinite(center):
        raise ValueError('Calibration recipe is not finite for this population and slope')
    tie_handling = 'inclusive-at-cutoff' if any(value == cutoff for value, _ in rows) else 'values-at-or-above-cutoff'
    recipe = {'format': 'vixen-occupancy/1', 'targetFraction': float(target), 'outputThreshold': float(threshold), 'slope': float(slope), 'scale': float(scale), 'cutoff': float(cutoff), 'center': float(center), 'totalPopulation': len(rows), 'totalWeight': total_weight, 'achievedFraction': achieved, 'tieHandling': tie_handling}
    return OccupancyCalibration(recipe)


def validate_occupancy(calibration, holdout):
    recipe = calibration.recipe if isinstance(calibration, OccupancyCalibration) else calibration
    if not isinstance(recipe, (dict, MappingProxyType)) or recipe.get('format') != 'vixen-occupancy/1':
        raise TypeError('A Vixen occupancy recipe is required')
    rows = _population(holdout)
    total_weight = math.fsum(weight for _, weight in rows)
    if not math.isfinite(total_weight) or total_weight <= 0:
        raise ValueError('Total holdout weight must be finite and positive')
    selected = [(value, weight) for value, weight in rows if _occupancy_value(recipe, value) >= recipe['outputThreshold']]
    included = math.fsum(weight for _, weight in selected)
    achieved = included / total_weight
    return {'targetFraction': recipe['targetFraction'], 'achievedFraction': achieved, 'difference': achieved - recipe['targetFraction'], 'absoluteError': abs(achieved - recipe['targetFraction']), 'totalPopulation': len(rows), 'totalWeight': total_weight, 'highPopulation': len(selected), 'highWeight': included}


def restore_occupancy(recipe):
    if not isinstance(recipe, dict) or recipe.get('format') != 'vixen-occupancy/1':
        raise TypeError('Invalid occupancy recipe')
    for key in ('targetFraction', 'outputThreshold', 'slope', 'scale', 'cutoff', 'center', 'totalWeight', 'achievedFraction'):
        if key not in recipe or not _finite(recipe[key]):
            raise ValueError(f'Invalid recipe field: {key}')
    if not 0 <= recipe['targetFraction'] <= 1 or not 0 < recipe['outputThreshold'] < 1 or recipe['slope'] <= 0 or recipe['scale'] <= 0 or not _finite(recipe['slope']/recipe['scale']) or recipe['slope']/recipe['scale']==0 or recipe['totalWeight'] <= 0 or not 0 <= recipe['achievedFraction'] <= 1:
        raise ValueError('Invalid occupancy recipe values')
    if isinstance(recipe.get('totalPopulation'), bool) or not isinstance(recipe.get('totalPopulation'), int) or recipe['totalPopulation'] < 1 or not isinstance(recipe.get('tieHandling'), str):
        raise ValueError('Invalid occupancy recipe metadata')
    return OccupancyCalibration(recipe)


def validate_correlation_matrix(matrix,tolerance=1e-10):
    return _freeze(exact_correlation_factor(matrix,tolerance))


def _seed32(seed):
    mask = 0xffffffff
    if isinstance(seed, bool):
        raise TypeError('Seed must be text or uint32')
    if isinstance(seed, int):
        if not 0 <= seed <= mask:
            raise ValueError('Numeric seed must be uint32')
        return seed
    if not isinstance(seed, str) or len(seed.encode('utf-16-le', errors='surrogatepass')) > 2048:
        raise TypeError('Seed must be text or uint32')
    raw = seed.encode('utf-16-le', errors='surrogatepass').decode('utf-16-le', errors='replace').encode('utf-8')
    value = 2166136261
    for byte in raw:
        value = ((value ^ byte) * 16777619) & mask
    return value


def _hash32(seed, index, channel):
    mask = 0xffffffff
    value = (seed ^ ((index & mask) * 0x9e3779b9 & mask) ^ ((channel & mask) * 0x85ebca6b & mask)) & mask
    value = ((value ^ (value >> 16)) * 0x7feb352d) & mask
    value = ((value ^ (value >> 15)) * 0x846ca68b) & mask
    return (value ^ (value >> 16)) & mask


def _derive_seed(root_seed, index):
    kind = 1 if isinstance(root_seed, str) else 2
    return _hash32(_hash32(_seed32(root_seed), kind, 0x56495845), index, 0x43484e4c)


class CorrelatedChannels:
    __slots__ = ('domain', 'matrix', 'factor', 'channel', 'source_seeds', 'source_configs', 'recipe', '_sources')

    def __init__(self, domain, checked, channel, seeds, sources, recipe):
        self.domain = domain
        self.matrix = checked['matrix']
        self.factor = checked['factor']
        self.channel = channel
        self.source_seeds = tuple(seeds)
        self.source_configs = tuple(_freeze({'seed': seed, 'channel': channel}) for seed in seeds)
        self.recipe = _freeze(recipe)
        self._sources = tuple(sources)

    def _combine(self, x, coordinates=None):
        if not _finite(x) or x < self.domain[0] or x > self.domain[1]:
            raise ValueError('x must lie inside the shared source domain')
        values = []
        for source, seed in zip(self._sources, self.source_seeds):
            result = source.sample(x) if coordinates is None else source.at(x, coordinates)
            if not isinstance(result, dict) or self.channel not in result or not _finite(result[self.channel]):
                raise ValueError(f'Source {seed} returned an invalid {self.channel}')
            values.append(result[self.channel])
        output = []
        for row in self.factor:
            value = math.fsum(coefficient * source for coefficient, source in zip(row, values))
            if not math.isfinite(value):
                raise ValueError('Correlated channel overflow')
            output.append(value)
        return tuple(output)

    def sample(self, x):
        return self._combine(x)

    def at(self, x, coordinates):
        return self._combine(x, coordinates)

    def serialize(self):
        return _plain(self.recipe)


def create_correlated_channels(matrix, create_source, root_seed, options=None):
    options = {} if options is None else options
    _options(options, {'channel', 'tolerance'})
    if not callable(create_source):
        raise TypeError('create_source must accept a derived seed')
    channel = options.get('channel', 'score')
    if channel not in ('score', 'unconstrainedDeviation'):
        raise ValueError('channel must be score or unconstrainedDeviation')
    checked = validate_correlation_matrix(matrix, options.get('tolerance', 1e-10))
    seeds = [_derive_seed(root_seed, i) for i in range(len(checked['matrix']))]
    if len(set(seeds)) != len(seeds):
        raise ValueError('Derived source seed collision')
    sources = [create_source(seed) for seed in seeds]
    domains = [_pair(getattr(source, 'domain', None), 'source domain') for source in sources]
    domain = (max(item[0] for item in domains), min(item[1] for item in domains))
    if domain[0] > domain[1]:
        raise ValueError('Source curves have no shared domain')
    recipe = {'format': 'vixen-correlated-channels/1', 'matrix': _plain(checked['matrix']), 'factor': _plain(checked['factor']), 'tolerance': checked['tolerance'], 'channel': channel, 'rootSeed': {'type': 'text' if isinstance(root_seed, str) else 'uint32', 'value': root_seed}, 'derivedSeeds': list(seeds), 'seedDerivation': 'hash32-domain-separated/1'}
    return CorrelatedChannels(domain, checked, channel, seeds, sources, recipe)


def measure_channel_correlation(group, points):
    sampler = group if callable(group) else getattr(group, 'sample', None)
    if not callable(sampler) or not isinstance(points, (list, tuple)) or len(points) < 2:
        raise TypeError('Provide a sampler and at least two reference points')
    rows = []
    for point in points:
        values = sampler(point)
        if not isinstance(values, (list, tuple)) or not values or not all(_finite(value) for value in values):
            raise ValueError('Sampler must return a nonempty finite numeric vector')
        rows.append(tuple(float(value) for value in values))
    size = len(rows[0])
    if any(len(row) != size for row in rows):
        raise ValueError('Sampler channel count changed')
    count = len(rows)
    means = [math.fsum(row[j] for row in rows) / count for j in range(size)]
    centered = [[row[j] - means[j] for j in range(size)] for row in rows]
    sums = [math.fsum(row[j] * row[j] for row in centered) for j in range(size)]
    if any(not math.isfinite(value) or value == 0 for value in sums):
        raise ValueError('Cannot measure correlation with zero or invalid variance')
    result = []
    for i in range(size):
        row = []
        for j in range(size):
            cross = math.fsum(values[i] * values[j] for values in centered)
            correlation = cross / math.sqrt(sums[i] * sums[j])
            if not math.isfinite(correlation) or abs(correlation) > 1 + 1e-12:
                raise ValueError('Invalid measured correlation')
            row.append(max(-1.0, min(1.0, correlation)))
        result.append(tuple(row))
    return tuple(result)
