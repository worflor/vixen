# Research design

## The question

Can a progression curve have an authored, legible opening and an irregular continuation that remains reproducible, smoothly explorable, and controllably coherent?

The narrow brief matters: progression 1–100 should look like mild exponential growth with a couple of tasteful dips. Beyond that, the curve may climb, fall, turn negative or change direction, but should not feel like independent random samples or a shared sine-wave timetable disguised by seeds.

Vixen is a constructed answer to that question, not a claim to have invented universal noise, formal dynamical chaos, or an optimal difficulty curve.

## What the work learned

**Seed diversity and timing diversity are different.** The old pulse construction could have low cross-seed correlation while retaining high phase coherence at a shared clock. Making pulse signs and amplitudes more random did not remove that timetable.

**A seed and a coordinate serve different purposes.** The seed selects a landscape. Continuous real coordinates provide a way to explore nearby variants within that landscape. The 16-dimensional space is a procedural parameterization, not a learned meaning space.

**Amplitude and coherence are different controls.** Turning down strength approaches the plain baseline. Stabilization instead approaches a gentler, seed-dependent trajectory. It should keep useful identity while reducing short-scale excursions.

**Exact components and empirical behavior belong together.** Deriving joins and bounds gives reliable construction constraints. Sampling spectra, sensitivity and trajectories exposes behavior that those constraints alone do not settle.

## What counts as evidence

- `research/evidence.json`: annotated earlier deployed Scryer component findings, retained with their original algorithm label and invocation provenance. It is not a raw proof transcript or a new whole-object certificate.
- `research/verification-game.json` and cross-port reports: sampled implementation acceptance with explicit scope.
- `research/rhythm-audit.json`: finite-window spectral and sensitivity measurements, including comparison to the historical pulse construction.
- `research/stabilizer-audit.json`: twelve sampled seeds and several stabilizer settings. Its second-difference roughness decreased at every tested setting. That is a scoped observation, not a theorem about every configuration.
- Tests: executable invariants and regressions, not a substitute for all-seed mathematical analysis.

The baseline-positivity argument combines an elementary interval decomposition with recorded exact sign checks. The gate's coefficients, monotonicity/range and complement witness have recorded engine findings. Composing those into the sampler is an engineering/mathematical argument; the evidence does not pretend to be one replayable whole-program certificate.

## Experimental method

When changing a construction, preserve the exact seed, full configuration, coordinate window, resolution, output used and failure case. Compare a meaningful predecessor or control, not only a attractive final plot.

- Test timing with spectra and cross-seed phase coherence, not correlation alone.
- Test every intended latent direction, then assess numerical rank and conditioning over a declared window.
- Examine continuous curves and integer samples separately; narrow events can disappear at game-level sampling resolution.
- Evaluate stabilization against its actual target. Distance to the baseline is a different quantity.
- Compare both language implementations, including strings, numeric seeds and edge cases.
- Keep negative results and engine refusals legible.

## Taste and boundaries

Preserve recognizable structure without putting every seed on a metronome. Prefer smooth transitions and navigable variation over arbitrary independent jumps. Give users an honest instrument with inspectable parameters, not mysterious sliders that quietly rewrite exported data.

A mathematically valid curve can still be a poor game curve. Balance, payout expectations, player readability and progression pacing require domain-specific evaluation. A locally full-rank sensitivity matrix can still lose useful differentiation over another window or near full stabilization.

No cryptographic randomness, semantic coordinate labels, strict monotonicity, universal seed independence, guaranteed balance, or production adoption is claimed. C2 continuity is not band limitation or an antialiasing guarantee.

## Useful next questions

Let real integrations choose the next work:

- Which backbone scales and amplitudes retain useful differences over a game's actual window?
- Which bounded-output calibrations give appropriate encounter or difficulty distributions?
- Does integer/fixed-point evaluation need a separate deterministic contract?
- Is another authored baseline useful enough to deserve a compatible interface?
- Can the full projection/gating/stabilization composition earn a replayable certificate?

Do not add knobs simply because they are possible. Preserve the small, understandable mechanism until a concrete use exposes a need.
