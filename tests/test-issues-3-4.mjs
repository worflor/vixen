import test from 'node:test';
import assert from 'node:assert/strict';
import * as V from '../vixen.mjs';
import {restoreInfluence} from '../vixen-influence.mjs';

test('windowed influence releases smoothly and delegates exactly outside support', () => {
  const root = V.create();
  const guide = V.polynomialGuide([25], {domain: [120, 170]});
  const influenced = root.influence(guide, {
    at: 120,
    amount: 1,
    handoffWidth: 10,
    until: 150,
    releaseWidth: 20
  });
  assert.deepEqual(influenced.domain, root.domain);
  for (const x of [1, 100, 120]) assert.deepEqual(influenced.sample(x), root.sample(x));
  assert.notEqual(influenced.sample(160).value, root.sample(160).value);
  assert.deepEqual(influenced.sample(170), root.sample(170));
  assert.deepEqual(influenced.sample(50000), root.sample(50000));
  assert.equal(influenced.influenceAt(170).targetValue, null);
});

test('deviation spaces use the existing baseline and unrestricted guide channels', () => {
  const root = V.create({seed: 'issues-3-4'});
  const guide = V.polynomialGuide([.2], {domain: [120, 500]});
  const baseline = root.influence(guide, {
    at: 120,
    amount: 1,
    handoffWidth: 10,
    reference: 'baseline'
  });
  const backbone = root.influence(guide, {
    at: 120,
    amount: 1,
    handoffWidth: 10,
    reference: 'backbone'
  });
  for (const x of [140, 200, 400]) {
    const p = root.sample(x);
    assert.equal(baseline.sample(x).unconstrainedValue, p.base * 1.2);
    assert.equal(backbone.sample(x).unconstrainedValue, p.unconstrainedGuideValue + p.base * .2);
  }
  assert.equal(baseline.snapshot().algorithm, 'vixen-influence-c2/2');
  assert.equal(backbone.snapshot().algorithm, 'vixen-influence-c2/2');
});

test('same-time influences apply in creation order and schema 1 replay remains available', () => {
  const root = V.create();
  const earlier = root.influence(V.polynomialGuide([5], {domain: [120, 500]}), {
    at: 120, amount: 1, handoffWidth: 10
  });
  const later = earlier.influence(V.polynomialGuide([9], {domain: [120, 500]}), {
    at: 120, amount: 1, handoffWidth: 10
  });
  assert.equal(later.sample(150).value, 9);
  const oldSchema = earlier.snapshot();
  assert.equal(oldSchema.algorithm, 'vixen-influence-c2/1');
  assert.deepEqual(V.restore(JSON.parse(JSON.stringify(oldSchema))).sample(150), earlier.sample(150));
});

test('schema 2 JSON replay, active checkpoint carry, and lower-domain checks', () => {
  const root = V.create();
  const influenced = root.influence(V.polynomialGuide([25], {domain: [120, 170]}), {
    at: 120, amount: 1, handoffWidth: 10, until: 150, releaseWidth: 20
  });
  const json = JSON.parse(JSON.stringify(influenced.snapshot()));
  const replay = restoreInfluence(json, V.restore);
  for (const x of [120, 130, 150, 160, 169, 170, 1000]) {
    assert.deepEqual(replay.sample(x), influenced.sample(x));
  }
  const checkpointSnapshot = influenced.tail(160);
  const checkpoint = restoreInfluence(JSON.parse(JSON.stringify(checkpointSnapshot)), V.restore);
  assert.deepEqual(checkpoint.domain, [160, root.domain[1]]);
  for (const x of [160, 165, 169, 170, 1000]) {
    assert.deepEqual(checkpoint.sample(x), influenced.sample(x));
  }
  assert.throws(() => checkpoint.sample(159));
  assert.throws(() => checkpoint.at(159, root.config.latent));
  assert.throws(() => checkpoint.location(159));
  assert.throws(() => checkpoint.inspect(159));
});

test('tails retain scheduled influences and remove completed window recipes', () => {
  const root = V.create();
  const future = root.influence(V.polynomialGuide([25], {domain: [120, 170]}), {
    at: 120, amount: 1, handoffWidth: 10, until: 150, releaseWidth: 20
  });
  const beforeActivation = restoreInfluence(JSON.parse(JSON.stringify(future.tail(110))), V.restore);
  assert.deepEqual(beforeActivation.sample(110), root.sample(110));
  assert.deepEqual(beforeActivation.sample(160), future.sample(160));
  const afterRelease = future.tail(170);
  assert.equal(afterRelease.algorithm, 'vixen-tail-c2/2');
});

test('invalid controls and malformed snapshots fail closed', () => {
  const root = V.create();
  const guide = V.polynomialGuide([3], {domain: [120, 300]});
  for (const options of [
    {at: null},
    {amount: null},
    {handoffWidth: null},
    {until: null},
    {releaseWidth: null},
    {reference: null},
    {until: 150},
    {releaseWidth: 40, until: 290},
    {reference: 'other'}
  ]) {
    assert.throws(() => root.influence(guide, {at: 120, ...options}));
  }
  const valid = root.influence(guide, {at: 120, amount: .7, handoffWidth: 10});
  const snapshot = JSON.parse(JSON.stringify(valid.snapshot()));
  assert.throws(() => restoreInfluence({...snapshot, amount: null}, V.restore));
  assert.throws(() => restoreInfluence({...snapshot, extra: true}, V.restore));
  const cycle = {...snapshot};
  cycle.curve = cycle;
  assert.throws(() => restoreInfluence(cycle, V.restore));
});

test('restoration has no fixed composition-depth limit', () => {
  const root = V.create();
  const guide = V.polynomialGuide([5], {domain: [120, 500]});
  let composed = root;
  for (let i = 0; i < 40; i++) {
    composed = composed.influence(guide, {at: 120, amount: .1, handoffWidth: 10});
  }
  const replay = restoreInfluence(JSON.parse(JSON.stringify(composed.snapshot())), V.restore);
  assert.deepEqual(replay.sample(150), composed.sample(150));
});
