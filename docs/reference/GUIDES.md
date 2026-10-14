# Function guides and influence

An influence pulls a timeline's unrestricted value toward an authored scalar guide. It does not interpret a function's meaning or steer latent coordinates. Guides use the caller's progression coordinate `x`.

## Input forms

```js
import {create, polynomialGuide, taylorGuide, captureGuide} from '@worflor/vixen';

const line = polynomialGuide([3, 2]); // y = 2*x + 3
const series = taylorGuide([1, 1, 1, 1], {center: 200});
const procedural = captureGuide(
  x => 80*Math.sin(x/23) + .3*x,
  {domain: [100, 500], count: 129}
);
const root = create({seed: 'world'});
const guided = root.influence(procedural, {
  at: 120, amount: .7, handoffWidth: 24
});
const future = guided.fork({at: 180, operation: 'take-key'});
```

```python
import math, json
from vixen import Vixen, polynomial_guide, taylor_guide, capture_guide, restore
line = polynomial_guide([3, 2])
series = taylor_guide([1, 1, 1, 1], center=200)
guide = capture_guide(lambda x: 80*math.sin(x/23)+.3*x,
                      domain=[100, 500], count=129)
f = Vixen(seed='world').influence(guide, at=120, amount=.7, handoffWidth=24)
replay = restore(json.loads(json.dumps(f.snapshot())))
```

- `polynomialGuide`: ascending coefficients of powers of `(x-center)/scale`. This directly represents the finite polynomial, without fitting a sampled approximation. Evaluation uses floats, not exact rational arithmetic.
- `taylorGuide`: derivative values at `center`, divided by their factorials to form a finite Taylor polynomial. It does not certify convergence or truncation error of the original function.
- `sampledGuide`: ordered `{x,y,d1,d2}` knots. A quintic Hermite segment shares each knot's value, first derivative and second derivative with its neighbors.
- `captureGuide`: evaluates a trusted callback at ordered knots once. Optional `derivative` and `curvature` callbacks supply jets; otherwise local quadratic divided differences estimate them. Any finite numeric function can be captured, including a discontinuous source, but the resulting C2 guide is an approximation. It may overshoot and may miss behavior between knots.
- `compileGuide` validates and freezes a guide once, returning `spec`, `domain` and `evaluate(x)`. `evaluateGuide` is a convenient one-off evaluation that compiles each call.

Python uses the corresponding snake_case names and read-only mapping/tuple views. Save guides through timeline snapshots for ordinary JSON data.

## Composition

For attachment coordinate `at`, handoff width `H`, amount `a` and quintic gate `S`, the active weight is `a*S((x-at)/H)`. The unrestricted result is a convex blend of the current unrestricted value and the guide. Amount zero and every coordinate through `at` return the parent sample directly. Full influence assigns the target directly after handoff, avoiding catastrophic cancellation when source and target scales differ.

Output policies apply **after** the pull. A floor or ceiling may prevent exact following even at amount one. Soft transition width incorporates external activation independently of native strength, retaining the inherited width at subsequent attachments and respecting the available band. Hard policies may introduce corners. C2 claims describe valid compositions in exact arithmetic, not a floating-point lockstep contract.

The authored positive baseline and seeded backbone remain reference channels. `score`, `rawDeviation`, `inspect` and ridge data describe the underlying field; they are not an additive explanation of the foreign target. `influenceAt(x)` reports the last influence's weight, target value, source value and actual following error. Stabilization and influence are separate controls.

Later forks retain the guide while deriving another seed-specific future. Further influences layer in order and preserve the actual parent prefix. At full influence, different seeds can produce the same target curve; distinct operation records do not guarantee distinct scalar futures.

## Replay and domains

A guided snapshot uses `vixen-influence-c2/1` and contains the underlying timeline snapshot, frozen guide data and attachment controls. Root `/4` snapshots and numerical behavior are unchanged. Replay never invokes a callback or executes stored source code. Save the **whole snapshot**, including nested influences.

The effective upper domain is the smaller of the parent and guide upper endpoints. The guide must cover the attachment and handoff; earlier history remains available. Queries beyond the effective domain are rejected, not extrapolated. Attachment defaults to the latest recorded position or opening endpoint, amount to .5 and width to at most 16 within the domain.

Finite input limits protect replay: 1–65 polynomial coefficients, 2–2049 strictly increasing knots, finite numbers with magnitude at most 1e250, guide coordinates in [-1e6,1e6], and at most 32 combined forks/influences. Nonrepresentable capture grids and nonfinite evaluations are rejected. Compilation checks conservative absolute-coefficient bounds over the entire guide domain; cancellation-heavy forms may require recentering, rescaling or smaller segments. Callback capture is trusted local developer code, not a sandbox; bounded call count does not preempt an expensive or nonterminating callback.

The sensitivity bound remains conservative for unrestricted spatial deviation: a scalar guide adds no latent dependence, and the convex pull cannot increase that field sensitivity. Guided output envelopes are deliberately broad unless an explicit output policy supplies a tighter bound.

[Component derivations](../../research/influence-evidence.json) distinguish exact Hermite coefficient relations, positivity certificates, a multivariable refusal and numerical implementation tests. They do not certify arbitrary callback fidelity or the whole sampler.
