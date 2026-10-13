import test from 'node:test';
import assert from 'node:assert/strict';
import {create} from '../vixen.mjs';
import {encounterSettings} from '../examples/encounters.mjs';
import {orbitSample} from '../examples/orbit.mjs';

test('encounter adapter stays bounded even when raw progression is negative', () => {
  const curve = create({seed: 'vixen', stabilizer: 0});
  let negatives = 0;
  for (let x = 1; x <= 600; x++) {
    const sample = curve.sample(x), settings = encounterSettings(sample);
    if (sample.value < 0) negatives++;
    assert.ok(settings.spawnChance >= 0.02 && settings.spawnChance <= 0.2);
    assert.ok(Number.isInteger(settings.enemyBudget));
    assert.ok(settings.enemyBudget >= 2 && settings.enemyBudget <= 8);
    assert.ok(settings.speedMultiplier >= 0.9 && settings.speedMultiplier <= 1.1);
  }
  assert.ok(negatives > 0, 'Exercise a real negative raw curve, not only benign fixtures');
});

test('coordinate orbit closes without rerolling the seed or mutating configuration', () => {
  const curve = create({seed: 'orbit-demo', stabilizer: 0.35});
  const before = JSON.stringify(curve.config);
  const first = orbitSample(curve, 0), last = orbitSample(curve, 2 * Math.PI);
  assert.ok(Math.abs(first.value - last.value) < 1e-8);
  assert.notEqual(first.value, orbitSample(curve, Math.PI).value);
  assert.equal(JSON.stringify(curve.config), before);
  assert.throws(() => orbitSample(curve, NaN));
});
