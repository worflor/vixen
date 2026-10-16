import {exactCorrelationFactor} from './vixen-correlation.mjs';
import * as I from './vixen-interval.mjs';
const hasOwn = (value, key) => Object.prototype.hasOwnProperty.call(value, key);
const finite = value => typeof value === 'number' && Number.isFinite(value);
const record = value => value !== null && typeof value === 'object' && !Array.isArray(value);

function intervalPair(value, label) {
  if (!Array.isArray(value) || value.length !== 2 || !finite(value[0]) || !finite(value[1]) || value[0] > value[1]) {
    throw new RangeError(`${label} must be an ordered pair of finite numbers`);
  }
  return [value[0], value[1]];
}

function crossingOptions(options) {
  if (!record(options)) throw new TypeError('Options must be an object');
  const allowed = new Set(['enclose', 'enclosePoint', 'evaluate', 'measure', 'domain', 'continuous', 'tolerance', 'maxIntervals', 'maxIterations', 'maxDepth']);
  for (const key of Object.keys(options)) if (!allowed.has(key)) throw new TypeError(`Unknown option: ${key}`);
  const tolerance = options.tolerance ?? 1e-6;
  const maxIntervals = options.maxIntervals ?? 10000;
  const maxIterations = options.maxIterations ?? 10000;
  const maxDepth = options.maxDepth ?? 64;
  if (!finite(tolerance) || tolerance <= 0) throw new RangeError('tolerance must be finite and positive');
  for (const [name, value] of [['maxIntervals', maxIntervals], ['maxIterations', maxIterations], ['maxDepth', maxDepth]]) {
    if (!Number.isSafeInteger(value) || value < 1) throw new RangeError(`${name} must be a positive safe integer`);
  }
  const measure = options.measure ?? 'deviation';
  if (typeof measure !== 'string' || measure.length === 0) throw new TypeError('measure must be a nonempty string');
  if (options.continuous !== undefined && typeof options.continuous !== 'boolean') throw new TypeError('continuous must be boolean');
  return {tolerance, maxIntervals, maxIterations, maxDepth, measure};
}

function crossingDomain(curve, options) {
  const value = options.domain ?? curve?.domain;
  if (value === undefined) return null;
  return intervalPair(value, 'domain');
}

function crossingEncloser(curve, options, measure) {
  if (options.enclose !== undefined) {
    if (typeof options.enclose !== 'function') throw new TypeError('enclose must be a function');
    return (lo, hi) => options.enclose(lo, hi, measure);
  }
  if (curve && typeof curve.encloseDeviation === 'function' && measure === 'deviation') {
    return (lo, hi) => curve.encloseDeviation(lo, hi);
  }
  const provider = curve?.enclosureProvider ?? curve?.enclose;
  if (provider !== undefined) {
    if (typeof provider !== 'function') throw new TypeError('Curve enclosure provider must be a function');
    return (lo, hi) => provider.call(curve, lo, hi, measure);
  }
  return null;
}

function crossingEvaluator(curve, options, measure) {
  let evaluate = options.evaluate;
  if (evaluate !== undefined && typeof evaluate !== 'function') throw new TypeError('evaluate must be a function');
  if (!evaluate && typeof curve === 'function') evaluate = curve;
  if (!evaluate && curve && typeof curve.sample === 'function') evaluate = x => curve.sample(x);
  if (!evaluate) return null;
  return x => {
    const result = evaluate(x);
    const value = typeof result === 'number' ? result : record(result) && hasOwn(result, measure) ? result[measure] : NaN;
    if (!finite(value)) throw new RangeError(`Curve output ${measure} must be finite`);
    return value;
  };
}

function checkedEnclosure(enclose, lo, hi) {
  const result = enclose(lo, hi);
  return intervalPair(result, 'enclosure');
}

function outstanding(items, reason) {
  return items.map(({lo, hi}) => ({interval: [lo, hi], reason}));
}

/** Certified first-threshold query. Certification depends on the caller's enclosure and continuity contracts. */
export function nextCrossing(curve, threshold, from, horizon, options = {}) {
  if(!finite(threshold)||!finite(from)||!finite(horizon)||horizon<from)throw new RangeError('threshold and ordered query bounds must be finite');
  const {tolerance,maxIntervals,maxIterations,maxDepth,measure}=crossingOptions(options);
  const domain=options.domain===undefined?curve?.domain:options.domain;
  if(domain!==undefined){const d=intervalPair(domain,'domain');if(from<d[0]||horizon>d[1])throw new RangeError('query outside declared domain');}
  if(options.continuous!==undefined&&typeof options.continuous!=='boolean')throw new TypeError('continuous must be boolean');
  const continuous=options.continuous===true,stack=[{lo:from,hi:horizon,depth:0}],points=new Map();let checks=0,pointChecks=0;
  const enclose=crossingEncloser(curve,options,measure),evaluate=crossingEvaluator(curve,options,measure);
  const pointOracle=options.enclosePoint===undefined?curve?.enclosePoint:options.enclosePoint;
  if(pointOracle!==undefined&&typeof pointOracle!=='function')throw new TypeError('enclosePoint must be callable');
  const unresolved=(first,reason)=>({status:'unresolved',reason,interval:[first.lo,first.hi],certifiedBefore:[from,first.lo],certifiedBeforeUpperExclusive:true,unresolved:[{interval:[first.lo,first.hi],reason},...stack.map(q=>({interval:[q.lo,q.hi],reason:'not-visited'}))].sort((a,b)=>a.interval[0]-b.interval[0]),enclosureChecks:checks,pointChecks});
  if(!enclose)return unresolved(stack.pop(),'enclosure-unavailable');
  if(!evaluate)return unresolved(stack.pop(),'evaluator-unavailable');
  const point=x=>{if(!points.has(x)){const interval=pointOracle?checkedEnclosure(z=>pointOracle.call(curve,z,measure),x,x):checkedEnclosure(enclose,x,x);pointChecks++;points.set(x,{interval,value:evaluate(x)});}return points.get(x);};
  const exact=p=>p.interval[0]===threshold&&p.interval[1]===threshold;
  const opposite=(a,b)=>(a.interval[1]<threshold&&b.interval[0]>threshold)||(a.interval[0]>threshold&&b.interval[1]<threshold);
  const found=(lo,hi,a,b)=>({status:'found',interval:[lo,hi],endpointValues:[a.value,b.value],endpointEnclosures:[a.interval,b.interval],certifiedBefore:[from,lo],certifiedBeforeUpperExclusive:true,unresolved:[],enclosureChecks:checks,pointChecks,continuousAssumed:continuous,pointIsolated:lo===hi});
  while(stack.length){
    const q=stack.pop();if(checks>=Math.min(maxIntervals,maxIterations))return unresolved(q,'budget');
    const box=checkedEnclosure(enclose,q.lo,q.hi);checks++;
    if(threshold<box[0]||threshold>box[1])continue;
    const left=point(q.lo);if(exact(left))return found(q.lo,q.lo,left,left);
    const width=q.hi-q.lo,right=point(q.hi);
    if(width<=tolerance/2){
      if(exact(right)||(continuous&&opposite(left,right)))return found(q.lo,q.hi,left,right);
      // A dyadic split can put a root on an uncertain shared endpoint. A small
      // overlapping hull proves a bracket without asserting endpoint equality.
      const lo=Math.max(from,q.lo-tolerance/4),hi=Math.min(horizon,q.hi+tolerance/4);
      if(continuous&&hi-lo<=tolerance){const a=point(lo),b=point(hi);if(opposite(a,b))return found(lo,hi,a,b);}
      if(!continuous)return unresolved(q,'continuity-required');
    }
    const mid=q.lo/2+q.hi/2;
    if(q.depth>=maxDepth||mid===q.lo||mid===q.hi)return unresolved(q,q.depth>=maxDepth?'depth':'machine-spacing');
    stack.push({lo:mid,hi:q.hi,depth:q.depth+1},{lo:q.lo,hi:mid,depth:q.depth+1});
  }
  return {status:'absent',interval:[from,horizon],certifiedAbsent:[from,horizon],unresolved:[],enclosureChecks:checks,pointChecks};
}

export function makeLipschitzEnclosure(evaluate, bound, pointError = 0) {
  if (typeof evaluate !== 'function') throw new TypeError('evaluate must be a function');
  if (!finite(bound) || bound < 0 || !finite(pointError) || pointError < 0) throw new RangeError('bound and pointError must be finite and nonnegative');
  return (lo, hi) => {
    if (!finite(lo) || !finite(hi) || lo > hi) throw new RangeError('Invalid enclosure interval');
    const mid = Math.max(lo,Math.min(hi,lo/2+hi/2));
    const value = evaluate(mid);
    if (!finite(value)) throw new RangeError('Point evaluation must be finite');
    const distance=Math.max(I.subtract(I.interval(mid),I.interval(lo)).hi,I.subtract(I.interval(hi),I.interval(mid)).hi);
    const radius=I.add(I.multiply(I.interval(bound),I.interval(distance)),I.interval(pointError)).hi;
    const result=I.add(I.interval(value),I.interval(-radius,radius));
    return [result.lo,result.hi];
  };
}

function populationRows(population) {
  if (!Array.isArray(population) || population.length === 0) throw new RangeError('population must be a nonempty array');
  return population.map((item, index) => {
    let deviation, weight = 1;
    if (finite(item)) deviation = item;
    else if (record(item) && hasOwn(item, 'deviation') && hasOwn(item, 'weight') && Object.keys(item).every(k => k === 'deviation' || k === 'weight')) {
      deviation = item.deviation;
      weight = item.weight;
    } else throw new TypeError(`Invalid population entry at ${index}`);
    if (!finite(deviation) || !finite(weight) || weight <= 0) throw new RangeError(`Invalid population value or weight at ${index}`);
    return {deviation, weight};
  });
}

function nextUp(value) {
  if (value === Infinity) return Infinity;
  if (Object.is(value, -0)) value = 0;
  if (value === 0) return Number.MIN_VALUE;
  const buffer = new ArrayBuffer(8);
  const view = new DataView(buffer);
  view.setFloat64(0, value, false);
  let bits = view.getBigUint64(0, false);
  bits += value > 0 ? 1n : -1n;
  view.setBigUint64(0, bits, false);
  return view.getFloat64(0, false);
}

function freezeRecipe(recipe) {
  for (const value of Object.values(recipe)) if (Array.isArray(value)) Object.freeze(value);
  return Object.freeze(recipe);
}

function occupancyValue(recipe, deviation) {
  if (!finite(deviation)) throw new RangeError('deviation must be finite');
  const t=recipe.outputThreshold;
  if(deviation===recipe.cutoff)return t;
  const z=2*((recipe.slope/recipe.scale)*(deviation-recipe.cutoff));
  if(z>=0){const e=Math.exp(-z);return t/(t+(1-t)*e);}
  const e=Math.exp(z);return (t*e)/(1-t+t*e);
}

function occupancyObject(recipe) {
  const frozen = freezeRecipe({...recipe});
  return Object.freeze({
    recipe: frozen,
    targetFraction: frozen.targetFraction,
    achievedFraction: frozen.achievedFraction,
    difference: frozen.achievedFraction - frozen.targetFraction,
    cutoff: frozen.cutoff,
    tieHandling: frozen.tieHandling,
    totalPopulation: frozen.totalPopulation,
    totalWeight: frozen.totalWeight,
    evaluate: deviation => occupancyValue(frozen, deviation),
    serialize: () => ({...frozen})
  });
}

/** Fit a monotone shifted tanh to an explicit empirical population. */
export function calibrateOccupancy(population, options = {}) {
  if (!record(options)) throw new TypeError('Options must be an object');
  const allowed = new Set(['targetFraction', 'outputThreshold', 'slope', 'scale']);
  for (const key of Object.keys(options)) if (!allowed.has(key)) throw new TypeError(`Unknown option: ${key}`);
  const target = options.targetFraction;
  const threshold = options.outputThreshold;
  const slope = options.slope ?? 1;
  const scale = options.scale ?? 1;
  if (!finite(target) || target < 0 || target > 1) throw new RangeError('targetFraction must be in [0,1]');
  if (!finite(threshold) || threshold <= 0 || threshold >= 1) throw new RangeError('outputThreshold must be strictly inside (0,1)');
  if (!finite(slope) || slope <= 0 || !finite(scale) || scale <= 0 || !finite(slope / scale) || slope / scale === 0) throw new RangeError('slope and scale must have a finite positive ratio');
  const rows = populationRows(population);
  const totalWeight = rows.reduce((sum, row) => sum + row.weight, 0);
  if (!finite(totalWeight) || totalWeight <= 0) throw new RangeError('Total population weight must be finite and positive');
  const values = [...new Set(rows.map(row => row.deviation))].sort((a, b) => a - b);
  const candidates = [];
  if (target === 1) candidates.push(values[0]);
  else if (target === 0) candidates.push(nextUp(values[values.length - 1]));
  else {
    for (const value of values) {
      candidates.push(value);
      const above = nextUp(value);
      if (Number.isFinite(above)) candidates.push(above);
    }
  }
  const scored = candidates.filter(finite).map(cutoff => {
    const candidate={outputThreshold:threshold,slope,scale,cutoff};
    const included = rows.reduce((sum,row)=>sum+(occupancyValue(candidate,row.deviation)>=threshold?row.weight:0),0);
    const achieved = included / totalWeight;
    return {cutoff, achieved, error: Math.abs(achieved - target)};
  }).sort((a, b) => a.error - b.error || Number(a.achieved > target) - Number(b.achieved > target) || a.cutoff - b.cutoff);
  if (!scored.length) throw new RangeError('Requested endpoint fraction has no finite representable cutoff');
  const chosen = scored[0];
  const q = .5*(Math.log(threshold)-Math.log1p(-threshold));
  const center = chosen.cutoff - q / (slope / scale);
  if (!finite(center)) throw new RangeError('Calibration recipe is not finite for this population and slope');
  const tieHandling = rows.some(row => row.deviation === chosen.cutoff) ? 'inclusive-at-cutoff' : 'values-at-or-above-cutoff';
  return occupancyObject({format: 'vixen-occupancy/1', targetFraction: target, outputThreshold: threshold, slope, scale, cutoff: chosen.cutoff, center, totalPopulation: rows.length, totalWeight, achievedFraction: chosen.achieved, tieHandling});
}

/** Measure an explicit holdout set; this is an empirical report only. */
export function validateOccupancy(calibration, holdout) {
  const recipe = calibration?.recipe ?? calibration;
  if (!record(recipe) || recipe.format !== 'vixen-occupancy/1') throw new TypeError('A Vixen occupancy recipe is required');
  const rows = populationRows(holdout);
  const totalWeight = rows.reduce((sum, row) => sum + row.weight, 0);
  if (!finite(totalWeight) || totalWeight <= 0) throw new RangeError('Total holdout weight must be finite and positive');
  const included = rows.reduce((sum, row) => sum + (occupancyValue(recipe, row.deviation) >= recipe.outputThreshold ? row.weight : 0), 0);
  const achievedFraction = included / totalWeight;
  return Object.freeze({targetFraction: recipe.targetFraction, achievedFraction, difference: achievedFraction - recipe.targetFraction, absoluteError: Math.abs(achievedFraction - recipe.targetFraction), totalPopulation: rows.length, totalWeight, highPopulation: rows.filter(row => occupancyValue(recipe, row.deviation) >= recipe.outputThreshold).length, highWeight: included});
}

export function restoreOccupancy(recipe) {
  if (!record(recipe) || recipe.format !== 'vixen-occupancy/1') throw new TypeError('Invalid occupancy recipe');
  for (const key of ['targetFraction', 'outputThreshold', 'slope', 'scale', 'cutoff', 'center', 'totalWeight', 'achievedFraction']) if (!finite(recipe[key])) throw new RangeError(`Invalid recipe field: ${key}`);
  if (recipe.targetFraction < 0 || recipe.targetFraction > 1 || recipe.outputThreshold <= 0 || recipe.outputThreshold >= 1 || recipe.slope <= 0 || recipe.scale <= 0 || !finite(recipe.slope/recipe.scale) || recipe.slope/recipe.scale === 0 || recipe.totalWeight <= 0 || recipe.achievedFraction < 0 || recipe.achievedFraction > 1 || !Number.isSafeInteger(recipe.totalPopulation) || recipe.totalPopulation < 1 || typeof recipe.tieHandling !== 'string') throw new RangeError('Invalid occupancy recipe values');
  const restored = occupancyObject({...recipe});
  return restored;
}

function numeric(value, label) {
  if (!finite(value)) throw new RangeError(`${label} must be finite`);
  return value;
}

function correlationTolerance(value) {
  if (!finite(value) || value <= 0) throw new RangeError('tolerance must be finite and positive');
  return value;
}

export function validateCorrelationMatrix(input,tolerance=1e-10){return exactCorrelationFactor(input,tolerance);}

function seed32(seed) {
  if (typeof seed === 'number') {
    if (!Number.isInteger(seed) || seed < 0 || seed > 0xffffffff) throw new RangeError('Numeric seed must be uint32');
    return seed >>> 0;
  }
  if (typeof seed !== 'string' || seed.length > 1024) throw new TypeError('Seed must be text or uint32');
  let hash = 2166136261;
  for (const byte of new TextEncoder().encode(seed)) hash = Math.imul(hash ^ byte, 16777619) >>> 0;
  return hash;
}

function hash32(seed, index, channel) {
  let hash = (seed ^ Math.imul(index >>> 0, 0x9e3779b9) ^ Math.imul(channel >>> 0, 0x85ebca6b)) >>> 0;
  hash = Math.imul(hash ^ (hash >>> 16), 0x7feb352d) >>> 0;
  hash = Math.imul(hash ^ (hash >>> 15), 0x846ca68b) >>> 0;
  return (hash ^ (hash >>> 16)) >>> 0;
}

function deriveSeed(rootSeed, index) {
  const kind = typeof rootSeed === 'string' ? 1 : 2;
  return hash32(hash32(seed32(rootSeed), kind, 0x56495845), index, 0x43484e4c);
}

function freezeDeep(value) {
  if (Array.isArray(value)) return Object.freeze(value.map(freezeDeep));
  if (record(value)) {
    for (const key of Object.keys(value)) value[key] = freezeDeep(value[key]);
    return Object.freeze(value);
  }
  return value;
}

/** Bind independent source curves to a PSD factor. No Vixen import is required. */
export function createCorrelatedChannels(inputMatrix, createSource, rootSeed, options = {}) {
  if (typeof createSource !== 'function') throw new TypeError('createSource must be a function accepting a derived seed');
  if (!record(options) || Object.keys(options).some(key => !['channel', 'tolerance'].includes(key))) throw new TypeError('Options may contain channel and tolerance');
  const channel = options.channel ?? 'score';
  if (!['score', 'unconstrainedDeviation'].includes(channel)) throw new RangeError('channel must be score or unconstrainedDeviation');
  const checked = validateCorrelationMatrix(inputMatrix, options.tolerance ?? 1e-10);
  const seeds = Array.from({length: checked.matrix.length}, (_, i) => deriveSeed(rootSeed, i));
  if (new Set(seeds).size !== seeds.length) throw new RangeError('Derived source seed collision');
  const sources = seeds.map(seed => createSource(seed));
  for (const source of sources) if (!source || typeof source.sample !== 'function' || typeof source.at !== 'function') throw new TypeError('Each source must provide sample(x) and at(x,h)');
  const domains = sources.map(source => intervalPair(source.domain, 'source domain'));
  const domain = [Math.max(...domains.map(item => item[0])), Math.min(...domains.map(item => item[1]))];
  if (domain[0] > domain[1]) throw new RangeError('Source curves have no shared domain');
  const extract = (result, seed) => {
    if (!record(result) || !hasOwn(result, channel) || !finite(result[channel])) throw new RangeError(`Source ${seed} returned an invalid ${channel}`);
    return result[channel];
  };
  const combine = values => checked.factor.map(row => {
    let result = 0;
    for (let i = 0; i < row.length; i++) result += row[i] * values[i];
    if (!finite(result)) throw new RangeError('Correlated channel overflow');
    return result;
  });
  const checkX = x => {
    if (!finite(x) || x < domain[0] || x > domain[1]) throw new RangeError('x must lie inside the shared source domain');
  };
  const sampleSources = (method, x, coordinates) => sources.map((source, i) => extract(method === 'sample' ? source.sample(x) : source.at(x, coordinates), seeds[i]));
  const recipe = freezeDeep({format: 'vixen-correlated-channels/1', matrix: checked.matrix.map(row => [...row]), factor: checked.factor.map(row => [...row]), tolerance: checked.tolerance, channel, rootSeed: {type: typeof rootSeed === 'string' ? 'text' : 'uint32', value: rootSeed}, derivedSeeds: [...seeds], seedDerivation: 'hash32-domain-separated/1'});
  const group = {
    domain: Object.freeze(domain),
    matrix: checked.matrix,
    factor: checked.factor,
    channel,
    sourceSeeds: Object.freeze([...seeds]),
    sourceConfigs: Object.freeze(seeds.map(seed => Object.freeze({seed, channel}))),
    recipe,
    sample(x) { checkX(x); return Object.freeze(combine(sampleSources('sample', x))); },
    at(x, coordinates) { checkX(x); return Object.freeze(combine(sampleSources('at', x, coordinates))); },
    serialize() { return freezeDeep(JSON.parse(JSON.stringify(recipe))); }
  };
  return Object.freeze(group);
}

/** Measure ordinary Pearson correlations over the caller's reference points. */
export function measureChannelCorrelation(group, points) {
  const sample = typeof group === 'function' ? group : group?.sample;
  if (typeof sample !== 'function' || !Array.isArray(points) || points.length < 2) throw new TypeError('Provide a sampler and at least two reference points');
  const rows = points.map(point => {
    const values = sample.call(group, point);
    if (!Array.isArray(values) || values.length === 0 || values.some(value => !finite(value))) throw new RangeError('Sampler must return a nonempty finite numeric vector');
    return values;
  });
  const n = rows.length, k = rows[0].length;
  if (rows.some(row => row.length !== k)) throw new RangeError('Sampler channel count changed');
  const means = Array.from({length: k}, (_, j) => rows.reduce((sum, row) => sum + row[j], 0) / n);
  const centered = rows.map(row => row.map((value, j) => value - means[j]));
  const sums = Array.from({length: k}, (_, j) => centered.reduce((sum, row) => sum + row[j] * row[j], 0));
  if (sums.some(value => !finite(value) || value === 0)) throw new RangeError('Cannot measure correlation with zero or invalid variance');
  const result = Array.from({length: k}, (_, i) => Array.from({length: k}, (_, j) => {
    const cross = centered.reduce((sum, row) => sum + row[i] * row[j], 0);
    const correlation = cross / Math.sqrt(sums[i] * sums[j]);
    if (!finite(correlation) || Math.abs(correlation) > 1 + 1e-12) throw new RangeError('Invalid measured correlation');
    return Math.max(-1, Math.min(1, correlation));
  }));
  return Object.freeze(result.map(row => Object.freeze(row)));
}
