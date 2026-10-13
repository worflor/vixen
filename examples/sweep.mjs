import {create} from '@worflor/vixen';

// node examples/sweep.mjs TEXT_SEED STABILIZER MAX_X STEP > samples.csv
const [seed = 'vixen', s = '0', end = '300', increment = '0.25'] = process.argv.slice(2);
const max = Number(end), step = Number(increment);
if (!Number.isFinite(max) || max < 1 || max > 50000 || !Number.isFinite(step) || step <= 0) {
  throw new RangeError('MAX_X must be in [1,50000] and STEP must be positive and finite');
}
const count = Math.floor((max - 1) / step) + 1;
if (count > 100000) throw new RangeError('Choose a larger STEP: at most 100000 rows per sweep');
const curve = create({seed, stabilizer: Number(s)});
const keys = ['x', 'value', 'guideValue', 'deviation', 'activity', 'positiveMultiplier'];
console.log(keys.join(','));
for (let i = 0; i < count; i++) {
  const sample = curve.sample(Math.min(max, 1 + i * step));
  console.log(keys.map(key => sample[key]).join(','));
}
