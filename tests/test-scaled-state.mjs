import assert from 'node:assert/strict';
import {Wide} from '../vixen-wide.mjs';
import {applyWidePolicy, ceilWide, floorWide, makeScaledSample} from '../vixen-scaled-state.mjs';

const wide = (n, e = 0n) => new Wide(BigInt(n), BigInt(e));
const zero = Wide.fromNumber(0);
const one = Wide.fromNumber(1);
const keys = [
  'x', 'logBase', 'score', 'guideScore', 'gate', 'rawDeviation',
  'unconstrainedDeviation', 'unconstrainedGuideDeviation', 'deviation', 'guideDeviation',
  'multiplier', 'activity', 'positiveMultiplier', 'signedLogValue', 'value', 'base',
  'guideValue', 'unconstrainedValue', 'unconstrainedGuideValue', 'positiveValue',
  'stabilizationWeight', 'floorRatio', 'ceilingRatio', 'floorValue', 'ceilingValue',
  'boundaryWidthRatio', 'boundaryWidth', 'constraintApplied', 'scaledValue', 'scaledBase',
  'scaledGuideValue', 'scaledUnconstrainedValue', 'scaledUnconstrainedGuideValue',
  'scaledPositiveValue', 'scaledFloorValue', 'scaledCeilingValue', 'scaledBoundaryWidth',
  'scaledDeviation', 'scaledGuideDeviation', 'scaledUnconstrainedDeviation',
  'scaledUnconstrainedGuideDeviation'
];

const tiny = Wide.fromRatio(1n, 1n, -1000n);
assert.ok(floorWide(tiny, zero, one).cmp(zero) > 0, 'lower strip retains a tiny positive input');
assert.equal(ceilWide(tiny, one, one).cmp(tiny), 0, 'upper lower-buffer formula retains tiny y when hi equals e');

assert.equal(floorWide(zero, zero, one).cmp(zero), 0);
assert.equal(floorWide(one, zero, one).cmp(one), 0);
assert.equal(ceilWide(zero, one, one).cmp(zero), 0);
assert.equal(ceilWide(one, one, one).cmp(one), 0);
assert.equal(applyWidePolicy(wide(5), wide(3), wide(3), one).cmp(wide(3)), 0);
assert.equal(applyWidePolicy(wide(5), wide(3), wide(2), one).cmp(wide(3)), 0);
assert.throws(() => floorWide(one, zero, wide(-1)), RangeError);
assert.throws(() => applyWidePolicy(one, zero, one, wide(-1)), RangeError);

function state(base, value, guide = value) {
  return {
    base,
    unconstrainedValue: value,
    unconstrainedGuideValue: guide,
    floorValue: null,
    ceilingValue: null,
    boundaryWidth: zero,
    score: 0,
    guideScore: 0,
    gate: 1,
    rawDeviation: 0,
    stabilizationWeight: 0.5,
    x: 120
  };
}

const target = one;
const hugeBaseline = wide(1, 2000);
const sample = makeScaledSample(state(hugeBaseline, target));
assert.deepEqual(Object.keys(sample), keys);
assert.equal(sample.value, 1, 'absolute target remains 1 even when its normalized deviation rounds to -1');
assert.equal(Wide.decode(sample.scaledValue).cmp(target), 0);
assert.equal(sample.deviation, -1);
assert.equal(sample.multiplier, null, 'unrepresentable normalized values are null rather than zero');
assert.deepEqual(sample.signedLogValue, {sign: 1, logAbs: 0});
assert.equal(Wide.decode(sample.scaledBase).cmp(hugeBaseline), 0);

const hugeValue = wide(1, 2000);
const hugeSample = makeScaledSample(state(one, hugeValue));
assert.equal(hugeSample.activity, 1, 'positive huge deviations saturate the bounded adapter');
assert.equal(hugeSample.positiveMultiplier, Math.exp(0.55));
assert.equal(hugeSample.value, null);
assert.equal(Wide.decode(hugeSample.scaledValue).cmp(hugeValue), 0);

for (const [key, value] of Object.entries(sample)) {
  if (key.startsWith('scaled') || key === 'signedLogValue' || key === 'constraintApplied') continue;
  assert.ok(value === null || (typeof value === 'number' && Number.isFinite(value)), `${key} must be finite or null`);
}
assert.throws(() => { sample.value = 9; }, TypeError);
assert.throws(() => { sample.scaledValue.sign = -1; }, TypeError);
