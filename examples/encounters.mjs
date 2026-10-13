import {create} from '@worflor/vixen';
import {pathToFileURL} from 'node:url';

// Illustrative tuning, not a balanced game or a random event draw.
export function encounterSettings(sample) {
  const a = sample.activity;
  return {
    spawnChance: 0.02 + 0.18 * a,
    enemyBudget: Math.round(2 + 6 * a),
    speedMultiplier: 0.9 + 0.2 * a,
  };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const curve = create({seed: 'encounter-demo', stabilizer: 0.5});
  for (const x of [50, 100, 144, 200, 300]) {
    console.log(JSON.stringify({x, ...encounterSettings(curve.sample(x))}));
  }
}
