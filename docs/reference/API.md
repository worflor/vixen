# API

## Configuration

Defaults and accepted ranges:

- `seed`: text (up to 1024 UTF-16 code units), or uint32 numeric seed; default `vixen`.
- `openingEndpoint`: [2,50000], default 100. Rescales the authored clock and moves the opening endpoint.
- `introVariation`: [0,1], default 0. Seeded center, width and depth variation in the two intro dips.
- `stabilizer`: 0–1; default 0. Decimals are preserved.
- `strength`: 0–2; default 1.
- `ramp`: 1–64; default 16. Entrance width after `openingEndpoint`, in caller units.
- `tempo`: 0.25–4; default 0.8.
- `contrast`: 0–16; default 10. Applies to the fine field, not the backbone.
- `latent`: exactly 16 finite numbers in [-128,128]; default zeros.
- `velocity`: exactly 16 finite numbers in [-4,4]; default zeros.

Unknown configuration keys and invalid values are rejected. Numeric 42 and text "42" select distinct landscapes.

## JavaScript

`create(config)` returns an immutable instrument:

- `sample(x)`: evaluate the configured path.
- `at(x, h)`: evaluate explicit 16-D coordinates, ignoring the configured path at that point. Coordinates must be finite in [-1e6,1e6].
- `location(x)`: the configured path position. With canonical clock t=1+(x-1)*99/(openingEndpoint-1), h=latent+max(0,t-100)*velocity/100.
- `inspect(x)`: fine and backbone ridge contributions before nonlinear transforms, sorted by absolute fine contribution. These do not sum directly to final deviation.
- `ridges()`: immutable feature definitions.
- `config`, `algorithm`, `bounds`: latest-leaf configuration and conservative metadata.
- `bounds.unconstrainedSpatialSlope`: conservative Euclidean spatial sensitivity bound for unrestricted deviation. [Derivation and scope](CONDITIONING.md).
- `domain`: actual caller-coordinate limits; short openings can reduce the upper limit.
- `dips`: the two active Gaussian parameter triples in canonical units.
- `fork({at, operation, handoffWidth?})`: a new immutable timeline with exact inherited history and a C2 handoff.
- `influence(guideOrCallback, options)`: attach an external scalar guide while preserving history.
- `influences`, `influenceAt(x)`: guided-timeline recipes and following diagnostics. See [function guides](GUIDES.md).
- `snapshot()`: complete replay construction, including branches and nested influence data.
- `lineage`: read-only decision and derived-seed metadata. See [timing and branch semantics](TIMELINES.md).

`x` must be finite inside `domain` (default [1,50000]). Samples contain `value`, `base`, `score`, `guideScore`, `rawDeviation`, `guideDeviation`, `guideValue`, `stabilizationWeight`, `deviation`, `multiplier`, `activity`, `positiveMultiplier`, `positiveValue`, `gate`, and `x`.

`restore(snapshot)` supports core timelines, nested guided compositions and valid historical hyperfield `/1`, `/2` and `/3` schemas. Newer fields cannot be retagged as old snapshots. Save `snapshot()`, not just `config`, to preserve a fork history. Unrelated algorithm identifiers are rejected.

Guide exports are `polynomialGuide`, `taylorGuide`, `sampledGuide`, `captureGuide`, `compileGuide` and `evaluateGuide`, with snake_case Python counterparts. [Guide representations and domains](GUIDES.md).

Construction exports include `INFLUENCE_ALGORITHM`, `boundaryRamp` (Python `boundary_ramp`), `ALGORITHM`, `DEFAULTS`, `DIMENSIONS`, `FEATURES`, `seed32`, `hash32`, `baseline`, `smooth5`, `bump`, and `navigationBasis`. Low-level helpers are construction tools; use the sampler's domain validation for game inputs.

## Python

`Vixen(**config)` provides `sample`, `at`, `location`, `inspect`, and `ridges`. Public instances and their configuration are immutable by the supported API; underscore-prefixed implementation objects (including `_leaf`) are not a hard security sandbox and must not be mutated. `config` and `bounds` are read-only mapping views, with tuple vectors; they are not callable and are not plain JSON dictionaries. Construct a new instance to change configuration. Samples are dictionaries. Python coordinates accept lists or tuples. Navigation is `navigation_basis(index)`.

Use the instance snapshot to select the appropriate replay schema; it supplies ordinary JSON data rather than requiring manual serialization of read-only views:

```python
import json
from vixen import ALGORITHM, Vixen, restore
curve = Vixen(seed="my-world", stabilizer=0.85)
snapshot = curve.snapshot()
replay = restore(json.loads(json.dumps(snapshot)))
```

## Stabilization

Let F0 be the existing curve, T the seeded broad backbone, and w=S(s)=6s^5-15s^4+10s^3. Then Fs=(1-w)*F0+w*T.

T uses the same counter-addressed ridge field at 32-times-broader coordinates, with gentle bounded modulation. At each point, distance to T contracts by 1-w. This is not a claim that distance to the unmodulated baseline decreases monotonically, or that every possible roughness metric decreases for every configuration.

At full stabilization and maximum strength, the target lies conservatively between 0.5 and 1.5 times the positive authored baseline. Both endpoints preserve the configured authored opening. The exact-real construction is C2; floating evaluations have roundoff.

## Boundary policy configuration

`floor`: none (default), zero, baseline, backbone. `ceilingMargin`: null (default) or finite [0,16], in baseline units above the chosen trajectory. `boundarySoftness`: finite [0,1], default 0.1. The upper anchor is baseline for baseline floor, original backbone otherwise. [Full boundary semantics](CONSTRAINTS.md).

Samples also contain unconstrainedValue, unconstrainedDeviation, unconstrainedGuideValue, unconstrainedGuideDeviation, nullable floorValue/ceilingValue, boundaryWidth, and constraintApplied. The guideValue/guideDeviation pair describes the constrained guide. Existing game adapters derive from constrained deviation. `boundaryRamp(t)` / Python `boundary_ramp(t)` expose the validated finite-coordinate C2 join (zero below 0, quintic in [0,1], identity above 1).

## Coherent and extended modes

`createCoherent` selects `/5`; `createExtended` selects `/6` rather than changing the historical constructor. Python exposes matching snake-case factories. Java provides `Vixen.createCoherent` and `Vixen.createExtended`. See [extended signals](EXTENDED.md) and [Java integration](JAVA.md) for their representation and capability contracts.

## Replay schemas

`vixen-hyperfield-c2/4` describes the core and fork lineage. `vixen-influence-c2/1` describes a guided composition containing its underlying snapshot, numeric guide data and attachment controls. Valid historical `/1`, `/2` and `/3` schemas remain supported; inconsistent fields and unrelated identifiers are rejected. Save the whole snapshot, not a manually retagged configuration. Constraints remain disabled by default.

Coherent native snapshots use `vixen-hyperfield-c2/5` and `vixen-tail-c2/3`. Extended native snapshots use `vixen-hyperfield-c2/6` and `vixen-tail-c2/4`. Windowed, relative and additive influence use explicit influence versions through `/3`. Finite operations use `vixen-operation-c2/1`; scaled operations and coupling use `vixen-extended-operations/1` and `vixen-extended-coupling/1`. Deep/shared compositions use `vixen-graph/1` with validated backward references. The schema identifies semantics, not merely an interchangeable tag.
