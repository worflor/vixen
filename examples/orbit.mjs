import {create} from '@worflor/vixen';
import {pathToFileURL} from 'node:url';

// Keep progression fixed and move on a smooth, closed coordinate path.
// Time is in radians here. Sampling rate and rendering belong to the host.
export function orbitSample(curve, time, progression = 144) {
  if (!Number.isFinite(time)) throw new RangeError('Time must be finite');
  const h = curve.config.latent.slice();
  h[0] += 2 * Math.cos(time);
  h[1] += 2 * Math.sin(time);
  return curve.at(progression, h);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const curve = create({seed: 'orbit-demo', stabilizer: 0.35});
  for (const time of [0, Math.PI / 2, Math.PI, 3 * Math.PI / 2, 2 * Math.PI]) {
    const sample = orbitSample(curve, time);
    console.log(JSON.stringify({time, activity: sample.activity,
      lightBrightness: 0.35 + 0.5 * sample.activity}));
  }
}
