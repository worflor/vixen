let decorate = curve => curve;
const states = new WeakMap();
const EMPTY = Object.freeze([]);
const influenceArrays = new WeakMap();

export function registerCurveDecorator(factory) {
  if (typeof factory !== 'function') throw new TypeError('Curve decorator must be callable');
  decorate = factory;
}

export function decorateCurve(curve) {
  const decorated = decorate(curve);
  const state = states.get(curve);
  if (state && decorated && (typeof decorated === 'object' || typeof decorated === 'function')) states.set(decorated, state);
  return decorated;
}

function opaqueState(curve) {
  let domain = null;
  try {
    const value = curve?.domain;
    if (Array.isArray(value) && value.length === 2 && value.every(Number.isFinite)) domain = value;
  } catch {}
  return {
    kind: 'opaque', curve, domain, latest: null, origin: curve, depth: 0, size: 1,
    influenceHead: null, hasInfluenceHead: false, jumps: []
  };
}

function stateFor(curve) {
  let state = states.get(curve);
  if (!state) {
    state = opaqueState(curve);
    states.set(curve, state);
  }
  return state;
}

export function registerRootCurve(curve, {domain = curve.domain, latest = 1} = {}) {
  states.set(curve, {
    kind: 'opaque', curve, domain, latest, origin: curve, depth: 0, size: 1,
    influenceHead: null, hasInfluenceHead: true, jumps: []
  });
  return curve;
}

function makeJumpBlocks(state) {
  const parent = state.parent;
  state.jumps = [{
    next: parent, minStart: state.start, maxEnd: state.end,
    domainLo: state.domain?.[0] ?? -Infinity, domainHi: state.domain?.[1] ?? Infinity,
    always: state.alwaysIdentity
  }];
  for (let k = 1; ; k++) {
    const left = state.jumps[k - 1];
    const middle = left.next;
    const right = middle?.jumps?.[k - 1];
    if (!right) break;
    state.jumps.push({
      next: right.next,
      minStart: Math.min(left.minStart, right.minStart),
      maxEnd: Math.max(left.maxEnd, right.maxEnd),
      domainLo: Math.max(left.domainLo, right.domainLo),
      domainHi: Math.min(left.domainHi, right.domainHi),
      always: left.always && right.always
    });
  }
}

export function registerUnaryCurve(curve, parent, options = {}) {
  const parentState = stateFor(parent);
  const domain = options.domain ?? curve.domain ?? parentState.domain;
  const head = options.influenceHead === undefined ? curveInfluenceHead(parent) : options.influenceHead;
  const state = {
    kind: 'unary', curve, parent: parentState, domain,
    depth: parentState.depth + 1, size: Math.min(1025, parentState.size + 1),
    snapshotFrame: options.snapshotFrame, rebuild: options.rebuild, tailSource: options.tailSource,
    discardOnTail: options.discardOnTail,
    latest: options.latest ?? latestWriteTime(parent),
    origin: options.origin ?? curveOrigin(parent),
    influenceHead: head, hasInfluenceHead: true,
    start: options.interval?.[0] ?? -Infinity,
    end: options.interval?.[1] ?? Infinity,
    alwaysIdentity: options.alwaysIdentity === true,
    identityAt: options.identityAt ?? (() => false),
    validate: options.validate,
    dependencies: options.dependencies,
    transform: options.transform, jetTransform: options.jetTransform,
    jumps: []
  };
  makeJumpBlocks(state);
  states.set(curve, state);
  return curve;
}

export function registerMultiCurve(curve, parents, options = {}) {
  const unique = new Map();
  const parentStates = parents.map(parent => {
    let state = unique.get(parent);
    if (!state) { state = stateFor(parent); unique.set(parent, state); }
    return state;
  });
  states.set(curve, {
    kind: 'multi', curve, parents: parentStates,
    depth: 1 + Math.max(...parentStates.map(p => p.depth)),
    size: Math.min(1025, 1 + parentStates.reduce((n,p) => n+p.size, 0)),
    snapshotFrame: options.snapshotFrame, rebuild: options.rebuild, tailSource: options.tailSource,
    domain: options.domain ?? curve.domain,
    latest: options.latest ?? Math.max(1, ...parentStates.map(state => state.latest ?? 1)),
    origin: curve, influenceHead: null, hasInfluenceHead: true,
    validate: options.validate, compute: options.compute, jetCompute: options.jetCompute,
  });
  return curve;
}

function inside(domain, x) {
  return domain !== null && domain !== undefined && x >= domain[0] && x <= domain[1];
}

function canonicalState(initial, x, mode='sample') {
  let state = initial;
  if (typeof x !== 'number' || !Number.isFinite(x)) return state;
  while (state.kind === 'unary') {
    if (!inside(state.domain, x)) break;
    let next = null;
    for (let k = state.jumps.length - 1; k >= 1; k--) {
      const block = state.jumps[k];
      if (x >= block.domainLo && x <= block.domainHi &&
          (block.always || (mode==='jet' ? x < block.minStart || x > block.maxEnd : x <= block.minStart || x >= block.maxEnd))) {
        next = block.next;
        break;
      }
    }
    if(next){state=next;continue;}
    if(state.alwaysIdentity||(mode==='jet'?x<state.start||x>state.end:state.identityAt(x))){state=state.parent;continue;}
    break;
  }
  return state;
}

function evaluateCurveMode(curve, x, coordinates, explicit, mode) {
  const root = canonicalState(stateFor(curve), x, mode);
  const memo = new Map();
  const stack = [{state: root, done: false}];
  while (stack.length) {
    const frame = stack[stack.length - 1], state = frame.state;
    if (memo.has(state)) { stack.pop(); continue; }
    if (!frame.done) {
      if (state.validate) state.validate(x, explicit, coordinates);
      if (state.kind === 'opaque') {
        if(mode==='jet'){
          if(typeof state.curve.jet!=='function')throw new TypeError('Analytic jets are unavailable for this source');
          memo.set(state,explicit?state.curve.jet(x,coordinates):state.curve.jet(x));
        }else memo.set(state,explicit?state.curve.at(x,coordinates):state.curve.sample(x));
        stack.pop();
        continue;
      }
      const children = state.kind === 'multi'
        ? state.parents
        : (state.dependencies ? state.dependencies(x, explicit).map(stateFor) : [state.parent]);
      frame.children = children.map(child => canonicalState(child, x, mode));
      frame.done = true;
      for (let i = frame.children.length - 1; i >= 0; i--) {
        if (!memo.has(frame.children[i])) stack.push({state: frame.children[i], done: false});
      }
      continue;
    }
    const values = frame.children.map(child => memo.get(child));
    const compute=mode==='jet'?state.jetCompute:state.compute,transform=mode==='jet'?state.jetTransform:state.transform;
    if(typeof(state.kind==='multi'?compute:transform)!=='function')throw new TypeError('Analytic jets are unavailable for this composition');
    memo.set(state,state.kind==='multi'?compute(values,x,coordinates,explicit):transform(values[0],x,coordinates,explicit));
    stack.pop();
  }
  return memo.get(root);
}

export function curveOrigin(curve) { return stateFor(curve).origin; }
export function curveLineage(curve) { return curveOrigin(curve).lineage; }
export function curveConfig(curve) { return curveOrigin(curve).config; }
export function curveDomain(curve) { return stateFor(curve).domain ?? curve.domain; }

export function latestWriteTime(curve) {
  const state = stateFor(curve);
  if (state.latest !== null && state.latest !== undefined) return state.latest;
  let latest = 1;
  for (const event of curve.lineage ?? []) latest = Math.max(latest, event.at);
  for (const event of curve.influences ?? []) latest = Math.max(latest, event.at);
  state.latest = latest;
  return latest;
}

class InfluenceLink {
  constructor(entry, previous) {
    this.entry = entry;
    this.previous = previous;
    this.length = (previous?.length ?? 0) + 1;
    Object.freeze(this);
  }
}

export function curveInfluenceHead(curve) {
  const state = stateFor(curve);
  if (state.hasInfluenceHead) return state.influenceHead;
  let head = null;
  for (const entry of curve.influences ?? []) head = new InfluenceLink(entry, head);
  state.influenceHead = head;
  state.hasInfluenceHead = true;
  return head;
}

export function appendInfluence(curve, entry) {
  return new InfluenceLink(entry, curveInfluenceHead(curve));
}

export function materializeInfluences(head) {
  if (!head) return EMPTY;
  const cached = influenceArrays.get(head);
  if (cached) return cached;
  const out = Array(head.length);
  for (let node = head, i = out.length - 1; node; node = node.previous) out[i--] = node.entry;
  const result = Object.freeze(out);
  influenceArrays.set(head, result);
  return result;
}

const enclosureScope = Symbol.for('dev.woflo.vixen.enclosure-scope/1');
export function scopeCurveEnclosure(curve, fn) { Object.defineProperty(fn, enclosureScope, {value: curve}); return fn; }
export function ownsCurveEnclosure(curve, fn) { return typeof fn === 'function' && fn[enclosureScope] === curve; }

const continuousFields = new Set(['x', 'value', 'base', 'rawDeviation', 'deviation', 'multiplier', 'activity', 'positiveMultiplier', 'positiveValue', 'guideValue', 'guideDeviation', 'unconstrainedValue', 'unconstrainedDeviation', 'unconstrainedGuideValue', 'unconstrainedGuideDeviation', 'gate', 'stabilizationWeight', 'floorValue', 'ceilingValue', 'boundaryWidth']);
export function queryControls(curve, from, options = {}) {
  if (options === null || typeof options !== 'object' || Array.isArray(options)) throw new TypeError('Query options must be an object');
  const out = {...options}, measure = out.measure === undefined ? 'deviation' : out.measure;
  if (out.continuous === undefined) out.continuous = out.evaluate === undefined && (continuousFields.has(measure) || (['score', 'guideScore'].includes(measure) && from > curve.config.openingEndpoint));
  return out;
}

export const GRAPH_ALGORITHM = 'vixen-graph/1';
function rebuildCurveStates(curve, leaf, combine, from) {
  const root = stateFor(curve), memo = new Map(), stack = [{state:root,done:false}];
  while (stack.length) {
    const frame=stack[stack.length-1], s=frame.state;
    if (memo.has(s)) {stack.pop();continue;}
    if (!frame.done) {
      frame.done=true;
      const alias=from===undefined?null:s.tailSource?.(from);
      if (alias) frame.alias=stateFor(alias);
      if (!frame.alias && s.kind==='opaque') {memo.set(s,leaf(s.curve));stack.pop();continue;}
      frame.parents=frame.alias?[frame.alias]:s.kind==='multi'?s.parents:[s.parent];
      for(let i=frame.parents.length-1;i>=0;i--)if(!memo.has(frame.parents[i]))stack.push({state:frame.parents[i],done:false});
      continue;
    }
    const parents=frame.parents.map(p=>memo.get(p));
    memo.set(s,frame.alias?parents[0]:combine(s,parents));stack.pop();
  }
  return memo.get(root);
}
export function snapshotCurve(curve, from) {
  if(from!==undefined && (!Number.isFinite(from)||from<curve.domain[0]||from>curve.domain[1]))throw new RangeError('Checkpoint outside curve domain');
  const state=stateFor(curve), flat=state.depth>32||state.size>64, nodes=[];
  const append=frame=>{if(!flat)return frame;const index=nodes.length;nodes.push(frame);return index;};
  const root=rebuildCurveStates(curve,
    c=>append(from===undefined?c.snapshot():c.tail(from)),
    (s,parents)=>{
      if(from!==undefined&&s.discardOnTail?.(from))return parents[0];
      if(typeof s.snapshotFrame!=='function')throw new TypeError('Composition does not supply a replay frame');
      return append(s.snapshotFrame(parents));
    },from);
  return flat?Object.freeze({algorithm:GRAPH_ALGORITHM,nodes:Object.freeze(nodes),root}):root;
}
export function forkCurve(curve,options) {
  if(!options||typeof options!=='object'||Array.isArray(options)||Reflect.ownKeys(options).some(k=>!['at','operation','handoffWidth'].includes(k)))throw new TypeError('Invalid fork options');
  const domain=curveDomain(curve),at=options.at;
  const width=Object.hasOwn(options,'handoffWidth')?options.handoffWidth:Math.min(16,domain[1]-at);
  if(typeof at!=='number'||!Number.isFinite(at)||at<Math.max(domain[0],latestWriteTime(curve))||at>domain[1]||typeof width!=='number'||!Number.isFinite(width)||width<=0||width>50000||at+width<=at||at+width>domain[1])throw new RangeError('Fork exceeds the available chronology or domain');
  options={...options,handoffWidth:width};
  return rebuildCurveStates(curve,c=>c.fork(options),(s,parents)=>{
    if(typeof s.rebuild!=='function')throw new TypeError('Forking this composition has ambiguous branch semantics');
    return s.rebuild(parents);
  });
}
export function restoreCurveGraph(snapshot, restoreFrame) {
  if(!snapshot||snapshot.algorithm!==GRAPH_ALGORITHM||Reflect.ownKeys(snapshot).some(k=>!['algorithm','nodes','root'].includes(k))||
      !Array.isArray(snapshot.nodes)||snapshot.nodes.length===0||snapshot.nodes.length>1000000||snapshot.root!==snapshot.nodes.length-1)
    throw new TypeError('Invalid composition graph');
  const values=[];
  for(const frame of snapshot.nodes){
    if(!frame||typeof frame!=='object'||Array.isArray(frame)||frame.algorithm===GRAPH_ALGORITHM)throw new TypeError('Invalid graph frame');
    const read=index=>{
      if(!Number.isInteger(index)||index<0||index>=values.length)throw new TypeError('Graph references must name an earlier node');
      return values[index];
    };
    values.push(restoreFrame(frame,read));
  }
  return values[snapshot.root];
}

export function evaluateCurve(curve,x,coordinates,explicit=false){return evaluateCurveMode(curve,x,coordinates,explicit,'sample');}
export function evaluateCurveJet(curve,x,coordinates,explicit=false){return evaluateCurveMode(curve,x,coordinates,explicit,'jet');}
