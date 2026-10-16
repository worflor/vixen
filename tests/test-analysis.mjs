import test from 'node:test';
import assert from 'node:assert/strict';
import {nextCrossing, makeLipschitzEnclosure, calibrateOccupancy, validateOccupancy, restoreOccupancy, validateCorrelationMatrix, createCorrelatedChannels, measureChannelCorrelation} from '../vixen-analysis.mjs';

function polynomial([c, b, a], domain = [0, 1]) {
  const evaluate = x => c + b * x + a * x * x;
  return {
    domain,
    sample: x => ({deviation: evaluate(x)}),
    encloseDeviation(lo, hi) {
      const values = [evaluate(lo), evaluate(hi)];
      if (a !== 0) {
        const vertex = -b / (2 * a);
        if (vertex > lo && vertex < hi) values.push(evaluate(vertex));
      }
      return [Math.min(...values) - 1e-13, Math.max(...values) + 1e-13];
    }
  };
}

test('crossing encloses a sign change and returns a tolerance-sized first interval', () => {
  const result = nextCrossing(polynomial([-0.25, 1, 0]), 0, 0, 1, {continuous: true, tolerance: 1e-5});
  assert.equal(result.status, 'found');
  assert.ok(result.interval[1] - result.interval[0] <= 1e-5);
  assert.ok(result.interval[0] <= 0.25 && result.interval[1] >= 0.25);
});

test('first crossing is not later than a subsequent polynomial crossing', () => {
  const result = nextCrossing(polynomial([0.1875, -1, 1]), 0, 0, 1, {continuous: true, tolerance: 1e-5});
  assert.equal(result.status, 'found');
  assert.ok(result.interval[0] < 0.5);
});

test('a narrow polynomial bump is found without relying on a fixed grid', () => {
  const result = nextCrossing(polynomial([-0.039999, 0.4, -1]), 0, 0, 1, {continuous: true, tolerance: 1e-5, maxIntervals: 10000});
  assert.equal(result.status, 'found');
  assert.ok(result.interval[0] < 0.2);
});

test('tangency stays unresolved and an enclosed root-free polynomial is absent', () => {
  const tangent = nextCrossing(polynomial([0.25, -1, 1]), 0, 0, 1, {continuous: true, tolerance: 1e-4});
  assert.equal(tangent.status, 'unresolved');
  const absent = nextCrossing(polynomial([0.35, -1, 1]), 0, 0, 1);
  assert.equal(absent.status, 'absent');
  assert.deepEqual(absent.unresolved, []);
});

test('missing, malformed and budget-limited enclosures fail closed', () => {
  const noOracle = nextCrossing(x => x, 0.5, 0, 1, {domain: [0, 1]});
  assert.equal(noOracle.reason, 'enclosure-unavailable');
  assert.throws(() => nextCrossing({domain: [0, 1], sample: x => x, enclose: () => [1, -1]}, 0, 0, 1));
  const limited = nextCrossing(polynomial([-0.25, 1, 0]), 0, 0, 1, {continuous: true, maxIntervals: 1});
  assert.equal(limited.status, 'unresolved');
  assert.ok(limited.unresolved.length > 0);
  const lip=makeLipschitzEnclosure(x=>x,1)(0,1);assert.ok(lip[0]<=0&&lip[1]>=1);
});

test('occupancy uses a declared empirical cutoff, handles ties and replays its recipe', () => {
  const calibration = calibrateOccupancy([1, 2, 3, 4, 5, 6, 7, 8, 9, 10], {targetFraction: 0.2, outputThreshold: 0.2});
  assert.equal(calibration.achievedFraction, 0.2);
  assert.ok(calibration.evaluate(9) >= 0.2);
  assert.ok(calibration.evaluate(8) < 0.2);
  const values = [-2, -1, 0, 1, 2];
  for (let i = 1; i < values.length; i++) assert.ok(calibration.evaluate(values[i]) > calibration.evaluate(values[i - 1]));
  const replay = restoreOccupancy(calibration.serialize());
  assert.equal(replay.evaluate(4), calibration.evaluate(4));
  const tied = calibrateOccupancy([1, 2, 2, 2, 3], {targetFraction: 0.4, outputThreshold: 0.5});
  assert.ok(tied.achievedFraction === 0.2 || tied.achievedFraction === 0.8);
  assert.notEqual(tied.achievedFraction, 0.4);
  const heldout = validateOccupancy(calibration, [0, 10, 11]);
  assert.equal(heldout.totalPopulation, 3);
  assert.equal(heldout.achievedFraction, 2 / 3);
});

test('reported occupancy measures the actual mapped output, including rounded near-ties',()=>{
 const population=[0,1e-20,1];const c=calibrateOccupancy(population,{targetFraction:2/3,outputThreshold:0.8});
 const actual=population.filter(x=>c.evaluate(x)>=0.8).length/population.length;
 assert.equal(c.achievedFraction,actual);assert.equal(validateOccupancy(c,population).achievedFraction,actual);
});

test('occupancy supports endpoint targets when a finite cutoff exists and rejects bad numerics', () => {
  assert.equal(calibrateOccupancy([1, 2], {targetFraction: 1, outputThreshold: 0.5}).achievedFraction, 1);
  assert.equal(calibrateOccupancy([1, 2], {targetFraction: 0, outputThreshold: 0.5}).achievedFraction, 0);
  assert.throws(() => calibrateOccupancy([Number.MAX_VALUE], {targetFraction: 0, outputThreshold: 0.5}));
  assert.throws(() => calibrateOccupancy([true], {targetFraction: 0.5, outputThreshold: 0.5}));
});

const sourceFactory = seed => {
  const phase = (seed % 997) / 997;
  const value = x => Math.sin(2 * Math.PI * (x + phase));
  return {domain: [0, 1], sample: x => ({score: value(x), unconstrainedDeviation: value(x)}), at: x => ({score: value(x), unconstrainedDeviation: value(x)})};
};

test('correlation validation accepts singular PSD matrices and rejects indefinite ones', () => {
  const singular = validateCorrelationMatrix([[1, -1], [-1, 1]]);
  assert.equal(singular.factor.length, 2);
  assert.throws(() => validateCorrelationMatrix([[1, 1.2], [1.2, 1]]));
});

test('correlated channels replay seeds, preserve negative relation, and support three channels', () => {
  const negative = createCorrelatedChannels([[1, -1], [-1, 1]], sourceFactory, 'root-seed');
  const replay = createCorrelatedChannels(negative.serialize().matrix, sourceFactory, 'root-seed');
  assert.deepEqual(negative.sourceSeeds, replay.sourceSeeds);
  const sample = negative.sample(0.31);
  assert.equal(sample.length, 2);
  assert.ok(Math.abs(sample[0] + sample[1]) < 1e-12);
  const measured = measureChannelCorrelation(negative, Array.from({length: 30}, (_, i) => i / 30));
  assert.ok(measured[0][1] < -0.999999);
  const three = createCorrelatedChannels([[1, 0, 0], [0, 1, 0], [0, 0, 1]], sourceFactory, 42);
  assert.equal(three.at(0.2, Array(16).fill(0)).length, 3);
  assert.throws(() => measureChannelCorrelation({sample: () => [1, 1]}, [0, 1]));
});

test('Lipschitz enclosures round outward at adjacent representable coordinates',()=>{
 const a=1,b=1+Number.EPSILON,q=makeLipschitzEnclosure(x=>x,1)(a,b);
 assert.ok(q[0]<=a&&q[1]>=b,JSON.stringify(q));
});
