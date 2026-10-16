import {decorateCurve,evaluateCurve,registerUnaryCurve,registerMultiCurve,latestWriteTime,curveOrigin,curveLineage,curveInfluenceHead,materializeInfluences,curveDomain,snapshotCurve,forkCurve,evaluateCurveJet} from './vixen-api.mjs';
import {exactMean,sumProducts} from './vixen-float.mjs';
import {operatorJet,coupledJets} from './vixen-operation-calculus.mjs';
import {withInfluence} from './vixen-influence.mjs';

export const OPERATION_ALGORITHM = 'vixen-operation-c2/1';
const MAX_COEFFICIENTS = 64;
const MAX_CURVES = 32;
const GROUPS = new WeakSet();

function finite(value, name) {
  if (typeof value !== 'number' || !Number.isFinite(value)) throw new RangeError(`${name} must be finite`);
  return value;
}
function object(value, allowed, required, name) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new TypeError(`${name} must be an object`);
  const keys = Reflect.ownKeys(value);
  if (keys.some(k => typeof k !== 'string' || !allowed.includes(k)) || required.some(k => !Object.hasOwn(value, k))) throw new TypeError(`Invalid ${name} fields`);
}
function domainOf(curve) {
  if (!curve || typeof curve.sample !== 'function' || typeof curve.at !== 'function' || typeof curve.snapshot !== 'function' || typeof curve.tail !== 'function' || typeof curve.fork !== 'function' || !Array.isArray(curve.domain) || curve.domain.length !== 2) throw new TypeError('A Vixen curve is required');
  const [lo, hi] = curve.domain;
  finite(lo, 'Domain lower bound'); finite(hi, 'Domain upper bound');
  if (hi <= lo) throw new RangeError('Curve domain must have positive width');
  return [lo, hi];
}
function support(at, attack, relaxation) {
  const riseEnd = at + attack, end = riseEnd + relaxation;
  if (!Number.isFinite(riseEnd) || !Number.isFinite(end) || riseEnd <= at || end <= riseEnd) throw new RangeError('Operation support must have positive representable widths');
  return [riseEnd, end];
}
function smooth5(t) { return t <= 0 ? 0 : t >= 1 ? 1 : t * t * t * (10 + t * (-15 + 6 * t)); }
function smooth5Jet(t) {
  if (t <= 0 || t >= 1) return [smooth5(t), 0, 0];
  return [smooth5(t), 30 * t * t * (1 - t) * (1 - t), 60 * t * (1 - t) * (1 - 2 * t)];
}
function horner(coefficients, t) {
  let value = 0;
  for (let i = coefficients.length - 1; i >= 0; i--) value = value * t + coefficients[i];
  return value;
}
function derivativeCoefficients(coefficients, order) {
  let c = coefficients.slice();
  for (let n = 0; n < order; n++) c = c.slice(1).map((v, i) => v * (i + 1));
  return c.length ? c : [0];
}
const POTENTIAL_FACTOR = Object.freeze([0, 0, 0, 0, 1, -4, 6, -4, 1]);
function polynomialProduct(a, b) {
  const out = Array(a.length + b.length - 1).fill(0);
  for (let i = 0; i < a.length; i++) for (let j = 0; j < b.length; j++) out[i + j] += a[i] * b[j];
  if (out.some(v => !Number.isFinite(v))) throw new RangeError('Polynomial coefficients exceed the finite numeric range');
  return out;
}
function polyJet(coefficients, t) {
  return [horner(coefficients, t), horner(derivativeCoefficients(coefficients, 1), t), horner(derivativeCoefficients(coefficients, 2), t), horner(derivativeCoefficients(coefficients, 3), t)];
}
function redistributionJet(recipe, x) {
  const {from, until, coefficients, magnitude} = recipe;
  if (x <= from || x >= until) return [0, 0, 0];
  const width = until - from, t = (x - from) / width;
  const f = polyJet(POTENTIAL_FACTOR, t), p = polyJet(coefficients, t);
  const value = magnitude * (f[1] * p[0] + f[0] * p[1]);
  const first = magnitude * (f[2] * p[0] + 2 * f[1] * p[1] + f[0] * p[2]) / width;
  const second = magnitude * (f[3] * p[0] + 3 * f[2] * p[1] + 3 * f[1] * p[2] + f[0] * p[3]) / (width * width);
  if (![value, first, second].every(Number.isFinite)) throw new RangeError('Perturbation jet exceeds the finite numeric range');
  return [value, first, second];
}
function jetFor(recipe, x) {
  if(recipe.magnitude===0)return[0,0,0];
  finite(x, 'Jet coordinate');
  if (recipe.type === 'impulse') {
    const [riseEnd, end] = support(recipe.at, recipe.attack, recipe.relaxation);
    if (x <= recipe.at || x >= end) return [0, 0, 0];
    if (x <= riseEnd) {
      const t = (x - recipe.at) / recipe.attack, [v, d, dd] = smooth5Jet(t);
      return [recipe.magnitude * v, recipe.magnitude * d / recipe.attack, recipe.magnitude * dd / recipe.attack / recipe.attack];
    }
    const t = (x - riseEnd) / recipe.relaxation, [v, d, dd] = smooth5Jet(t);
    return [recipe.magnitude * (1 - v), -recipe.magnitude * d / recipe.relaxation, -recipe.magnitude * dd / recipe.relaxation / recipe.relaxation];
  }
  if (recipe.type === 'redistribution') return redistributionJet(recipe, x);
  throw new TypeError('Unknown perturbation recipe');
}
export function perturbationJet(options, x) {
  finite(x,'sample coordinate');
  if (!options || typeof options !== 'object' || Array.isArray(options)) throw new TypeError('Perturbation options are required');
  if (options.type === 'impulse') {
    object(options, ['type', 'at', 'magnitude', 'relaxation', 'attack'], ['type', 'at', 'magnitude', 'relaxation'], 'impulse recipe');
    const relaxation = finite(options.relaxation, 'relaxation'), attack = Object.hasOwn(options, 'attack') ? finite(options.attack, 'attack') : relaxation / 8;
    const at = finite(options.at, 'at'), magnitude = finite(options.magnitude, 'magnitude');
    if (relaxation <= 0 || attack <= 0) throw new RangeError('Attack and relaxation must be positive');
    support(at, attack, relaxation);
    const [value,first,second]=jetFor({type:'impulse',at,magnitude,attack,relaxation},x);
    if(![value,first,second].every(Number.isFinite))throw new RangeError('Perturbation derivatives exceed the finite numeric range');
    return Object.freeze({value,first,second});
  }
  if (options.type === 'redistribution') {
    const recipe = normalizeRedistributionRecipe(options);
    const [value, first, second] = jetFor(recipe, x);
    if(![value,first,second].every(Number.isFinite))throw new RangeError('Perturbation derivatives exceed the finite numeric range');
    return Object.freeze({value, first, second});
  }
  throw new TypeError('Perturbation type must be impulse or redistribution');
}
function normalizeImpulse(options, domain, carried = false) {
  object(options, ['at', 'magnitude', 'relaxation', 'attack'], ['at', 'magnitude', 'relaxation'], 'impulse options');
  const at = finite(options.at, 'at'), magnitude = finite(options.magnitude, 'magnitude'), relaxation = finite(options.relaxation, 'relaxation');
  const attack = Object.hasOwn(options, 'attack') ? finite(options.attack, 'attack') : relaxation / 8;
  if (relaxation <= 0 || attack <= 0) throw new RangeError('Attack and relaxation must be positive');
  const [riseEnd, end] = support(at, attack, relaxation);
  if ((!carried && at < domain[0]) || (carried && at < 1) || riseEnd > domain[1] || end > domain[1] || end <= domain[0]) throw new RangeError('Impulse support must lie inside the available domain');
  return Object.freeze({type: 'impulse', at, magnitude, relaxation, attack});
}
const CERTIFICATE = Object.freeze({kind: 'exact-real-endpoint-integral', potential: 't^4*(1-t)^4*P(t)', perturbation: 'd/dt potential', integral: 'Q(1)-Q(0)=0'});
function normalizeRedistributionRecipe(input) {
  object(input, ['type', 'from', 'until', 'coefficients', 'magnitude', 'measure', 'certificate'], ['type', 'from', 'until', 'coefficients', 'magnitude', 'measure'], 'redistribution recipe');
  if (input.type !== 'redistribution') throw new TypeError('Invalid redistribution recipe');
  const from = finite(input.from, 'window start'), until = finite(input.until, 'window end'), magnitude = finite(input.magnitude, 'magnitude');
  if (until <= from || from + (until - from) <= from) throw new RangeError('Redistribution window must have positive representable width');
  if (!Array.isArray(input.coefficients) || input.coefficients.length < 1 || input.coefficients.length > MAX_COEFFICIENTS) throw new RangeError('Shape must contain 1 to 64 coefficients');
  const coefficients = input.coefficients.map((v, i) => finite(v, `shape[${i}]`));
  const measure = input.measure;
  if (!['actualDeviation', 'unconstrainedDeviation'].includes(measure)) throw new TypeError('Invalid redistribution measure');
  if (input.certificate !== undefined) {
    const keys=Object.keys(CERTIFICATE); object(input.certificate,keys,keys,'algebra certificate descriptor');
    if(keys.some(k=>input.certificate[k]!==CERTIFICATE[k]))throw new TypeError('Invalid algebra certificate descriptor');
  }
  const q = polynomialProduct(POTENTIAL_FACTOR, coefficients), derivative = derivativeCoefficients(q, 1);
  const radius = Math.abs(magnitude) * derivative.reduce((sum, v) => sum + Math.abs(v), 0);
  if (!Number.isFinite(radius)) throw new RangeError('Redistribution bound exceeds the finite numeric range');
  return Object.freeze({type: 'redistribution', from, until, coefficients: Object.freeze(coefficients), magnitude, measure, certificate: CERTIFICATE});
}
function normalizeRedistribution(curve, window, shape, carried = false) {
  const domain = domainOf(curve);
  if (!Array.isArray(window) || window.length !== 2) throw new TypeError('window must be [from, until]');
  const from = finite(window[0], 'window start'), until = finite(window[1], 'window end');
  if (until <= from || from + (until - from) <= from) throw new RangeError('Redistribution window must have positive representable width');
  if ((!carried && (from < domain[0] || until > domain[1])) || (carried && (from < 1 || until <= domain[0] || until > domain[1]))) throw new RangeError('Redistribution window must lie inside the available domain');
  let coefficients, magnitude = 1, measure = 'actualDeviation';
  if (Array.isArray(shape)) coefficients = shape;
  else {
    object(shape, ['coefficients', 'magnitude', 'measure'], ['coefficients'], 'shape options');
    coefficients = shape.coefficients;
    if (Object.hasOwn(shape, 'magnitude')) magnitude = finite(shape.magnitude, 'magnitude');
    if (Object.hasOwn(shape, 'measure')) measure = shape.measure;
  }
  return normalizeRedistributionRecipe({type: 'redistribution', from, until, coefficients, magnitude, measure});
}
function policy(value, p) {
  const lo = p.floorValue, hi = p.ceilingValue, e = p.boundaryWidth;
  if (lo !== null && hi !== null && hi <= lo) return lo;
  if (lo !== null) {
    if (value <= lo) value = lo;
    else if (e > 0 && value < lo + e) { const t = (value - lo) / e; value = lo + e * (t <= 0 ? 0 : t >= 1 ? t : t * t * t * (6 + t * (-8 + 3 * t))); }
  }
  if (hi !== null) {
    if (value >= hi) value = hi;
    else if (e > 0 && value > hi - e) { const t = (hi - value) / e; value = hi - e * (t <= 0 ? 0 : t >= 1 ? t : t * t * t * (6 + t * (-8 + 3 * t))); }
  }
  return value;
}
function finiteSample(p, deviation) {
  const base = finite(p.base, 'sample base');
  if (base <= 0) throw new RangeError('Sample base must be positive');
  const unconstrainedValue = base + base * deviation;
  if (!Number.isFinite(unconstrainedValue)) throw new RangeError('Transformed value exceeds the finite numeric range');
  const constrained = p.floorValue !== null || p.ceilingValue !== null;
  const value = constrained ? policy(unconstrainedValue, p) : unconstrainedValue;
  const actual = constrained ? (value - base) / base : deviation;
  const positiveMultiplier = Math.exp(.55 * Math.tanh(actual));
  if (![value, actual, positiveMultiplier].every(Number.isFinite)) throw new RangeError('Transformed sample exceeds the finite numeric range');
  return Object.freeze({...p, value, unconstrainedValue, unconstrainedDeviation: deviation, deviation: actual, multiplier: 1 + actual, activity: .5 + .5 * Math.tanh(actual), positiveMultiplier, positiveValue: base * positiveMultiplier, constraintApplied: value !== unconstrainedValue});
}
function updateBounds(curve, radius) {
  if (!curve.bounds || !Array.isArray(curve.bounds.deviation)) return curve.bounds;
  const [lo, hi] = curve.bounds.deviation, deviation = Object.freeze([lo - radius, hi + radius]);
  return Object.freeze({...curve.bounds, deviation, multiplier: Object.freeze([1 + deviation[0], 1 + deviation[1]])});
}
function range(curve, from, until, count, sample) {
  const [lo, hi] = domainOf(curve);
  finite(from, 'range start'); finite(until, 'range end');
  if (!Number.isInteger(count) || count < 2 || count > 100001 || from < lo || until > hi || until < from) throw new RangeError('Invalid sample range');
  return Object.freeze(Array.from({length: count}, (_, i) => sample(from + (until - from) * i / (count - 1))));
}
function normalizedSample(p){return Object.freeze({...p,logBase:Math.log(p.base),signedLogValue:Object.freeze({sign:Math.sign(p.value)||0,logAbs:p.value===0?null:Math.log(Math.abs(p.value))}),floorRatio:p.floorValue===null?null:p.floorValue/p.base,ceilingRatio:p.ceilingValue===null?null:p.ceilingValue/p.base,boundaryWidthRatio:p.boundaryWidth/p.base});}
function makeOperationCurve(curve, recipe, restore) {
  const radius = recipe.type === 'impulse' ? Math.abs(recipe.magnitude) : Math.abs(recipe.magnitude) * derivativeCoefficients(polynomialProduct(POTENTIAL_FACTOR, recipe.coefficients), 1).reduce((s, v) => s + Math.abs(v), 0);
  const apply = (p, x) => {
    const delta = jetFor(recipe, x)[0];
    if (delta === 0) return p;
    const current = finite(p.unconstrainedDeviation, 'unconstrainedDeviation'), next = current + delta;
    if (!Number.isFinite(next)) throw new RangeError('Transformed deviation exceeds the finite numeric range');
    return next === current ? p : finiteSample(p, next);
  };
  let api;
  const descriptors = Object.getOwnPropertyDescriptors(curve);
  const overrides = {
    algorithm: {value: OPERATION_ALGORITHM, enumerable: true},
    bounds: {value: updateBounds(curve, radius), enumerable: true},
    sample: {value: x => evaluateCurve(api, x), enumerable: true},
    at: {value: (x, h) => evaluateCurve(api, x, h, true), enumerable: true},
    sampleRange: {value: (from, until, count = 129) => range(curve, from, until, count, x => evaluateCurve(api, x)), enumerable: true},
    snapshot: {value: () => snapshotCurve(api), enumerable: true},
    tail: {value: from => snapshotCurve(api, from), enumerable: true},
    fork: {value: options => forkCurve(api, options), enumerable: true},
    impulse: {value: options => withImpulse(api, options, restore), enumerable: true},
    redistribute: {value: (window, shape) => withRedistribution(api, window, shape, restore), enumerable: true},
    influence: {value: (target, options) => withInfluence(api, target, options), enumerable: true},
    slope: {value: (x,h)=>evaluateCurveJet(api,x,h,h!==undefined).slope, enumerable: true}, curvature: {value: (x,h)=>evaluateCurveJet(api,x,h,h!==undefined).curvature, enumerable: true}, jet: {value: (x,h)=>evaluateCurveJet(api,x,h,h!==undefined), enumerable: true},
    derivative: {value: (x,h)=>evaluateCurveJet(api,x,h,h!==undefined).slope, enumerable: true}, secondDerivative: {value: (x,h)=>evaluateCurveJet(api,x,h,h!==undefined).curvature, enumerable: true},
    normalizedDomain: {value: Object.freeze(Array.from(curveDomain(curve))), enumerable: true},
    normalized: {value: x=>normalizedSample(evaluateCurve(api,x)), enumerable: true}, normalised: {value: x=>normalizedSample(evaluateCurve(api,x)), enumerable: true},
    sampleNormalized: {value: x=>normalizedSample(evaluateCurve(api,x)), enumerable: true}, atNormalized: {value: (x,h)=>normalizedSample(evaluateCurve(api,x,h,true)), enumerable: true}, normalizedAt: {value: (x,h)=>normalizedSample(evaluateCurve(api,x,h,true)), enumerable: true}
  };
  api = Object.freeze(Object.defineProperties({}, {...descriptors, ...overrides}));
  const [start, end] = recipe.type === 'impulse' ? [recipe.at, support(recipe.at, recipe.attack, recipe.relaxation)[1]] : [recipe.from, recipe.until];
  registerUnaryCurve(api, curve, {
    domain: curveDomain(curve), latest: Math.max(latestWriteTime(curve),start), origin: curveOrigin(curve),
    influenceHead: curveInfluenceHead(curve), interval: [start, end], alwaysIdentity: recipe.magnitude === 0,
    identityAt: x => recipe.magnitude === 0 || x <= start || x >= end,
    transform: (parent, x) => apply(parent, x),
    jetTransform: (parent,x) => operatorJet(parent,jetFor(recipe,x),apply(parent.sample,x)),
    snapshotFrame: parents => Object.freeze({algorithm: OPERATION_ALGORITHM, operation: recipe.type, curve: parents[0], recipe}),
    discardOnTail: from => end <= from,
    rebuild: parents => makeOperationCurve(parents[0], recipe, restore)
  });
  return decorateCurve(api);
}
function validateOperationDomain(curve, recipe, carried) {
  const [lo, hi] = domainOf(curve);
  if (recipe.type === 'impulse') {
    const [riseEnd, end] = support(recipe.at, recipe.attack, recipe.relaxation);
    if ((!carried && recipe.at < lo) || (carried && recipe.at < 1) || riseEnd > hi || end > hi || end <= lo) throw new RangeError('Impulse support must lie inside the available domain');
  } else if ((!carried && (recipe.from < lo || recipe.until > hi)) || (carried && (recipe.from < 1 || recipe.until <= lo || recipe.until > hi))) throw new RangeError('Redistribution window must lie inside the available domain');
  if (recipe.type === 'redistribution' && recipe.measure === 'actualDeviation' && recipe.magnitude !== 0 && (curve.config.floor !== 'none' || curve.config.ceilingMargin !== null)) throw new RangeError('Actual-deviation redistribution requires an unconstrained source; choose measure: unconstrainedDeviation explicitly');
}
export function withImpulse(curve, options, restore) {
  const recipe = normalizeImpulse(options, domainOf(curve));
  return makeOperationCurve(curve, recipe, restore);
}
export function withRedistribution(curve, window, shape, restore) {
  const recipe = normalizeRedistribution(curve, window, shape);
  validateOperationDomain(curve, recipe, false);
  return makeOperationCurve(curve, recipe, restore);
}
function validCoupling(curves, strength, window, carried = false) {
  if (!Array.isArray(curves) || curves.length < 2 || curves.length > MAX_CURVES) throw new RangeError('Coupling requires 2 to 32 curves');
  if (curves.some(c => !c || typeof c.sample !== 'function' || typeof c.at !== 'function' || typeof c.snapshot !== 'function' || typeof c.tail !== 'function')) throw new TypeError('Coupling inputs must be Vixen curves');
  const domains = curves.map(domainOf), [lo, hi] = domains[0];
  if (domains.some(d => d[0] !== lo || d[1] !== hi)) throw new RangeError('Coupled curves must have identical finite domains');
  finite(strength, 'strength');
  if (strength < 0 || strength > 1) throw new RangeError('strength must lie in [0,1]');
  if (!Array.isArray(window) || window.length !== 2) throw new TypeError('window must be [from, until]');
  const from = finite(window[0], 'window start'), until = finite(window[1], 'window end');
  if (until <= from || from + (until - from) <= from || (!carried && (from < lo || until > hi)) || (carried && (from < 1 || until > hi))) throw new RangeError('Coupling window must have positive width inside the available domain');
  if (strength !== 0 && curves.some(c => c.config.floor !== 'none' || c.config.ceilingMargin !== null)) throw new RangeError('Nontrivial coupling requires sources without nonlinear floor or ceiling policies');
  return {lo, hi, from, until};
}
function gate(x, from, until) {
  if (x <= from || x >= until) return 0;
  const mid = from + (until - from) / 2;
  return x <= mid ? smooth5((x - from) / (mid - from)) : smooth5((until - x) / (until - mid));
}
function deriveUnconstrained(p, deviation) {
  const base = finite(p.base, 'sample base'), value = base + base * deviation;
  if (base <= 0 || !Number.isFinite(value)) throw new RangeError('Coupled value exceeds the finite numeric range');
  const positiveMultiplier = Math.exp(.55 * Math.tanh(deviation));
  return Object.freeze({...p, value, unconstrainedValue: value, unconstrainedDeviation: deviation, deviation, multiplier: 1 + deviation, activity: .5 + .5 * Math.tanh(deviation), positiveMultiplier, positiveValue: base * positiveMultiplier, constraintApplied: false});
}
function makeGroup(curves, strength, window, carried = false) {
  const {lo, hi, from, until} = validCoupling(curves, strength, window, carried);
  const frozenCurves = Object.freeze(curves.slice()), frozenWindow = Object.freeze([from, until]);
  function compute(parents, x) {
    const amount = strength * gate(x, from, until);
    if (amount === 0) return Object.freeze(parents);
    const deviations = parents.map(p => finite(p.deviation, 'deviation'));
    const mean = exactMean(deviations);
    const outputs = deviations.map((d,i)=>{
      if(d===mean)return parents[i];
      let mixed=(1-amount)*d+amount*mean;
      if(!Number.isFinite(mixed))mixed=sumProducts([[1-amount,d],[amount,mean]]);
      return deriveUnconstrained(parents[i],mixed);
    });
    return Object.freeze(outputs);
  }
  const group = {
    algorithm: OPERATION_ALGORITHM, strength, window: frozenWindow, domain: Object.freeze([lo, hi]),
    sample: x => evaluateCurve(group,x),
    jet: (x,h) => evaluateCurveJet(group,x,h,h!==undefined), at: (x, h) => evaluateCurve(group,x,h,true),
    sampleRange: (a, b, count = 129) => range(group, a, b, count, x => evaluateCurve(group,x)),
    snapshot: () => snapshotCurve(group),
    tail: fromX => snapshotCurve(group, fromX),
    fork: () => { throw new TypeError('Forking a coupling group has ambiguous branch semantics'); }
  };
  GROUPS.add(group);
  registerMultiCurve(group, frozenCurves, {
    domain:group.domain,
    validate:x=>{finite(x,'sample coordinate');if(x<lo||x>hi)throw new RangeError('x outside coupled domain');},
    compute:(parents,x)=>compute(parents,x),
    jetCompute:(parents,x)=>coupledJets(parents,compute(parents.map(p=>p.sample),x),x,strength,from,until),
    snapshotFrame:parents=>Object.freeze({algorithm:OPERATION_ALGORITHM,operation:'coupling',curves:Object.freeze(parents),strength,window:frozenWindow})
  });
  const bounds = (() => {
    const ranges = frozenCurves.map(c => c.bounds?.deviation).filter(Array.isArray);
    if (ranges.length !== frozenCurves.length) return undefined;
    const lower = Math.min(...ranges.map(r => r[0])), upper = Math.max(...ranges.map(r => r[1]));
    return Object.freeze({deviation: Object.freeze([lower, upper]), multiplier: Object.freeze([1 + lower, 1 + upper])});
  })();
  const views = frozenCurves.map((source, index) => {
    const view = Object.freeze({
      algorithm: OPERATION_ALGORITHM, config: source.config, domain: group.domain, bounds: Object.freeze({...source.bounds,...bounds,unconstrainedSpatialSlope:Math.max(...frozenCurves.map(c=>c.bounds.unconstrainedSpatialSlope))}),
      lineage: source.lineage, influences: source.influences, dips: source.dips,
      sample: x => evaluateCurve(view,x), at: (x, h) => evaluateCurve(view,x,h,true),
      sampleRange: (a, b, count = 129) => range(view, a, b, count, x => evaluateCurve(view,x)),
      location: x => source.location(x), inspect: x => source.inspect(x), ridges: () => source.ridges(),
      snapshot: () => snapshotCurve(view),
      tail: fromX => snapshotCurve(view, fromX),
      fork: () => { throw new TypeError('Forking a coupled projection has ambiguous branch semantics'); },
      impulse: options => withImpulse(view, options), redistribute: (w, s) => withRedistribution(view, w, s),
      influence: (target, options) => withInfluence(view, target, options),
      slope:(x,h)=>evaluateCurveJet(view,x,h,h!==undefined).slope,curvature:(x,h)=>evaluateCurveJet(view,x,h,h!==undefined).curvature,jet:(x,h)=>evaluateCurveJet(view,x,h,h!==undefined),
      derivative:(x,h)=>evaluateCurveJet(view,x,h,h!==undefined).slope,secondDerivative:(x,h)=>evaluateCurveJet(view,x,h,h!==undefined).curvature,
      normalizedDomain:group.domain, normalized:x=>normalizedSample(evaluateCurve(view,x)),normalised:x=>normalizedSample(evaluateCurve(view,x)),sampleNormalized:x=>normalizedSample(evaluateCurve(view,x)),atNormalized:(x,h)=>normalizedSample(evaluateCurve(view,x,h,true)),normalizedAt:(x,h)=>normalizedSample(evaluateCurve(view,x,h,true))
    });
    registerUnaryCurve(view,group,{
      domain:group.domain,latest:latestWriteTime(source),origin:curveOrigin(source),influenceHead:curveInfluenceHead(source),
      transform:parents=>parents[index],
      jetTransform:parents=>parents[index],
      snapshotFrame:parents=>Object.freeze({algorithm:OPERATION_ALGORITHM,operation:'couplingView',group:parents[0],index}),
      tailSource:fromX=>fromX>=until||strength===0?source:null
    });
    return decorateCurve(view);
  });
  Object.defineProperty(group, 'curves', {value: Object.freeze(views), enumerable: true});
  return Object.freeze(group);
}
export function couple(curves, strength, window) { return makeGroup(curves, strength, window); }
function validateSnapshotTree(root) {
  const active = new WeakSet(), stack = [{value: root, leave: false, depth: 0}];
  let nodes = 0;
  while (stack.length) {
    const item = stack.pop(), v = item.value;
    if (item.leave) { active.delete(v); continue; }
    if (typeof v === 'number') { if (!Number.isFinite(v)) throw new TypeError('Snapshot contains nonfinite numeric data'); continue; }
    if (v === null || typeof v === 'string' || typeof v === 'boolean') continue;
    if (typeof v !== 'object' || item.depth > 128 || ++nodes > 100000) throw new TypeError('Snapshot contains unsupported or excessive data');
    if (active.has(v)) throw new TypeError('Snapshot cycles are not allowed');
    active.add(v); stack.push({value: v, leave: true, depth: item.depth});
    if (Array.isArray(v)) {
      if (Reflect.ownKeys(v).some(k => k !== 'length' && (typeof k !== 'string' || !/^(0|[1-9][0-9]*)$/.test(k)))) throw new TypeError('Snapshot arrays must be plain');
      for (let i = v.length - 1; i >= 0; i--) stack.push({value: v[i], leave: false, depth: item.depth + 1});
    } else {
      const proto = Object.getPrototypeOf(v);
      if (proto !== Object.prototype && proto !== null) throw new TypeError('Snapshot objects must be plain');
      const keys = Reflect.ownKeys(v);
      if (keys.some(k => typeof k !== 'string')) throw new TypeError('Snapshot symbols are not allowed');
      for (const k of keys) stack.push({value: v[k], leave: false, depth: item.depth + 1});
    }
  }
}
export function restoreOperation(snapshot, restore) {
  validateSnapshotTree(snapshot);
  if (typeof restore !== 'function') throw new TypeError('A root restore callback is required');
  if (!snapshot || typeof snapshot !== 'object' || Array.isArray(snapshot) || snapshot.algorithm !== OPERATION_ALGORITHM) throw new TypeError('Invalid operation snapshot');
  if (snapshot.operation === 'impulse' || snapshot.operation === 'redistribution') {
    object(snapshot, ['algorithm', 'operation', 'curve', 'recipe'], ['algorithm', 'operation', 'curve', 'recipe'], 'operation snapshot');
    const curve = restore(snapshot.curve);
    if (snapshot.operation === 'impulse') {
      object(snapshot.recipe, ['type', 'at', 'magnitude', 'relaxation', 'attack'], ['type', 'at', 'magnitude', 'relaxation', 'attack'], 'impulse recipe');
      if (snapshot.recipe.type !== 'impulse') throw new TypeError('Operation and recipe disagree');
      const {at,magnitude,relaxation,attack}=snapshot.recipe;
      const recipe = normalizeImpulse({at,magnitude,relaxation,attack}, domainOf(curve), true);
      return makeOperationCurve(curve, recipe, restore);
    }
    const recipe = normalizeRedistributionRecipe(snapshot.recipe);
    validateOperationDomain(curve, recipe, true);
    return makeOperationCurve(curve, recipe, restore);
  }
  if (snapshot.operation === 'coupling') {
    object(snapshot, ['algorithm', 'operation', 'curves', 'strength', 'window'], ['algorithm', 'operation', 'curves', 'strength', 'window'], 'coupling snapshot');
    if (!Array.isArray(snapshot.curves) || snapshot.curves.length < 2 || snapshot.curves.length > MAX_CURVES) throw new RangeError('Invalid coupling source count');
    const curves = snapshot.curves.map(restore);
    return makeGroup(curves, finite(snapshot.strength, 'strength'), snapshot.window, true);
  }
  if (snapshot.operation === 'couplingView') {
    object(snapshot, ['algorithm', 'operation', 'group', 'index'], ['algorithm', 'operation', 'group', 'index'], 'coupling view snapshot');
    const group = restore(snapshot.group);
    if (!GROUPS.has(group) || !Number.isInteger(snapshot.index) || snapshot.index < 0 || snapshot.index >= group.curves.length) throw new TypeError('Invalid coupling view');
    return group.curves[snapshot.index];
  }
  throw new TypeError('Unknown operation snapshot kind');
}
