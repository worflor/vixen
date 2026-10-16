# C2 output operations

`vixen-operations.mjs` and `vixen_operations.py` add replayable output transforms without changing a source curve or its history. The algorithm identifier is `vixen-operation-c2/1`. Snapshots store numeric operation data and a source snapshot or a backward graph reference; deep/shared compositions use `vixen-graph/1`. The root restore dispatcher must route this identifier to `restoreOperation(snapshot, restore)` or `restore_operation(snapshot, restore)`.

## Impulse

`withImpulse(curve, {at, magnitude, relaxation, attack?}, restore)` and `with_impulse` add a causal perturbation to unconstrained normalized deviation. `relaxation` is positive; `attack` defaults to `relaxation / 8`. The entire support must fit the available domain. A quintic smoothstep rises from zero to `magnitude` over the attack interval and falls back to zero over the relaxation interval. Positive and negative magnitudes are supported. Samples at and beyond the support endpoints delegate exactly to the parent. Floor and ceiling policies still apply to the resulting value and can clip the perturbation.

This construction preserves history at the start and is C2, but it does not make an instantaneous value jump. An instantaneous jump cannot also be C2 and history-preserving.

## Redistribution

`withRedistribution(curve, [from, until], shape, restore)` and `with_redistribution` accept polynomial coefficients in ascending order. `shape` may be that coefficient array, or `{coefficients, magnitude?, measure?}`. The default magnitude is 1. The perturbation is the derivative with respect to local `t=(x-from)/(until-from)` of `Q(t)=t^4(1-t)^4 P(t)`. It and its first two derivatives vanish at both endpoints. Its exact-real integral over the window is `Q(1)-Q(0)=0` by construction.

Authoring coefficients transforms `P` into a derivative shape; it does not assert that the original `P` has zero integral. The snapshot includes a compact algebra certificate descriptor for this construction, not a certification of arbitrary source code. Runtime polynomial evaluation and coefficient arithmetic round in floating point, so measured sums need tolerance and need not be bitwise equal.

By default, `measure: 'actualDeviation'` requires no nonlinear floor or ceiling policy. Such policies can remove mass through clipping, so actual-deviation conservation is rejected when they are present. Use the explicit `measure: 'unconstrainedDeviation'` option only when conservation is intended in that pre-policy channel; it makes no claim about constrained `sample().deviation`.

## Coupling

`couple(curves, strength, [from, until])` returns an immutable group with `sample(x)`, `at(x, h)`, `curves`, `snapshot()`, and `tail(from)`. There must be 2 to 32 curves with identical finite domains. Strength is in `[0,1]`. A quintic gate rises causally from zero at `from` to one at the midpoint and releases to zero at `until`. At each point it applies the doubly stochastic matrix `(1-w)I + w*11^T/N` to normalized deviations. The exact-real sum of those deviations is preserved at every point; floating-point sums need not be bitwise equal. This is a statement about normalized deviations, not raw values with possibly different baselines.

Nonzero coupling rejects sources with nonlinear floor or ceiling policies. With strength zero, each projection returns its parent sample unchanged. Coupled projections retain group persistence in snapshots. Forking a group or projection is rejected because independent branch semantics are ambiguous. The group does not merge histories, seeds, thresholds, or configuration.

## Sampling, replay, and limits

Operation wrappers provide their own `sampleRange`/`sample_range`, `sample`, and `at` paths and recompute value-derived adapters while retaining source score, guide, inspection, and configuration channels. `location`, `inspect`, and ridge inspection remain source views. `fork` wraps the forked parent with the same operation recipe; `influence` is applied to the transformed wrapper. Tails nest the parent tail snapshot, so root restoration retains the lower domain bound and active operation recipe.

`perturbationJet` and `perturbation_jet` return analytic perturbation value, first derivative, and second derivative. Public finite wrappers provide analytically propagated `jet`, `slope` and `curvature`, and normalized sampling of the actual transformed result. Their normalized domain is the wrapper's finite domain; it does not inherit a wider native range or an ancestor's values. Derivative orders report their own availability. The separate `/6` APIs retain extended values without promising arbitrary-precision derivatives. Numerical regression checks can exercise these constructions but do not certify whole-program behavior or arbitrary callbacks.
