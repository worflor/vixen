import {decorateCurve,evaluateCurve,registerUnaryCurve,latestWriteTime,curveOrigin,curveLineage,appendInfluence,materializeInfluences,snapshotCurve,forkCurve,evaluateCurveJet} from './vixen-api.mjs';
import {sumProducts} from './vixen-float.mjs';
import {influenceJet} from './vixen-influence-calculus.mjs';
import {compileGuide, captureGuide} from './vixen-guide.mjs';

export const INFLUENCE_ALGORITHM = 'vixen-influence-c2/1';
export const INFLUENCE_ALGORITHM_V2 = 'vixen-influence-c2/2';
export const INFLUENCE_ALGORITHM_V3 = 'vixen-influence-c2/3';
export const isInfluenceAlgorithm = value => value === INFLUENCE_ALGORITHM || value === INFLUENCE_ALGORITHM_V2 || value === INFLUENCE_ALGORITHM_V3;

const OPTION_KEYS = new Set(['at', 'amount', 'handoffWidth', 'count', 'until', 'releaseWidth', 'reference', 'mode']);
const SNAPSHOT_V1_KEYS = ['algorithm', 'curve', 'guide', 'at', 'amount', 'handoffWidth'];
const SNAPSHOT_V2_BASE_KEYS = ['algorithm', 'curve', 'guide', 'at', 'amount', 'handoffWidth', 'reference'];
const SNAPSHOT_V2_WINDOW_KEYS = [...SNAPSHOT_V2_BASE_KEYS, 'until', 'releaseWidth'];
const hasOwn = (value, key) => Object.hasOwn(value, key);

function finite(value, lo, hi, name = 'Influence number') {
  if (typeof value !== 'number' || !Number.isFinite(value) || value < lo || value > hi) {
    throw new RangeError(`${name} outside its finite domain`);
  }
  return value;
}

function record(value, keys, message) {
  if (!value || typeof value !== 'object' || Array.isArray(value) || Reflect.ownKeys(value).some(key => !keys.includes(key))) {
    throw new TypeError(message);
  }
}

function gate(t) {
  return t <= 0 ? 0 : t >= 1 ? 1 : t * t * t * (10 + t * (-15 + 6 * t));
}

function ramp(t) {
  return t <= 0 ? 0 : t >= 1 ? 1 : t * t * t * (6 + t * (-8 + 3 * t));
}

function policy(value, p) {
  const {floorValue: lo, ceilingValue: hi, boundaryWidth: width} = p;
  if (lo !== null && hi !== null && hi <= lo) return lo;
  if (lo !== null) {
    if (value <= lo) value = lo;
    else if (width > 0 && value < lo + width) value = lo + width * ramp((value - lo) / width);
  }
  if (hi !== null) {
    if (value >= hi) value = hi;
    else if (width > 0 && value > hi - width) value = hi - width * ramp((hi - value) / width);
  }
  return value;
}

function affected(p, target, amount, config, reference, mode) {
  if (amount === 0) return p;
  const extra = p.base * amount * config.boundarySoftness;
  const lo = p.floorValue;
  const hi = p.ceilingValue;
  let width = p.boundaryWidth;
  if (lo !== null && hi !== null) {
    const room = Math.max(0, (hi - lo) / 2 - width);
    if (extra > 0 && room > 0) {
      let increment=extra*room/(room+extra);
      if(!Number.isFinite(increment))increment=extra<=room?extra/(1+extra/room):room/(1+room/extra);
      width+=increment;
    }
  } else if (lo !== null || hi !== null) {
    width += extra;
  }
  p = {...p, boundaryWidth: width};

  let unconstrainedValue;
  if (mode === 'add') {
    const increment = reference === 'value' ? amount * target : p.base * (amount * target);
    unconstrainedValue = p.unconstrainedValue + increment;
  } else if (reference === 'value') {
    unconstrainedValue = amount === 1
      ? target
      : (1 - amount) * p.unconstrainedValue + amount * target;
  } else if (reference === 'baseline') {
    const targetValue = p.base * (1 + target);
    unconstrainedValue = amount === 1
      ? targetValue
      : (1 - amount) * p.unconstrainedValue + amount * targetValue;
  } else {
    const targetValue = p.unconstrainedGuideValue + p.base * target;
    unconstrainedValue = amount === 1
      ? targetValue
      : (1 - amount) * p.unconstrainedValue + amount * targetValue;
  }

  if(!Number.isFinite(unconstrainedValue)) {
    const prior=mode==='add'?[[p.unconstrainedValue]]:[[1-amount,p.unconstrainedValue]];
    const terms=reference==='value'?[[amount,target]]:mode==='add'?[[p.base,amount,target]]:
      reference==='baseline'?[[p.base,amount,1+target]]:[[amount,p.unconstrainedGuideValue],[p.base,amount,target]];
    unconstrainedValue=sumProducts([...prior,...terms]);
  }
  if(!Number.isFinite(unconstrainedValue)||!Number.isFinite(width))throw new RangeError('Influence sample exceeds the finite numeric range');
  const value = policy(unconstrainedValue, p);
  const deviation = (value - p.base) / p.base;
  const positiveMultiplier = Math.exp(.55 * Math.tanh(deviation));
  const guideValue = policy(p.unconstrainedGuideValue, p);
  const result = {
    ...p,
    guideValue,
    guideDeviation: (guideValue - p.base) / p.base,
    value,
    unconstrainedValue,
    unconstrainedDeviation: (unconstrainedValue - p.base) / p.base,
    deviation,
    multiplier: 1 + deviation,
    activity: .5 + .5 * Math.tanh(deviation),
    positiveMultiplier,
    positiveValue: p.base * positiveMultiplier,
    constraintApplied: value !== unconstrainedValue
  };
  if(Object.values(result).some(v=>typeof v==='number'&&!Number.isFinite(v)))throw new RangeError('Influence sample exceeds the finite numeric range');
  return Object.freeze(result);
}

function latest(curve) { return latestWriteTime(curve); }

function weightAt(x, recipe) {
  const {at, amount, handoffWidth, windowed, until, releaseWidth} = recipe;
  if (x <= at) return 0;
  if (!windowed) return amount * gate((x - at) / handoffWidth);
  const handoffEnd = at + handoffWidth;
  if (x < handoffEnd) return amount * gate((x - at) / handoffWidth);
  if (releaseWidth === 0) return x < until ? amount : 0;
  if (x <= until) return amount;
  const releaseEnd = until + releaseWidth;
  if (x < releaseEnd) return amount * (1 - gate((x - until) / releaseWidth));
  return 0;
}

function recipeOptions(recipe) {
  const options = {
    at: recipe.at,
    amount: recipe.amount,
    handoffWidth: recipe.handoffWidth
  };
  if (recipe.windowed) {
    options.until = recipe.until;
    options.releaseWidth = recipe.releaseWidth;
  }
  if (recipe.reference !== 'value') options.reference = recipe.reference;
  if (recipe.mode === 'add') options.mode = recipe.mode;
  return options;
}

function makeInfluence(curve, target, options = {}, carried = false) {
  record(options, [...OPTION_KEYS], 'Influence options: at, amount, handoffWidth, count, until, releaseWidth, reference, mode');
  const optionHas = key => hasOwn(options, key);
  if (optionHas('count') && options.count === undefined) throw new TypeError('count must be an integer when supplied');
  const domainLo = curve.domain[0];
  const domainHi = curve.domain[1];
  const at = optionHas('at')
    ? options.at
    : Math.max(curve.config.openingEndpoint, latest(curve), domainLo);
  finite(at, carried ? 1 : domainLo, domainHi, 'Influence activation');
  const amount = optionHas('amount') ? options.amount : .5;
  finite(amount, 0, 1, 'Influence amount');
  if (!carried && at < latest(curve)) throw new RangeError('Influence cannot precede existing timeline events');

  if (typeof target === 'function') {
    const count = optionHas('count') ? options.count : 129;
    target = captureGuide(target, {domain: [at, domainHi], count});
  } else if (optionHas('count')) {
    throw new TypeError('count applies only to callback capture');
  }
  const compiled = compileGuide(target);
  const windowed = optionHas('until') || optionHas('releaseWidth');
  const mode = optionHas('mode') ? options.mode : 'blend';
  if (mode !== 'blend' && mode !== 'add') throw new TypeError('mode must be blend or add');
  const reference = optionHas('reference') ? options.reference : mode === 'add' ? 'baseline' : 'value';
  if (!['value', 'baseline', 'backbone'].includes(reference)) {
    throw new TypeError('reference must be value, baseline, or backbone');
  }

  const handoffLimit = Math.min(domainHi, compiled.domain[1]);
  const handoffWidth = optionHas('handoffWidth')
    ? options.handoffWidth
    : Math.min(16, handoffLimit - at);
  finite(handoffWidth, Number.MIN_VALUE, 50000, 'Handoff width');
  const handoffEnd = at + handoffWidth;
  if (!Number.isFinite(handoffEnd) || handoffEnd <= at || handoffEnd > domainHi ||
      compiled.domain[0] > at || handoffEnd > handoffLimit) {
    throw new RangeError('Guide must cover a representable handoff inside the domain');
  }

  let until;
  let releaseWidth;
  let releaseEnd;
  if (windowed) {
    if (!optionHas('until') || !optionHas('releaseWidth')) throw new TypeError('Window requires until and positive releaseWidth');
    releaseWidth = options.releaseWidth;
    finite(releaseWidth, Number.MIN_VALUE, 50000, 'Release width');
    until = optionHas('until') ? options.until : domainHi - releaseWidth;
    finite(until, handoffEnd, domainHi, 'Release start');
    releaseEnd = until + releaseWidth;
    if (!Number.isFinite(releaseEnd) || (releaseWidth > 0 && releaseEnd <= until) || releaseEnd > domainHi) {
      throw new RangeError('Windowed influence exceeds the representable domain');
    }
    if (compiled.domain[1] < releaseEnd) {
      throw new RangeError('Guide must cover the active window through its release endpoint');
    }
  } else {
    until = undefined;
    releaseWidth = undefined;
    releaseEnd = undefined;
    if (compiled.domain[0] > at || handoffEnd > handoffLimit) {
      throw new RangeError('Guide must cover a representable handoff inside the domain');
    }
  }

  const zeroGuide = mode === 'add' && (compiled.spec.kind === 'polynomial'
    ? compiled.spec.coefficients.every(c => c === 0)
    : compiled.spec.knots.every(k => k.y === 0 && k.d1 === 0 && k.d2 === 0));
  const recipe = Object.freeze({at, amount, handoffWidth, windowed, until, releaseWidth, releaseEnd, reference, mode});
  const schema2 = mode === 'add' || windowed || reference !== 'value';
  const entry = mode === 'add' || windowed || reference !== 'value'
    ? Object.freeze({at, amount, handoffWidth, guide: compiled.spec, reference, ...(mode === 'add' ? {mode} : {}), ...(windowed ? {until, releaseWidth} : {})})
    : Object.freeze({at, amount, handoffWidth, guide: compiled.spec});
  const influenceHead = appendInfluence(curve, entry);
  const domain = windowed
    ? Object.freeze([domainLo, domainHi])
    : Object.freeze([domainLo, Math.min(domainHi, compiled.domain[1])]);
  const config = curve.config;
  const lower = config.floor === 'none' ? -Number.MAX_VALUE : config.floor === 'zero' ? -1 : config.floor === 'baseline' ? 0 : -.25 * config.strength;
  const upper = config.ceilingMargin === null
    ? Number.MAX_VALUE
    : (config.floor === 'baseline' ? 0 : .25 * config.strength) + config.ceilingMargin;
  const bounds = Object.freeze({
    ...curve.bounds,
    deviation: Object.freeze([lower, upper]),
    multiplier: Object.freeze([1 + lower, 1 + upper])
  });

  function check(x) {
    finite(x, domain[0], domain[1], 'Influence coordinate');
  }

  let api;
  function transform(parent, x) {
    const amountAt = weightAt(x, recipe);
    return amountAt === 0 || zeroGuide ? parent : affected(parent, compiled.evaluate(x), amountAt, config, reference, mode);
  }
  function sample(x, latent, explicit = false) {
    return evaluateCurve(api, x, latent, explicit);
  }

  function snapshotWith(parentSnapshot) {
    if (!schema2) {
      return Object.freeze({
        algorithm: INFLUENCE_ALGORITHM,
        curve: parentSnapshot,
        guide: compiled.spec,
        at,
        amount,
        handoffWidth
      });
    }
    const snapshot = {
      algorithm: mode === 'add' ? INFLUENCE_ALGORITHM_V3 : INFLUENCE_ALGORITHM_V2,
      curve: parentSnapshot,
      guide: compiled.spec,
      at,
      amount,
      handoffWidth,
      reference
    };
    if (mode === 'add') snapshot.mode = mode;
    if (windowed) {
      snapshot.until = until;
      snapshot.releaseWidth = releaseWidth;
    }
    return Object.freeze(snapshot);
  }

  function fork(options) {
    record(options, ['at', 'operation', 'handoffWidth'], 'Fork options: at, operation, handoffWidth');
    const earliest = Math.max(domain[0], latest(api));
    finite(options.at, earliest, domain[1], 'Fork activation');
    const width = hasOwn(options, 'handoffWidth') ? options.handoffWidth : Math.min(16, domain[1] - options.at);
    finite(width, Number.MIN_VALUE, 50000, 'Fork handoff width');
    const end = options.at + width;
    if (!Number.isFinite(end) || end <= options.at || end > domain[1]) throw new RangeError('Fork exceeds influence domain');
    return forkCurve(api, {...options, handoffWidth: width});
  }

  function tail(from) {
    check(from);
    if (typeof curve.tail !== 'function') throw new TypeError('The parent timeline must provide tail(from)');
    return snapshotCurve(api, from);
  }

  api = Object.freeze({
    algorithm: mode === 'add' ? INFLUENCE_ALGORITHM_V3 : schema2 ? INFLUENCE_ALGORITHM_V2 : INFLUENCE_ALGORITHM,
    config,
    bounds,
    domain,
    dips: curve.dips,
    get lineage() { return curveLineage(curve); },
    get influences() { return materializeInfluences(influenceHead); },
    sample: x => sample(x),
    jet: (x,h) => evaluateCurveJet(api,x,h,h!==undefined),
    slope: (x,h) => evaluateCurveJet(api,x,h,h!==undefined).slope,
    curvature: (x,h) => evaluateCurveJet(api,x,h,h!==undefined).curvature,
    at: (x, latent) => sample(x, latent, true),
    location: x => { check(x); return curveOrigin(curve).location(x); },
    inspect: x => { check(x); return curveOrigin(curve).inspect(x); },
    ridges: () => curveOrigin(curve).ridges(),
    influenceAt: x => {
      check(x);
      const weight = weightAt(x, recipe);
      const parent = evaluateCurve(curve, x), output = weight === 0 ? null : compiled.evaluate(x);
      if (mode === 'add') {
        const increment = output === null ? 0 : reference === 'value' ? weight * output : parent.base * (weight * output);
        return Object.freeze({mode, weight, targetValue: null, sourceValue: parent.value, followingError: null,
          increment, deviationIncrement: increment / parent.base});
      }
      const targetValue = output === null ? null : reference === 'value' ? output
        : reference === 'baseline' ? parent.base * (1 + output)
        : parent.unconstrainedGuideValue + parent.base * output;
      return Object.freeze({weight, targetValue, sourceValue: parent.value,
        followingError: targetValue === null ? null : sample(x).value - targetValue});
    },
    snapshot: () => snapshotCurve(api),
    tail,
    fork,
    influence: (guide, influenceOptions) => makeInfluence(api, guide, influenceOptions)
  });
  registerUnaryCurve(api, curve, {
    domain, latest: Math.max(latestWriteTime(curve), at), influenceHead,
    interval: [at, windowed ? releaseEnd : Infinity], alwaysIdentity: amount === 0 || zeroGuide,
    identityAt: x => amount === 0 || x <= at || (windowed && x >= releaseEnd),
    validate: check,
    transform: (parent,x) => transform(parent,x),
    jetTransform: (parent,x) => influenceJet(parent,compiled,recipe,config,transform(parent.sample,x),x,policy),
    snapshotFrame: parents => snapshotWith(parents[0]),
    discardOnTail: from => windowed && releaseEnd <= from,
    rebuild: parents => makeInfluence(parents[0], compiled.spec, recipeOptions(recipe), true)
  });
  return decorateCurve(api);
}

export function withInfluence(curve, target, options) {
  return makeInfluence(curve, target, options);
}

function validateSnapshot(snapshot) {
  if (!snapshot || typeof snapshot !== 'object' || Array.isArray(snapshot)) throw new TypeError('Invalid influence snapshot');
  const keys = Reflect.ownKeys(snapshot);
  if (snapshot.algorithm === INFLUENCE_ALGORITHM) {
    if (keys.length !== SNAPSHOT_V1_KEYS.length || keys.some(key => !SNAPSHOT_V1_KEYS.includes(key)) ||
        SNAPSHOT_V1_KEYS.some(key => !hasOwn(snapshot, key))) {
      throw new TypeError('Invalid schema 1 influence snapshot fields');
    }
    return {guide: snapshot.guide, options: {at: snapshot.at, amount: snapshot.amount, handoffWidth: snapshot.handoffWidth}};
  }
  const schema3 = snapshot.algorithm === INFLUENCE_ALGORITHM_V3;
  if (!schema3 && snapshot.algorithm !== INFLUENCE_ALGORITHM_V2) throw new TypeError('Unsupported influence algorithm');
  if (schema3 && snapshot.mode !== 'add') throw new TypeError('Schema 3 requires additive mode');
  const hasUntil = hasOwn(snapshot, 'until');
  const hasReleaseWidth = hasOwn(snapshot, 'releaseWidth');
  if (hasUntil !== hasReleaseWidth) throw new TypeError('Window snapshots require both until and releaseWidth');
  const expected = [...(hasUntil ? SNAPSHOT_V2_WINDOW_KEYS : SNAPSHOT_V2_BASE_KEYS), ...(schema3 ? ['mode'] : [])];
  if (keys.length !== expected.length || keys.some(key => !expected.includes(key)) || expected.some(key => !hasOwn(snapshot, key))) {
    throw new TypeError('Invalid schema 2 influence snapshot fields');
  }
  const options = {at: snapshot.at, amount: snapshot.amount, handoffWidth: snapshot.handoffWidth, reference: snapshot.reference};
  if (schema3) options.mode = snapshot.mode;
  if (hasUntil) {
    options.until = snapshot.until;
    options.releaseWidth = snapshot.releaseWidth;
  } else if (!schema3 && snapshot.reference === 'value') {
    throw new TypeError('Schema 2 requires an extended influence recipe');
  }
  return {guide: snapshot.guide, options};
}

export function restoreInfluence(snapshot, restore) {
  if (typeof restore !== 'function') throw new TypeError('A parent restore function is required');
  const frames = [];
  const seen = new Set();
  let current = snapshot;
  while (current && typeof current === 'object' && !Array.isArray(current) && isInfluenceAlgorithm(current.algorithm)) {
    if (seen.has(current)) throw new TypeError('Cyclic influence composition');
    seen.add(current);
    frames.push(validateSnapshot(current));
    current = current.curve;
  }
  let result = restore(current);
  for (const frame of frames.reverse()) result = makeInfluence(result, frame.guide, frame.options, true);
  return result;
}
