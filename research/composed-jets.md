# Analytic composition

A jet stores a value and its first two derivatives with respect to progression. Explicit latent coordinates remain fixed; implicit coordinates follow the configured path. Evaluation uses the scalar sampler's memoized composition DAG. Scalar samples remain authoritative and unchanged.

## Guides

For polynomial coefficients, propagate Horner state with

- `v_next = z*v + c`
- `d_next = z*d + v`
- `dd_next = z*dd + 2*d`

The updates use the previous state. Convert authored units with `d/scale` and `dd/scale/scale`.

For a quintic segment, let `A=a.y`, `B=h*a.d1`, `C=h*h*a.d2/2`, and endpoint residuals `U`, `V`, `W`. The remaining coefficients are `10U-4V+W/2`, `-15U+7V-W`, and `6U-3V+W/2`. Their endpoint value, slope and curvature residuals are checked in `composed-jet-components.json`. Captured guides differentiate their actual stored interpolant, not an unstored source function.

## Influences and operations

Differentiate the actual authored blend or additive expression using product and quotient rules, including the smooth activation/release weight, moving baseline, guide, soft boundary width and constraints. An impulse or redistribution differentiates its stored perturbation polynomial and its composition with the parent. Fully weighted value guides do not inherit an unrelated unavailable parent-value derivative.

Coupling differentiates `(1-w)*d_i + w*mean(d)`. Equality between a member's current deviation and the current mean is not a structural identity: their derivatives may differ. The component conservation identity is a real-arithmetic statement; it does not certify machine-level exact conservation or nonlinear constrained outputs.

## Availability

First and second derivatives have separate availability. An unrepresentable curvature does not erase a finite slope. Hard contacts and known legacy constrained-composition discontinuities are unavailable rather than reported as smooth derivatives. Unaffected analytic fields can remain available. Analytic capability does not supply a certified global interval enclosure automatically.

The constrained overlapping-fork seam remains an unresolved scalar-model issue. Detecting it in derivative availability is not a repair of that seam.
