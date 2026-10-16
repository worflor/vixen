import {Wide} from './vixen-wide.mjs';

const ZERO = Wide.fromNumber(0);
const ONE = Wide.fromNumber(1);
const HALF = Wide.fromRatio(1n, 2n);
const SIX = Wide.fromNumber(6);
const NEGATIVE_EIGHT = Wide.fromNumber(-8);
const THREE = Wide.fromNumber(3);
const FOUR = Wide.fromNumber(4);
const LN2 = Math.LN2;

function requireWide(value, name) {
  if (!(value instanceof Wide)) throw new TypeError(`${name} must be a Wide`);
  return value;
}

function requireWidth(value) {
  const width = requireWide(value, 'boundaryWidth');
  if (width.cmp(ZERO) < 0) throw new RangeError('boundaryWidth must be nonnegative');
  return width;
}

function rampNearZero(t) {
  const t2 = t.mul(t);
  const t3 = t2.mul(t);
  return t3.mul(SIX.add(t.mul(NEGATIVE_EIGHT.add(THREE.mul(t)))));
}

export function floorWide(y, lo, e) {
  y = requireWide(y, 'y');
  lo = requireWide(lo, 'lo');
  e = requireWidth(e);
  if (y.cmp(lo) <= 0) return lo;
  if (e.cmp(ZERO) === 0) return y;
  const edge = lo.add(e);
  if (y.cmp(edge) >= 0) return y;

  const t = y.sub(lo).div(e);
  if (t.cmp(HALF) <= 0) return lo.add(e.mul(rampNearZero(t)));

  const oneMinusT = ONE.sub(t);
  const correction = e.mul(t).mul(oneMinusT.pow(3)).mul(ONE.add(THREE.mul(t)));
  return y.sub(correction);
}

export function ceilWide(y, hi, e) {
  y = requireWide(y, 'y');
  hi = requireWide(hi, 'hi');
  e = requireWidth(e);
  if (y.cmp(hi) >= 0) return hi;
  if (e.cmp(ZERO) === 0) return y;
  const lowerBuffer = hi.sub(e);
  if (y.cmp(lowerBuffer) <= 0) return y;

  const s = hi.sub(y).div(e);
  if (s.cmp(HALF) <= 0) return hi.sub(e.mul(rampNearZero(s)));

  const oneMinusS = ONE.sub(s);
  const correction = e.mul(oneMinusS).mul(s.pow(3)).mul(FOUR.sub(THREE.mul(s)));
  return y.add(correction);
}

function applyWideValuePolicy(y, lo, hi, e) {
  y = requireWide(y, 'y');
  if (lo !== null) lo = requireWide(lo, 'lo');
  if (hi !== null) hi = requireWide(hi, 'hi');
  e = requireWidth(e);
  if (lo !== null && hi !== null && hi.cmp(lo) <= 0) return lo;

  let value = lo === null ? y : floorWide(y, lo, e);
  if (hi !== null) value = ceilWide(value, hi, e);
  return value;
}

function scalar(state, key) {
  const value = state[key];
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    throw new RangeError(`${key} must be a finite number`);
  }
  return value;
}

function rawNumber(value) {
  try {
    const result = value.toNumber();
    return typeof result === 'number' && Number.isFinite(result) && (result !== 0 || value.sign === 0)
      ? result
      : null;
  } catch {
    return null;
  }
}

function logAbs(value) {
  if (value.sign === 0) return null;
  const exponent2 = Number(value.e + 52n);
  if (!Number.isFinite(exponent2)) return null;
  const result = Math.log(Math.abs(Number(value.m)) / 2 ** 52) + exponent2 * LN2;
  return Number.isFinite(result) ? result : null;
}

function signedLog(value) {
  return Object.freeze({sign: value.sign, logAbs: logAbs(value)});
}

function tanhWide(value) {
  if (value.sign === 0) return 0;
  const raw = rawNumber(value);
  if (raw !== null) return Math.tanh(raw);
  return value.e + 52n > 1023n ? value.sign : 0;
}

function scaled(value) {
  return value.toJSON();
}

function optionalRaw(value) {
  return value === null ? null : rawNumber(value);
}

export function makeScaledSample(state) {
  if (!state || typeof state !== 'object' || Array.isArray(state)) {
    throw new TypeError('state must be a record');
  }

  const base = requireWide(state.base, 'base');
  if (base.cmp(ZERO) <= 0) throw new RangeError('base must be positive');
  const unconstrainedValue = requireWide(state.unconstrainedValue, 'unconstrainedValue');
  const unconstrainedGuideValue = requireWide(state.unconstrainedGuideValue, 'unconstrainedGuideValue');
  const floorValue = state.floorValue === null ? null : requireWide(state.floorValue, 'floorValue');
  const ceilingValue = state.ceilingValue === null ? null : requireWide(state.ceilingValue, 'ceilingValue');
  const boundaryWidth = requireWidth(state.boundaryWidth);
  const score = scalar(state, 'score');
  const guideScore = scalar(state, 'guideScore');
  const gate = scalar(state, 'gate');
  const rawDeviation = scalar(state, 'rawDeviation');
  const stabilizationWeight = scalar(state, 'stabilizationWeight');
  const x = scalar(state, 'x');

  const value = applyWidePolicy(unconstrainedValue, floorValue, ceilingValue, boundaryWidth);
  const guideValue = applyWidePolicy(unconstrainedGuideValue, floorValue, ceilingValue, boundaryWidth);
  const ratio = value.div(base);
  const guideRatio = guideValue.div(base);
  const unconstrainedRatio = unconstrainedValue.div(base);
  const unconstrainedGuideRatio = unconstrainedGuideValue.div(base);
  const deviation = ratio.sub(ONE);
  const guideDeviation = guideRatio.sub(ONE);
  const unconstrainedDeviation = unconstrainedRatio.sub(ONE);
  const unconstrainedGuideDeviation = unconstrainedGuideRatio.sub(ONE);
  const boundedDeviation = tanhWide(deviation);
  const positiveMultiplier = Math.exp(0.55 * boundedDeviation);
  const positiveValue = base.mul(Wide.fromNumber(positiveMultiplier));
  const floorRatio = floorValue === null ? null : floorValue.div(base);
  const ceilingRatio = ceilingValue === null ? null : ceilingValue.div(base);
  const boundaryWidthRatio = boundaryWidth.div(base);

  return Object.freeze({
    x,
    logBase: logAbs(base),
    score,
    guideScore,
    gate,
    rawDeviation,
    unconstrainedDeviation: rawNumber(unconstrainedDeviation),
    unconstrainedGuideDeviation: rawNumber(unconstrainedGuideDeviation),
    deviation: rawNumber(deviation),
    guideDeviation: rawNumber(guideDeviation),
    multiplier: rawNumber(ratio),
    activity: 0.5 + 0.5 * boundedDeviation,
    positiveMultiplier,
    signedLogValue: signedLog(value),
    value: rawNumber(value),
    base: rawNumber(base),
    guideValue: rawNumber(guideValue),
    unconstrainedValue: rawNumber(unconstrainedValue),
    unconstrainedGuideValue: rawNumber(unconstrainedGuideValue),
    positiveValue: rawNumber(positiveValue),
    stabilizationWeight,
    floorRatio: optionalRaw(floorRatio),
    ceilingRatio: optionalRaw(ceilingRatio),
    floorValue: optionalRaw(floorValue),
    ceilingValue: optionalRaw(ceilingValue),
    boundaryWidthRatio: rawNumber(boundaryWidthRatio),
    boundaryWidth: rawNumber(boundaryWidth),
    constraintApplied: value.cmp(unconstrainedValue) !== 0,
    scaledValue: scaled(value),
    scaledBase: scaled(base),
    scaledGuideValue: scaled(guideValue),
    scaledUnconstrainedValue: scaled(unconstrainedValue),
    scaledUnconstrainedGuideValue: scaled(unconstrainedGuideValue),
    scaledPositiveValue: scaled(positiveValue),
    scaledFloorValue: floorValue === null ? null : scaled(floorValue),
    scaledCeilingValue: ceilingValue === null ? null : scaled(ceilingValue),
    scaledBoundaryWidth: scaled(boundaryWidth),
    scaledDeviation: scaled(deviation),
    scaledGuideDeviation: scaled(guideDeviation),
    scaledUnconstrainedDeviation: scaled(unconstrainedDeviation),
    scaledUnconstrainedGuideDeviation: scaled(unconstrainedGuideDeviation)
  });
}

export function applyWidePolicy(input,lo,hi,width){
 if(input instanceof Wide)return applyWideValuePolicy(input,lo,hi,width);
 const record=makeScaledSample(input),state={...input,...record};
 for(const key of ['base','value','guideValue','positiveValue','unconstrainedValue','unconstrainedGuideValue','floorValue','ceilingValue','boundaryWidth']){
  const encoded=record[`scaled${key[0].toUpperCase()}${key.slice(1)}`];state[key]=encoded===null?null:Wide.decode(encoded);
 }
 return Object.freeze(state);
}
