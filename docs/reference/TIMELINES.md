# Timing, seeded openings and timelines

Timing and immutable branches are part of both runtime objects.

## Translate the opening

`openingEndpoint` is the real progression coordinate at which the authored opening ends. Default 100; finite range [2,50000]. The canonical clock is:

```text
t(x) = 1 + (x-1)*99/(openingEndpoint-1)
```

At the default endpoint, the clock is exactly `t=x`. Consequently the two dips, baseline growth, field timing and configured travel all stretch with the opening. This is time reparameterization, not an additive offset or a new output scale. `ramp` remains in caller progression units: the chaos gate opens from `openingEndpoint` to `openingEndpoint+ramp`.

`domain` is `[1, min(50000, 1+49999*(openingEndpoint-1)/99)]`. It keeps both the caller coordinate and canonical clock inside their supported domains. Short openings therefore reduce the maximum accepted caller coordinate. Inspect `domain` rather than assuming every timing configuration accepts 50000. An opening at the domain end may leave no usable continuation.

## Seed the two dips

`introVariation` is [0,1], default 0. It interpolates dip **parameters**, not whole curves, so halfway does not mean stacking four notches. Center, squared width and depth move toward separately counter-addressed values derived from the typed seed:

- First center: [32,47]; width: [4,6]; depth: [18,25].
- Second center: [62,82]; width: [4,7]; depth: [35,48].

These are canonical-clock ranges, including the fixed authored parameters. `dips` exposes the actual parameter triples. Each dip is Gaussian `depth*exp(-((t-center)/width)^2)` subtracted from `t*exp(t/100)`. The varied opening returns to the unchanged authored baseline using a quintic fade on canonical [90,100]. At and beyond 100 it is exactly the fixed authored baseline. The seed controls bounded variability, not cryptographic unpredictability or a proof that seeds are statistically independent.

### Why the opening stays positive

All allowed dip parameters obey the boxes above. Monotonicity of `exp` and `exp(z)>=1+z` for nonnegative `z` give a finite decomposition:

- t in [1,20]: Gaussian losses <25/32+48/256; growth >=t.
- t in [20,26]: losses <25/2+48/256; growth >=t.
- t in [26,49]: losses <25+48/8; growth >=t*(1+t/100).
- t in [49,50000]: losses <=73; growth >=t*(1+t/100).

Scryer confirmed all four polynomial lower bounds are positive and certified `exp(-4)<1/32`, `exp(-8)<1/256`, `exp(-1)<1/2`, `exp(-3)<1/8` by rigorous rational enclosures. The relevant first/second Gaussian exponents are at least 4/8, 1/8, and 0/3 in the first three boxes. The unchanged opening belongs to the same parameter bounds, so its convex fade with the varied opening remains positive. This is an explicit composition argument, not a whole-program certificate.

## Fork a future

```js
import {create, restore} from '@worflor/vixen';
const root = create({seed:'my-world', openingEndpoint:160, introVariation:1});
const door = root.fork({at:240, operation:'opened-the-left-door', handoffWidth:24});
const key = door.fork({at:320, operation:'took-the-key'});
const replay = restore(JSON.parse(JSON.stringify(key.snapshot())));
// root and door remain unchanged. key inherits door's history, not root's.
```

```python
import json
from vixen import Vixen, restore
root = Vixen(seed='my-world', openingEndpoint=160, introVariation=1)
door = root.fork(at=240, operation='opened-the-left-door', handoffWidth=24)
replay = restore(json.loads(json.dumps(door.snapshot())))
```

`operation` is a nonempty string up to 1024 UTF-16 code units. It is a deterministic **decision token**, not an interpreted physical command. The parent leaf's typed seed, operation hash and exact IEEE-754 fork-coordinate bits derive the future seed through the existing uint32 hash. Equal decisions at equal positions on equal parents replay; different tokens usually differ, but finite uint32 hashes can collide. No collision-free or cryptographic guarantee is offered.

The current world coordinates, travel vector, timing and all control policies are inherited. For every `x<=at`, `sample` and `at` delegate directly to the actual parent: exact history preservation without resampling a stored table. `location(x)` remains the same configured path. In the handoff interval:

```text
u = (x-at)/handoffWidth
w = 10*u^3 - 15*u^4 + 6*u^5
Fbranch(x) = (1-w)*Fparent(x) + w*Ffuture(x)
```

The gate has zero first and second derivatives at both ends. It is `smooth5`, not the separate `boundaryRamp(t)=6t^3-8t^4+3t^5` floor/ceiling join; the two quintics have different jobs. Hence it matches parent position/value, slope and curvature at entry, and future value/slope/curvature at exit, wherever the parents are C2. It does not extrapolate or freeze the old slope indefinitely. Hard boundary policies can still introduce corners; a fork does not cure those.

Actual constrained values, baseline, guide, unconstrained diagnostics and admissible limits are blended. Every adapter is then derived from the actual branch value and baseline. Convexity preserves a convex blend of parent/future floors and ceilings. Thus a baseline or backbone floor during handoff refers to the **blended trajectory**, not solely the latest leaf. This also preserves positivity and the conservative relative envelopes. `inspect` supplies weighted ridge contributions during a handoff, tagged with `sourceSeed` so overlapping branches do not disguise distinct ridges behind identical indices; `ridges()` describes the latest future leaf, not a complete historical or composite ridge bank.

The handoff width defaults to 16, shortened only if needed to fit the domain. A width must be positive, representable and entirely inside `domain`; a fork at the domain endpoint is rejected. Nested forks cannot precede their parent's fork coordinate. Overlapping handoffs are supported. Maximum lineage depth is 32, explicitly bounded to keep untrusted replay and evaluation work finite.

## Save the timeline, not just its future seed

Always save `instrument.snapshot()` / `curve.snapshot()`. The snapshot contains algorithm, full **root** configuration and ordered decision lineage. `config` describes the latest future leaf; reconstructing that alone discards all branches and their history. `lineage` also exposes derived seed metadata; snapshots omit these redundant seeds and recompute them on replay.

Valid `/1`, `/2` and `/3` snapshots restore old numerical behavior, without retagging newer fields as old schemas. The exact `/3` source is retained in `legacy/v3-archive/`. New lineage fields are not accepted in old snapshots. Unknown snapshot/operation fields, malformed values and excessive depth are rejected.

## Evidence boundary

[Component evidence](../../research/timelines-evidence.json) records deployed Scryer findings, exact positivity certificates, rechecked claims and an explicit matrix-inverse refusal/wish. The test suite checks history, C2 numerical joins, constrained adapters, typed decisions, nested replay, valid domains and cross-port agreement. Sampled dip-count audits are not an all-seed extrema theorem. Float curves remain tolerance-based across libm implementations; only integer branch/hash derivation is exact across ports.
