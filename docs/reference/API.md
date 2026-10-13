# API

## Configuration

Defaults and accepted ranges:

- `seed`: text (up to 1024 UTF-16 code units), or uint32 numeric seed; default `vixen`.
- `stabilizer`: 0–1; default 0. Decimals are preserved.
- `strength`: 0–2; default 1.
- `ramp`: 1–64; default 16. Entrance width after x=100.
- `tempo`: 0.25–4; default 0.8.
- `contrast`: 0–16; default 10. Applies to the fine field, not the backbone.
- `latent`: exactly 16 finite numbers in [-128,128]; default zeros.
- `velocity`: exactly 16 finite numbers in [-4,4]; default zeros.

Unknown configuration keys and invalid values are rejected. Numeric 42 and text "42" select distinct landscapes.

## JavaScript

`create(config)` returns an immutable instrument:

- `sample(x)`: evaluate the configured path.
- `at(x, h)`: evaluate explicit 16-D coordinates, ignoring the configured path at that point. Coordinates must be finite in [-1e6,1e6].
- `location(x)`: the configured path position. After x=100, h=latent+(x-100)*velocity/100.
- `inspect(x)`: fine and backbone ridge contributions before nonlinear transforms, sorted by absolute fine contribution. These do not sum directly to final deviation.
- `ridges()`: immutable feature definitions.
- `config`, `algorithm`, `bounds`: replay and conservative output metadata.

`x` must be finite in [1,50000]. Samples contain `value`, `base`, `score`, `guideScore`, `rawDeviation`, `guideDeviation`, `guideValue`, `stabilizationWeight`, `deviation`, `multiplier`, `activity`, `positiveMultiplier`, `positiveValue`, `gate`, and `x`.

`restore({algorithm, config})` supports the current algorithm and prior hyperfield /1 snapshots without a stabilizer field. Unrelated algorithm identifiers are rejected.

Exports also include `ALGORITHM`, `DEFAULTS`, `DIMENSIONS`, `FEATURES`, `seed32`, `hash32`, `baseline`, `smooth5`, `bump`, and `navigationBasis`. Low-level helpers are construction tools; use the sampler's domain validation for game inputs.

## Python

`Vixen(**config)` provides `sample`, `at`, `location`, `inspect`, and `ridges`. `config` and `bounds` are read-only mapping views, with tuple vectors; they are not callable and are not plain JSON dictionaries. Construct a new instance to change configuration. Samples are dictionaries. Python coordinates accept lists or tuples. Navigation is `navigation_basis(index)`.

Python uses the module-level algorithm identifier, and converts the configuration view before JSON serialization:

```python
import json
from vixen import ALGORITHM, Vixen, restore
curve = Vixen(seed="my-world", stabilizer=0.85)
snapshot = {"algorithm": ALGORITHM, "config": dict(curve.config)}
replay = restore(json.loads(json.dumps(snapshot)))
```

## Stabilization

Let F0 be the existing curve, T the seeded broad backbone, and w=S(s)=6s^5-15s^4+10s^3. Then Fs=(1-w)*F0+w*T.

T uses the same counter-addressed ridge field at 32-times-broader coordinates, with gentle bounded modulation. At each point, distance to T contracts by 1-w. This is not a claim that distance to the unmodulated baseline decreases monotonically, or that every possible roughness metric decreases for every configuration.

At full stabilization and maximum strength, the target lies conservatively between 0.5 and 1.5 times the positive authored baseline. Both endpoints preserve x<=100. The exact-real construction is C2; floating evaluations have roundoff.
