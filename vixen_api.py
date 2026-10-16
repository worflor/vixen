"""Shared capabilities and stack-safe evaluation for composed Vixen curves."""
import math
from collections.abc import Mapping

_UNSET = object()


class _InfluenceLink:
    __slots__ = ('entry', 'previous', 'length', 'materialized')

    def __init__(self, entry, previous):
        self.entry = entry
        self.previous = previous
        self.length = (previous.length if previous is not None else 0) + 1
        self.materialized = None


class _EvaluationState:
    __slots__ = ('kind', 'curve', 'parents', 'parent', 'domain', 'latest', 'origin',
                 'influence_head', 'has_influence_head', 'validate', 'compute',
                 'transform', 'jet_transform', 'jet_compute', 'dependencies', 'identity_at', 'always_identity',
                 'start', 'end', 'jumps', 'depth', 'size', 'snapshot_frame', 'rebuild', 'discard_on_tail', 'tail_source')

    def __init__(self, curve, kind='opaque', domain=None, latest=None, origin=None,
                 influence_head=None, has_influence_head=False):
        self.kind = kind
        self.curve = curve
        self.parents = ()
        self.parent = None
        self.domain = domain
        self.latest = latest
        self.origin = curve if origin is None else origin
        self.influence_head = influence_head
        self.has_influence_head = has_influence_head
        self.validate = None
        self.compute = None
        self.transform = None
        self.jet_transform = self.jet_compute = None
        self.dependencies = None
        self.identity_at = lambda x: False
        self.always_identity = False
        self.start = -math.inf
        self.end = math.inf
        self.jumps = ()
        self.depth, self.size = 0, 1
        self.snapshot_frame = self.rebuild = self.discard_on_tail = self.tail_source = None


def _state_for(curve):
    try:
        state = curve._evaluation_state
    except (AttributeError, TypeError):
        state = None
    if state is not None:
        return state
    try:
        domain = curve.domain
    except (AttributeError, TypeError, RecursionError):
        domain = None
    return _EvaluationState(curve, domain=domain)


def register_root_curve(curve, domain, latest=1):
    state = _EvaluationState(curve, domain=domain, latest=latest, origin=curve,
                             influence_head=None, has_influence_head=True)
    object.__setattr__(curve, '_evaluation_state', state)
    return curve


def curve_origin(curve):
    return _state_for(curve).origin


def curve_lineage(curve):
    return curve_origin(curve).lineage


def curve_config(curve):
    return curve_origin(curve).config


def curve_domain(curve):
    state = _state_for(curve)
    return state.domain if state.domain is not None else curve.domain


def latest_write_time(curve):
    state = _state_for(curve)
    if state.latest is not None:
        return state.latest
    latest = 1
    for event in getattr(curve, 'lineage', ()):
        latest = max(latest, event['at'] if isinstance(event, Mapping) else event.at)
    for event in getattr(curve, 'influences', ()):
        latest = max(latest, event['at'] if isinstance(event, Mapping) else event.at)
    state.latest = latest
    return latest


def curve_influence_head(curve):
    state = _state_for(curve)
    if state.has_influence_head:
        return state.influence_head
    head = None
    for entry in getattr(curve, 'influences', ()):
        head = _InfluenceLink(entry, head)
    state.influence_head = head
    state.has_influence_head = True
    return head


def append_influence(curve, entry):
    return _InfluenceLink(entry, curve_influence_head(curve))


def materialize_influences(head):
    if head is None:
        return ()
    if head.materialized is None:
        result = [None] * head.length
        node, index = head, head.length - 1
        while node is not None:
            result[index] = node.entry
            index -= 1
            node = node.previous
        head.materialized = tuple(result)
    return head.materialized


def curve_influences(curve):
    return materialize_influences(curve_influence_head(curve))


def _inside(domain, x):
    return domain is not None and len(domain) == 2 and domain[0] <= x <= domain[1]


def _make_jumps(state):
    parent = state.parent
    state.jumps = ({'next': parent, 'min_start': state.start, 'max_end': state.end,
                    'domain_lo': state.domain[0] if state.domain is not None else -math.inf,
                    'domain_hi': state.domain[1] if state.domain is not None else math.inf,
                    'always': state.always_identity},)
    jumps = list(state.jumps)
    k = 1
    while True:
        left = jumps[k - 1]
        middle = left['next']
        inherited = getattr(middle, 'jumps', ())
        if len(inherited) < k:
            break
        right = inherited[k - 1]
        jumps.append({'next': right['next'],
                      'min_start': min(left['min_start'], right['min_start']),
                      'max_end': max(left['max_end'], right['max_end']),
                      'domain_lo': max(left['domain_lo'], right['domain_lo']),
                      'domain_hi': min(left['domain_hi'], right['domain_hi']),
                      'always': left['always'] and right['always']})
        k += 1
    state.jumps = tuple(jumps)


def register_unary_curve(curve, parent, *, domain, latest=None, origin=None,
                         influence_head=_UNSET, validate=None, identity_at=None,
                         always_identity=False, interval=(-math.inf, math.inf),
                         dependencies=None, snapshot_frame=None, rebuild=None, discard_on_tail=None, tail_source=None, jet_transform=None, transform):
    parent_state = _state_for(parent)
    head = curve_influence_head(parent) if influence_head is _UNSET else influence_head
    state = _EvaluationState(curve, kind='unary', domain=domain,
                             latest=latest_write_time(parent) if latest is None else latest,
                             origin=curve_origin(parent) if origin is None else origin,
                             influence_head=head, has_influence_head=True)
    state.parent = parent_state
    state.depth, state.size = parent_state.depth + 1, min(1025, parent_state.size + 1)
    state.snapshot_frame, state.rebuild, state.discard_on_tail, state.tail_source = snapshot_frame, rebuild, discard_on_tail, tail_source
    state.validate = validate
    state.identity_at = identity_at or (lambda x: False)
    state.always_identity = always_identity
    state.start, state.end = interval
    state.dependencies = dependencies
    state.transform = transform
    state.jet_transform = jet_transform
    _make_jumps(state)
    object.__setattr__(curve, '_evaluation_state', state)
    return curve


def register_multi_curve(curve, parents, *, domain, compute, jet_compute=None, latest=None, validate=None, snapshot_frame=None, rebuild=None, tail_source=None):
    unique = {}
    states = []
    for parent in parents:
        state = unique.get(id(parent))
        if state is None:
            state = _state_for(parent)
            unique[id(parent)] = state
        states.append(state)
    state = _EvaluationState(curve, kind='multi', domain=domain,
                             latest=max((latest_write_time(parent) for parent in parents), default=1) if latest is None else latest,
                             influence_head=None, has_influence_head=True)
    state.parents = tuple(states)
    state.depth, state.size = 1 + max(s.depth for s in states), min(1025, 1 + sum(s.size for s in states))
    state.snapshot_frame, state.rebuild, state.tail_source = snapshot_frame, rebuild, tail_source
    state.compute = compute
    state.jet_compute = jet_compute
    state.validate = validate
    object.__setattr__(curve, '_evaluation_state', state)
    return curve


def _canonical(state, x, mode='sample'):
    if isinstance(x, bool) or not isinstance(x, (int, float)) or not math.isfinite(x):
        return state
    while state.kind == 'unary':
        if not _inside(state.domain, x):
            break
        next_state = None
        for block in reversed(state.jumps[1:]):
            if block['domain_lo'] <= x <= block['domain_hi'] and (
                    block['always'] or ((x < block['min_start'] or x > block['max_end']) if mode=='jet' else (x <= block['min_start'] or x >= block['max_end']))):
                next_state = block['next']
                break
        if next_state is not None:
            state = next_state
            continue
        if state.always_identity or ((x<state.start or x>state.end) if mode=='jet' else state.identity_at(x)):
            state = state.parent
            continue
        break
    return state


def _evaluate_curve_mode(curve, x, coordinates, mode):
    explicit = coordinates is not _UNSET
    root = _canonical(_state_for(curve), x, mode)
    memo = {}
    stack = [(root, False, None)]
    while stack:
        state, done, children = stack[-1]
        key = id(state)
        if key in memo:
            stack.pop()
            continue
        if not done:
            if state.validate is not None:
                state.validate(x)
            if state.kind == 'opaque':
                if mode=='jet':
                    method=getattr(state.curve,'jet',None)
                    if method is None or getattr(type(state.curve),'jet',None) is CurveCapabilities.jet:
                        raise NotImplementedError('Analytic jets are unavailable for this source')
                    memo[key]=method(x,coordinates) if explicit else method(x)
                else:
                    memo[key] = state.curve.at(x, coordinates) if explicit else state.curve.sample(x)
                stack.pop()
                continue
            if state.kind == 'multi':
                children = state.parents
            elif state.dependencies is None:
                children = (state.parent,)
            else:
                children = tuple(_state_for(parent) for parent in state.dependencies(x, explicit))
            children = tuple(_canonical(child, x, mode) for child in children)
            stack[-1] = (state, True, children)
            for child in reversed(children):
                if id(child) not in memo:
                    stack.append((child, False, None))
            continue
        values = tuple(memo[id(child)] for child in children)
        callback=(state.jet_compute if mode=='jet' else state.compute) if state.kind=='multi' else (state.jet_transform if mode=='jet' else state.transform)
        if callback is None:raise NotImplementedError('Analytic jets are unavailable for this composition')
        memo[key]=callback(values if state.kind=='multi' else values[0],x,coordinates,explicit)
        stack.pop()
    return memo[id(root)]


def evaluate_curve(curve,x,coordinates=_UNSET):
    return _evaluate_curve_mode(curve,x,coordinates,'sample')


def evaluate_curve_jet(curve,x,coordinates=_UNSET):
    return _evaluate_curve_mode(curve,x,coordinates,'jet')


class CurveCapabilities:
    __slots__ = ()

    def jet(self,x,h=None):
        return evaluate_curve_jet(self,x) if h is None else evaluate_curve_jet(self,x,h)

    def slope(self,x,h=None):return self.jet(x,h)['slope']
    def curvature(self,x,h=None):return self.jet(x,h)['curvature']

    @property
    def normalized_domain(self):return self.domain

    def sample_normalized(self,x):return self._normalized_sample(self.sample(x))
    def at_normalized(self,x,h):return self._normalized_sample(self.at(x,h))
    @staticmethod
    def _normalized_sample(p):
        out=dict(p);v=p['value'];b=p['base']
        out.update(logBase=math.log(b),signedLogValue={'sign':0 if v==0 else (-1 if v<0 else 1),'logAbs':None if v==0 else math.log(abs(v))},floorRatio=None if p['floorValue'] is None else p['floorValue']/b,ceilingRatio=None if p['ceilingValue'] is None else p['ceilingValue']/b,boundaryWidthRatio=p['boundaryWidth']/b)
        return out

    def next_crossing(self, threshold, from_, horizon, options=None):
        from vixen_analysis import next_crossing
        controls = query_controls(self, from_, options)
        return next_crossing(self, threshold, from_, horizon, controls)

    def impulse(self, at=None, magnitude=None, relaxation=None, *, attack=None, **options):
        from vixen_operations import with_impulse
        if isinstance(at, dict):
            if magnitude is not None or relaxation is not None or attack is not None or options:
                raise TypeError('Use either an impulse record or individual controls')
            controls = at
        else:
            controls = dict(options, at=at, magnitude=magnitude, relaxation=relaxation)
            if attack is not None:
                controls['attack'] = attack
        return with_impulse(self, controls)

    def redistribute(self, window, shape):
        from vixen_operations import with_redistribution
        return with_redistribution(self, window, shape)

    def sample_range(self, x0, x1, count=129):
        if isinstance(count, bool) or not isinstance(count, int) or not 2 <= count <= 100001:
            raise ValueError('Sample count must be an integer in [2,100001]')
        domain = curve_domain(self)
        if any(isinstance(x, bool) or not isinstance(x, (int, float)) or not math.isfinite(x) or not domain[0] <= x <= domain[1] for x in (x0, x1)):
            raise ValueError('Range endpoints must lie in the available domain')
        return tuple(self.sample(x1 if i == count - 1 else x0 + (x1 - x0) * i / (count - 1)) for i in range(count))


_CONTINUOUS_FIELDS = frozenset(['x', 'value', 'base', 'rawDeviation', 'deviation', 'multiplier', 'activity', 'positiveMultiplier', 'positiveValue', 'guideValue', 'guideDeviation', 'unconstrainedValue', 'unconstrainedDeviation', 'unconstrainedGuideValue', 'unconstrainedGuideDeviation', 'gate', 'stabilizationWeight', 'floorValue', 'ceilingValue', 'boundaryWidth'])


def query_controls(curve, from_, options=None):
    if options is not None and not isinstance(options, dict):
        raise TypeError('Query options must be a dictionary')
    out = {} if options is None else dict(options)
    measure = out.get('measure', 'deviation')
    if 'continuous' not in out:
        out['continuous'] = 'evaluate' not in out and (measure in _CONTINUOUS_FIELDS or (measure in ('score', 'guideScore') and from_ > curve.config['openingEndpoint']))
    return out


GRAPH_ALGORITHM = 'vixen-graph/1'


def _rebuild_curve_states(curve, leaf, combine, from_=_UNSET):
    root = _state_for(curve)
    memo, stack = {}, [[root, False, None, False]]
    while stack:
        state, done, parents, alias = stack[-1]
        if state in memo:
            stack.pop()
            continue
        if not done:
            source = state.tail_source(from_) if from_ is not _UNSET and state.tail_source is not None else None
            if source is None and state.kind == 'opaque':
                memo[state] = leaf(state.curve)
                stack.pop()
                continue
            parents = (_state_for(source),) if source is not None else state.parents if state.kind == 'multi' else (state.parent,)
            stack[-1] = [state, True, parents, source is not None]
            for parent in reversed(parents):
                if parent not in memo:
                    stack.append([parent, False, None, False])
            continue
        values = tuple(memo[parent] for parent in parents)
        memo[state] = values[0] if alias else combine(state, values)
        stack.pop()
    return memo[root]


def snapshot_curve(curve, from_=_UNSET):
    if from_ is not _UNSET and (isinstance(from_, bool) or not isinstance(from_, (int, float)) or not math.isfinite(from_) or not curve.domain[0] <= from_ <= curve.domain[1]):
        raise ValueError('Checkpoint outside curve domain')
    state = _state_for(curve)
    flat, nodes = state.depth > 32 or state.size > 64, []
    def append(frame):
        if not flat:
            return frame
        index = len(nodes)
        nodes.append(frame)
        return index
    def combine(state, parents):
        if from_ is not _UNSET and state.discard_on_tail is not None and state.discard_on_tail(from_):
            return parents[0]
        if state.snapshot_frame is None:
            raise TypeError('Composition does not supply a replay frame')
        return append(state.snapshot_frame(parents))
    result = _rebuild_curve_states(curve, lambda c: append(c.snapshot() if from_ is _UNSET else c.tail(from_)), combine, from_)
    return _freeze_snapshot({'algorithm': GRAPH_ALGORITHM, 'nodes': nodes, 'root': result} if flat else result)


def fork_curve(curve, options):
    domain=curve_domain(curve);at=options.get('at')
    if isinstance(at,bool) or not isinstance(at,(int,float)) or not math.isfinite(at) or not max(domain[0],latest_write_time(curve))<=at<=domain[1]:
        raise ValueError('Fork exceeds the available chronology or domain')
    width=options.get('handoffWidth')
    if width is None:width=min(16,domain[1]-at)
    if isinstance(width,bool) or not isinstance(width,(int,float)) or not math.isfinite(width) or not 0<width<=50000 or at+width<=at or at+width>domain[1]:
        raise ValueError('Fork exceeds the available chronology or domain')
    options=dict(options,handoffWidth=width)
    def combine(state, parents):
        if state.rebuild is None:
            raise ValueError('Forking this composition has ambiguous branch semantics')
        return state.rebuild(parents)
    return _rebuild_curve_states(curve, lambda c: c.fork(**options), combine)


def restore_curve_graph(snapshot, restore_frame):
    if not isinstance(snapshot, Mapping) or snapshot.get('algorithm') != GRAPH_ALGORITHM or set(snapshot) != {'algorithm', 'nodes', 'root'}:
        raise TypeError('Invalid composition graph')
    nodes, root = snapshot['nodes'], snapshot['root']
    if not isinstance(nodes, (list, tuple)) or not 1 <= len(nodes) <= 1000000 or isinstance(root, bool) or not isinstance(root, (int,float)) or root != len(nodes)-1:
        raise ValueError('Invalid composition graph')
    values = []
    for frame in nodes:
        if not isinstance(frame, Mapping) or frame.get('algorithm') == GRAPH_ALGORITHM:
            raise TypeError('Invalid graph frame')
        def read(index):
            if isinstance(index, bool) or not isinstance(index, (int,float)) or not 0 <= index < len(values) or int(index) != index:
                raise ValueError('Graph references must name an earlier node')
            return values[int(index)]
        values.append(restore_frame(frame, read))
    return values[int(root)]


class _FrozenSnapshot(dict):
    @staticmethod
    def _immutable(*args,**kwargs):
        raise TypeError('Snapshots are read-only')
    __setitem__=__delitem__=clear=pop=popitem=setdefault=update=__ior__=_immutable


def _freeze_snapshot(value):
    if isinstance(value,Mapping):
        return _FrozenSnapshot({k:_freeze_snapshot(v) for k,v in value.items()})
    if isinstance(value,(list,tuple)):
        return tuple(_freeze_snapshot(v) for v in value)
    return value
