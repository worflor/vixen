# Technical design

## The technical model

Vixen is a pure sampler, not a simulation. A configuration builds a fixed collection of seeded ridge features. Sampling any valid progression coordinate produces the same result without advancing RNG state or replaying previous samples.

There are five layers:

1. **Authored baseline:** exponential growth with two Gaussian notches. By default it is independent of seed and coordinates; optional seed-derived notch parameter variation and affine clock reparameterization are specified in [TIMELINES.md](reference/TIMELINES.md).
2. **Seeded field:** 48 normalized random projection directions in 16 dimensions, each sampling a counter-addressed C2 lattice-noise channel at its own scale and phase.
3. **Adapters and stabilization:** a broad, gentle version of that field provides a seeded trajectory target; output adapters expose raw signed growth, bounded activity and positive multipliers.

4. **Boundary policies:** optional floor and ceiling maps constrain raw value, with C2 transition buffers or explicit hard mode. Derived adapters use the final signal. See [CONSTRAINTS.md](reference/CONSTRAINTS.md).

The equations and component evidence live in [CONSTRUCTION.md](reference/CONSTRUCTION.md). Configuration domains and output fields are authoritative in [API.md](reference/API.md).

5. **Timeline composition:** immutable operation-derived leaf futures blend over quintic handoffs while delegating all historical evaluations to their actual parent. Save root configuration plus lineage with `snapshot()`. Constraints and adapters compose over actual values, not an independent display approximation.

## Repository boundaries

- `vixen.mjs`: dependency-free ESM runtime.
- `vixen.py`: dependency-free Python runtime.
- `vixen.d.ts`: TypeScript description of the JavaScript API.
- `instrument/`: template, UI and generated standalone HTML. It embeds these exact runtime sources, not a separate reimplementation.
- `examples/`: basic use, host-game mappings, coordinate orbits and CSV sampling.
- `tests/`: sampler acceptance, stabilization compatibility, integration-example tests and cross-language comparison.
- `tools/`: reproducible instrument building and distribution smoke checks.
- `research/`: executable numerical audits and explicitly scoped evidence reports.
- `legacy/`: historical samplers needed for comparisons or deliberate old-algorithm replay.

Runtime consumers do not need Node tooling for Python, Python tooling for JavaScript, the research stack, the instrument, or Scryer.

## Parameters and coordinates

The seed selects fixed feature geometry; text and numeric seeds are distinct. A latent vector is an ordinary 16-dimensional real coordinate, not a semantic embedding or sixteen independent output channels. Normalized projection directions turn it into each feature's scalar position.

`sample(x)` uses the configured latent origin and affine velocity. `at(x,h)` uses explicitly supplied coordinates and does not apply the configured velocity to that call. Within one seed, continuous coordinate paths move continuously through the constructed field. Replacing the seed chooses a different landscape and is not a continuous morphing operation.

Zero gate through `openingEndpoint` preserves the configured authored opening (default x=100). Sample debug scores are zeroed in that region; continuity claims concern the synthesized curve and its derived continuous adapters, not every debug field.

## Stabilization

A scalar stabilizer s is eased with the same quintic S. It blends the fine curve toward a fixed-for-that-configuration broad trajectory T:

`Fs = (1-S(s))*F0 + S(s)*T`

The target samples the same seeded field at 32-times-broader coordinates, with a 0.25 modulation coefficient and a gentle fixed contrast of 2. These are construction constants, not hidden stochastic state. Strength affects both branches; contrast applies to the fine branch.

- s=0 must reproduce the prior hyperfield exactly in the same JavaScript runtime.
- s=1 must land on the seeded guide, not merely the unmodulated baseline.
- Each intermediate sample lies between its fine and guide values in exact arithmetic.
- Distance to the guide contracts by `1-S(s)`.
- Full stabilization is conservatively between 0.5 and 1.5 times the positive baseline at maximum strength.

This does not imply pointwise deviation from the baseline shrinks, strict monotonic progression, or every roughness measure decreases under every configuration. Short-window differentiation can weaken as the field is broadened.

## Compatibility

Save the complete `snapshot()` including the root configuration and lineage. The current identifier is `vixen-hyperfield-c2/4`; it is independent of package semantic version.

`restore` accepts that identifier and restores valid /3 snapshots at default timing/intro controls; it maps v2 snapshots with constraints disabled. Old identifiers containing constraint keys are rejected. It also maps prior `vixen-hyperfield-c2/1` snapshots without a stabilizer field to stabilization zero. Unrelated identifiers are rejected rather than silently normalized. Legacy source remains separate.

Counter hashes must match exactly across JavaScript and Python. Floating evaluation uses explicit scaled tolerance: exp, tanh and projection arithmetic are not promised bitwise-identical across language runtimes or machines. Integer rewards and lockstep protocols need their own host contract.

## Verification

```sh
npm ci
npm test
npm run typecheck
python tests/cross_port.py
python tools/build_instrument.py
```

Tests cover typed seeds, domains, random-access replay, unchanged opening, bounded adapters, coordinate/path equivalence, numerical joins, exact zero-stabilizer legacy replay, positive seeded target, contraction and example integrations. The cross-port program evaluates 2,400 configurations, including nonzero stabilization, and exact counter hashes.

Research audits need NumPy: `python -m pip install "numpy>=1.24"`, then `npm run audit`. Their windows, seed sets and default-setting sensitivity scope are part of the evidence, not an optional footnote.

Open the rebuilt instrument and check stabilization, navigation, plane rotation, travel, precise import and CSV. The earlier source-surface UI report is retained as historical evidence; it is not a claim that CI launches a browser on every commit.

## Distribution

`npm pack` creates an allowlisted package; `uv build --wheel` or `python -m pip wheel . --no-deps -w dist` builds the Python module. Then run `python tools/install_smoke.py` to exercise installed distributions in isolated targets rather than checkout-shadowed imports.

GitHub CI verifies runtime tests, declarations, cross-port behavior, instrument building and distributions. Keep runtime dependencies empty. Never publish to registries, change repository privacy or add an open-source license without owner approval.

## README animation

`uv run --with pillow python tools/build_animation.py` regenerates `docs/media/seed-gallery.gif` from actual Python-port samples. It hard-cuts through the text seed `vixen` and numeric seeds 0–4, holds each frame, and uses one shared continuation scale. The opening is shown in a separately labelled zoom. It does not imply that discrete seeds interpolate continuously. Pillow and system fonts are artwork-build tools, not runtime dependencies.

## Performance and changes

A branch lineage is limited to 32 decisions; handoff evaluation can inspect multiple active ancestors, while completed transitions evaluate only the latest leaf. Nested fork coordinates cannot move backward. Build the instrument once per configuration; `sample` uses cached projections while `at` computes explicit-coordinate projections. Both evaluate fine and broad noise. Measure the intended host workload before treating it as per-pixel or audio-rate infrastructure.

Changing the gate, counter mixing, feature construction, scale constants, adapters or evaluation order is a compatibility-sensitive change. Test both language implementations, rerun numerical audits, decide algorithm identity deliberately, and preserve or explicitly reject old snapshots. Packaging improvements should not casually become mathematical redesign.

## Constrained stabilization

The boundary map applies after the unrestricted stabilized signal and to its guide. Full stabilization reaches that mapped guide. Monotone motion between mapped endpoints survives, but the unrestricted exact linear distance factor generally does not. Debug fields preserve unconstrained values. Positive softness joins boundaries C2; zero softness may introduce corners. The gate-scaled width preserves the exact opening; zero strength stays authored growth.

Run `python tools/verify.py` for current acceptance and `node tests/browser.mjs` for the local browser contract (requires an installed Chromium-family browser, or BROWSER_EXE). Browser/libm comparisons use the same explicit numeric tolerance as cross-language checks.
