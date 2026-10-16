import test from 'node:test';
import assert from 'node:assert/strict';
import {create, restore} from '../vixen.mjs';
import {withImpulse, withRedistribution, couple, perturbationJet, restoreOperation, OPERATION_ALGORITHM} from '../vixen-operations.mjs';

function restoreAny(snapshot) {
  return snapshot?.algorithm === OPERATION_ALGORITHM ? restoreOperation(snapshot, restoreAny) : restore(snapshot);
}
function close(a, b, tolerance = 1e-10) { assert.ok(Math.abs(a - b) <= tolerance * Math.max(1, Math.abs(a), Math.abs(b)), `${a} != ${b}`); }

test('impulses are causal, signed, C2 at support joins, and delegate outside support', () => {
  const parent = create({seed: 'operation-test'}), at = 120;
  const positive = withImpulse(parent, {at, magnitude: .2, attack: 2, relaxation: 8});
  const negative = withImpulse(parent, {at, magnitude: -.2, attack: 2, relaxation: 8});
  assert.deepEqual(positive.sample(at), parent.sample(at));
  assert.deepEqual(positive.sample(131), parent.sample(131));
  assert.deepEqual(positive.sample(at + 1).unconstrainedDeviation > parent.sample(at + 1).unconstrainedDeviation, true);
  assert.ok(negative.sample(at + 1).unconstrainedDeviation < parent.sample(at + 1).unconstrainedDeviation);
  for (const x of [at, at + 2, at + 10]) {
    const j = perturbationJet({type: 'impulse', at, magnitude: .2, attack: 2, relaxation: 8}, x);
    if (x !== at + 10) close(j.value === 0 ? 0 : j.first, 0);
    close(j.second, 0);
  }
  for (const x of [at, at + 2, at + 10]) {
    const j = perturbationJet({type: 'impulse', at, magnitude: -.2, attack: 2, relaxation: 8}, x);
    close(j.first, 0); close(j.second, 0);
  }
});

test('redistribution has zero endpoint jets and carries an exact-algebra descriptor', () => {
  const parent = create({seed: 'redistribution'});
  const curve = withRedistribution(parent, [120, 140], [1, -.5, .25]);
  assert.deepEqual(curve.sample(120), parent.sample(120));
  assert.deepEqual(curve.sample(140), parent.sample(140));
  const snapshot = curve.snapshot();
  assert.equal(snapshot.recipe.certificate.integral, 'Q(1)-Q(0)=0');
  for (const x of [120, 140]) {
    const jet = perturbationJet({type: 'redistribution', from: 120, until: 140, coefficients: [1, -.5, .25], magnitude: 1, measure: 'actualDeviation'}, x);
    assert.deepEqual(jet, {value: 0, first: 0, second: 0});
  }
  assert.throws(() => withRedistribution(create({floor: 'zero'}), [120, 140], [1]), /unconstrained source/);
  assert.doesNotThrow(() => withRedistribution(create({floor: 'zero'}), [120, 140], {coefficients: [1], measure: 'unconstrainedDeviation'}));
});

test('coupling preserves the pointwise sum of normalized deviations', () => {
  const a = create({seed: 'a'}), b = create({seed: 'b'}), c = create({seed: 'c'});
  const group = couple([a, b, c], .8, [120, 160]);
  for (const x of [120, 125, 140, 159, 160]) {
    const before = [a, b, c].reduce((s, curve) => s + curve.sample(x).deviation, 0);
    const after = group.sample(x).reduce((s, sample) => s + sample.deviation, 0);
    close(after, before, 1e-9);
  }
  assert.deepEqual(couple([a, b], 0, [120, 160]).sample(140), [a.sample(140), b.sample(140)]);
  assert.throws(() => couple([a, create({ceilingMargin: 1})], .5, [120, 160]), /nonlinear/);
  assert.throws(() => group.fork({at: 170, operation: 'x'}), /ambiguous/);
});

test('adapters, random access, active JSON replay, tail replay, and validation', () => {
  const base = create({seed: 17});
  const op = withImpulse(base, {at: 120, magnitude: .1, relaxation: 8}).redistribute([130, 150], {coefficients: [1, 2], magnitude: .3}).fork({at: 170, operation: 'branch'});
  const sample = op.sample(135);
  close(sample.multiplier, 1 + sample.deviation);
  close(sample.activity, .5 + .5 * Math.tanh(sample.deviation));
  close(sample.positiveValue, sample.base * sample.positiveMultiplier);
  assert.deepEqual(op.sampleRange(130, 132, 3)[0], op.sample(130));
  const replay = restoreAny(JSON.parse(JSON.stringify(op.snapshot())));
  assert.deepEqual(replay.sample(135), op.sample(135));
  const tail = op.tail(125), tailReplay = restoreAny(JSON.parse(JSON.stringify(tail)));
  assert.deepEqual(tailReplay.sample(135), op.sample(135));
  assert.throws(() => withImpulse(base, {at: 120, magnitude: 1, relaxation: 0}), /positive/);
  assert.throws(() => withRedistribution(base, [120, 120], [1]), /positive/);
  assert.throws(() => withRedistribution(base, [120, 140], [NaN]), /finite/);
  const cyclic = {...op.snapshot()}; cyclic.curve = cyclic;
  assert.throws(() => restoreOperation(cyclic, restoreAny), /cycles/);
});

test('coupling snapshots retain group persistence and views', () => {
  const group = couple([create({seed: 1}), create({seed: 2})], .5, [120, 150]);
  const replay = restoreAny(JSON.parse(JSON.stringify(group.snapshot())));
  assert.deepEqual(replay.sample(135), group.sample(135));
  const view = restoreAny(JSON.parse(JSON.stringify(group.curves[0].snapshot())));
  assert.deepEqual(view.sample(135), group.curves[0].sample(135));
});
