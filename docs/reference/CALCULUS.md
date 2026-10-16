# Native leaf calculus

`vixen-calculus.mjs` and `vixen_calculus.py` provide dependency-free second-order automatic differentiation for a native Vixen leaf. They do not evaluate fork histories, influences, or operation kernels.

## Leaf jets

`leafJet(curve, x, coordinates?)` and `leaf_jet(curve, x, coordinates=None)` return the actual `sample` or `at` result together with derivatives and a read-only `fields` mapping. `slope` and `curvature` differentiate `sample.value` with respect to external `x`. Each numeric sample field has its own `{value, d1, d2}` record; discrete flags and identifiers remain only in `sample`.

The calculation uses the leaf's 48 seeded ridges, quintic lattice interpolation and hash, authored clock, optional Gaussian opening dips, smooth opening gate, tanh transforms, stabilization and constraint ramps. Omitting coordinates differentiates the moving latent path represented by the ridges. Supplying a 16-number vector differentiates `at(x, h)` with that vector held fixed.

A zero-width constraint exactly at its boundary is reported with null value derivatives and a rationale. At the opening endpoint, score field derivatives are null because the sampler changes score branches there; the value remains differentiable through its smooth gate. Inputs representing forked timelines are rejected so callers cannot mistake a whole timeline derivative for a leaf derivative.

The exported Jet arithmetic helpers support later composition: `jet`, `jetAdd`, `jetSub`, `jetMul`, `jetDiv`, `jetExp`, `jetLog`, `jetTanh`, `jetSmooth5`, and `jetBoundaryRamp` in JavaScript; the corresponding `jet`, `jet_add`, `jet_sub`, `jet_mul`, `jet_div`, `jet_exp`, `jet_log`, `jet_tanh`, `jet_smooth5`, and `jet_boundary_ramp` names in Python. Second derivatives use ordinary derivative values, not Taylor coefficients.

## Normalized native samples

`nativeNormalized(curve, x, h?)` and `native_normalized(curve, x, h=None)` accept `1 <= x <= 2^40`. Inside the leaf's prior domain they retain the actual sample diagnostics. Beyond that domain they evaluate the real seeded ridge and guide fields and return constraints and adapters in baseline-relative units.

The record includes `logBase`, normalized deviations and adapters, raw `base` and `value` when representable, and `signedLogValue: {sign, logAbs}`. A zero value has sign zero and null `logAbs`; a negative value has sign minus one. A raw number is null if exponentiation overflows or underflows to zero. This representation does not claim that every signed-log magnitude has a representable raw double.

Baseline evaluation uses the authored Gaussian corrections in log space. Constraint floors, ceilings, collapsed bands and smooth boundary widths are evaluated as baseline ratios, so an overflowing baseline is never materialized to compute a deviation. No floating-point representation provides infinite precision; results remain subject to IEEE-754 rounding.
