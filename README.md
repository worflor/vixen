# Vixen

**A seeded landscape with teeth.** Deterministic progression curves for JavaScript and Python, with a navigable 16-dimensional field, trajectory stabilization, output boundaries and immutable timeline branches.

![Six seeds with fixed axes and three stabilization settings](docs/media/seed-gallery.gif)

## Purpose

An exponential gives predictable growth. Independent rolls give discontinuous variation. Periodic modulation can put every seed on the same timetable. Vixen combines an authored opening with smooth, counter-addressed variation and a seed-specific broad backbone.

The seed selects a landscape; coordinates select a location; velocity selects a path. Stabilization controls attraction to the backbone rather than reducing all variation toward a generic curve. Evaluation is random-access and requires no mutable RNG state.

Useful applications include encounter intensity, difficulty experiments, procedural families, environmental controls and reproducible stress fixtures. Host projects own calibration, rewards and decisions. Vixen is not cryptographic randomness, a physics simulator, a universal noise framework or a band-limited audio generator.

## Explore

Open [instrument/vixen.html](instrument/vixen.html) directly in a browser. Adjust the seed, opening, stabilization and bounds; explore the coordinate map; inspect a position and fork it. Export the complete replay snapshot or actual sampled CSV.

Root controls lock after branching to prevent edits from erasing history. Display controls remain available. Reset starts a new root.

## Install

Authenticate for private Git access.

```sh
npm install git+https://github.com/worflor/vixen.git
python -m pip install "vixen-curves @ git+https://github.com/worflor/vixen.git"
```

```js
import {create, restore} from '@worflor/vixen';
const curve = create({seed: 'my-world', stabilizer: 0.85});
const {activity, positiveMultiplier} = curve.sample(300);
```

```python
from vixen import Vixen, restore
curve = Vixen(seed="my-world", stabilizer=0.85)
activity = curve.sample(300)["activity"]
```

Alternatively vendor [vixen.mjs](vixen.mjs) and [its declarations](vixen.d.ts), or [vixen.py](vixen.py). ESM/Node 20+ or modern browsers; Python 3.10+. Both runtimes are dependency-free. No AI model, Scryer connection, server or viewer is required.

## Choose an output

- `activity`: bounded control signal in [0,1], not a calibrated probability.
- `positiveMultiplier`: bounded positive tuning factor.
- `positiveValue`: positive authored growth scaled by that factor.
- `value`: signed progression, potentially negative and very large.

Every adapter derives from the actual constrained signal. Unconstrained diagnostics remain available. Floating curves use declared tolerances across numeric backends; hashes and branch-seed derivation match exactly across ports.

## Configure the object

```js
const root = create({
  seed: 'my-world',
  openingEndpoint: 160,
  introVariation: 1,
  stabilizer: 0.3,
  floor: 'backbone',
  ceilingMargin: 0.35,
  boundarySoftness: 0.1
});
```

`openingEndpoint` rescales the progression clock. `introVariation` varies the two opening dips' centers, widths and depths; zero selects the fixed authored opening. Read `root.domain` before sampling: a shortened opening reduces the supported caller domain.

Floors are `none`, `zero`, `baseline` and `backbone`. The nullable ceiling margin is measured in baseline units. Positive softness supplies a smooth boundary transition; zero selects hard clipping. Limits are never permission to cross a boundary. A collapsed trajectory band intentionally removes variation. Full stabilization need not be strictly monotonic.

## Branch and replay

```js
const door = root.fork({at: 240, operation: 'opened-left-door', handoffWidth: 24});
const key = door.fork({at: 320, operation: 'took-key'});
const replay = restore(JSON.parse(JSON.stringify(key.snapshot())));
```

A fork preserves its actual parent's history and world coordinates through the decision point. The handoff matches value, slope and curvature wherever the parent curves are C2, then approaches a decision-derived future. Narrow handoffs can still produce large derivatives. Hard clipping can still introduce corners.

The operation is seed material, not an interpreted physical command. Finite hashes can collide. Nested branches inherit their parent's history, not an independently reconstructed root.

Save `snapshot()`, which contains root configuration and ordered lineage. `config` describes the latest future leaf and is insufficient to reconstruct history.

## Develop

```sh
npm ci
npm test
npm run typecheck
python -m unittest discover -s tests -p 'test_python*.py'
python tests/cross_port.py
node tests/fuzz.mjs
node tools/benchmark.mjs
python tools/build_instrument.py
```

Examples: [encounters](examples/encounters.mjs), [coordinate orbit](examples/orbit.mjs), [sweeps](examples/sweep.mjs), [constraints](examples/constraints.mjs), [timelines](examples/timelines.mjs).

## Function influence

A line, finite Taylor polynomial, supplied derivative jets or a captured procedural function can guide the same object. Attachment preserves history; later forks retain the guide. Capture stores a smooth approximation, never executable callback source. [Guide API and mathematical scope](docs/reference/GUIDES.md).

```js
import {create, captureGuide} from "@worflor/vixen";
const root = create({seed:"world"});
const guide = captureGuide(x => 80*Math.sin(x/23)+.3*x,
  {domain:[100,500], count:129});
const guided = root.influence(guide, {at:120, amount:.7, handoffWidth:24});
```

## Reference

- [Function guides](docs/reference/GUIDES.md)
- [API reference](docs/reference/API.md)
- [Construction](docs/reference/CONSTRUCTION.md)
- [Boundary policies](docs/reference/CONSTRAINTS.md)
- [Timing and timelines](docs/reference/TIMELINES.md)
- [Conditioning](docs/reference/CONDITIONING.md)
- [Technical design](docs/TDD.md), [research design](docs/RDD.md), [repository map](AGENTS.md)

Scryer supplies exact component findings and replayable certificates where available. Those results, executable regressions and sampled audits have separate scopes. No whole-program certificate or proof of dynamical chaos is claimed. Compatibility belongs to the replay contract; historical observations remain in `research/history/`.

Licensed under [MIT](LICENSE). npm-registry publication is disabled by `private: true`.
