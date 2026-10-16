# Analytic derivatives and normalized sampling

## Public finite-curve API

Finite native timelines, influences, impulses, redistribution and coupling projections expose `jet(x, coordinates?)`, `slope` and `curvature` in JavaScript and Python. Derivatives propagate analytically through the actual composition graph, not through finite-difference sampling. A supplied sixteen-number coordinate vector is held fixed; omitting it differentiates the configured moving path.

```js
import {createCoherent} from '@worflor/vixen';
const curve = createCoherent({seed: 'analytic'})
  .impulse(120, .1, 20)
  .fork({at: 160, operation: 'take-key', handoffWidth: 8});
const result = curve.jet(180);
const samples = curve.sampleRange(160, 220, 61);
```

`sampleRange` / `sample_range` accepts two available-domain endpoints and a count in `[2,100001]`, including both endpoints. It evaluates the actual curve rather than interpolating a stored table.

A public jet contains the primal `sample`, value slope and curvature, field jets, availability and reasons. Availability is order-specific: a finite slope can survive an overflowing curvature. Hard contacts or nonrepresentable derivatives are reported as unavailable instead of fabricated. The `score` branch at the opening endpoint has a separate diagnostic discontinuity; the entrance gate can still preserve value derivatives.

Java exposes the same finite analysis through `Curve.analyticJet(x, coordinates)` with order-specific availability. `Curve.jet` is a strict convenience form requiring both derivatives. See [Java integration](JAVA.md).

## Native leaf kernel

`vixen-calculus.mjs` and `vixen_calculus.py` supply the leaf kernel used by composition. `leafJet(curve, x, coordinates?)` / `leaf_jet(curve, x, coordinates=None)` accepts a native leaf, not a forked timeline. Calling it directly on a timeline is rejected; use the timeline's public `jet` method.

The kernel differentiates the seeded ridge lattice, quintic interpolation, authored clock, Gaussian opening, gate, tanh transforms, stabilization and boundary ramps. Field jets contain `{value, d1, d2}`; discrete flags and identifiers remain in the primal sample. Second derivatives are ordinary derivative values, not Taylor coefficients.

Jet arithmetic helpers include `jet`, `jetAdd`, `jetSub`, `jetMul`, `jetDiv`, `jetExp`, `jetLog`, `jetTanh`, `jetSmooth5` and `jetBoundaryRamp`, with corresponding snake-case Python names. Numeric guides also expose analytic jets; callback capture remains a sampled approximation of its original source.

## Normalized native samples

`nativeNormalized(curve, x, h?)` / `native_normalized(curve, x, h=None)` accepts native leaf coordinates through `2^40`. Inside the finite leaf domain it preserves actual sample diagnostics. Beyond it, baseline-relative evaluation avoids forming an overflowing baseline.

Normalized records include `logBase`, deviations, adapters and `signedLogValue: {sign, logAbs}`. Raw magnitudes are null/None if they overflow or underflow to zero; canonical zero has sign zero and null `logAbs`. A nonrepresentable nonzero magnitude is not a zero.

Finite composition wrappers normalize their own transformed samples within their declared `normalizedDomain`. They do not inherit a native ancestor's wider domain or its untransformed values. For far forks, authored guides and scaled composition, choose the explicit `/6` [extended-domain API](EXTENDED.md).

## Scope

Floating analytic propagation approximates derivatives of the declared construction; it is not a proof of a continuous real-valued implementation at every machine coordinate. Hard clipping, narrow handoffs and numerical range limits retain their documented availability constraints. The `/6` value representation does not establish an arbitrary-precision derivative interface or an extended-domain enclosure certificate.
