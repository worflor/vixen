# Boundary policies

The sampler first builds its unrestricted stabilized signal F and seeded guide T. A pure boundary map P then produces `value=P(F)` and `guideValue=P(T)`. The source values remain in `unconstrainedValue` and `unconstrainedGuideValue`. Every game adapter derives from the final deviation, not the discarded source signal.

## Configuration

- `floor`: `none` (default), `zero`, `baseline`, or `backbone`.
- `ceilingMargin`: `null` (default) or a finite number in [0,16].
- `boundarySoftness`: a finite number in [0,1], default 0.1.

The floor L is absent, zero, authored baseline B, or original seeded guide T, respectively. The optional ceiling is `C=A+ceilingMargin*B`, with A=B when the baseline floor is selected and A=T otherwise. This makes the ceiling an upper envelope relative to the relevant trajectory, while keeping all combinations feasible. The guide is positive, so the zero floor and guide-relative ceiling do not conflict.

A zero upper margin with baseline/backbone floor deliberately produces a zero-width band and returns its reference. Zero upper margin with no floor or zero floor is only an upper cap, not a collapsed trajectory.

## Smooth boundary kernel

For normalized distance t, define q as zero at/below 0, `6t^3-8t^4+3t^5` inside [0,1], and t at/above 1. Endpoint values, slopes and curvatures match those outer branches: `(0,0,0)` at 0 and `(1,1,0)` at 1. This is not the gate polynomial: it joins a flat boundary to an identity map.

A floor returns L below L, returns the source value above L+e, and uses `L+e*q((F-L)/e)` in between. The ceiling mirrors this construction: C above C, identity below C-e, and `C-e*q((C-F)/e)` in between. These maps are monotone and C2 for positive transition width.

Scryer derived the coefficients by exact augmented-matrix nullspace, proved uniqueness by nonzero determinant, decided `0<=q(t)<=t` and `0<=q'(t)<=189/125` by exact real sign decomposition, and returned the nonnegative Handelman witness `t-q=t(1-t)^3+3t^2(1-t)^3`. The actual invocation records and refusals are in [constraints-evidence.json](../../research/constraints-evidence.json). No full-sampler proof-DAG replay is claimed.

## Width and feasibility

Let `a=strength*entranceGate*boundarySoftness`. For a single boundary, e=B*a. With both boundaries and positive normalized gap d=(C-L)/B, use `e=B*a*d/(d+2a)`. This smooth rational expression gives `0<2e<C-L`, rather than a kink-producing minimum between widths. The lower and upper transition strips cannot overlap. Zero gap returns the reference; zero a uses hard boundaries.

Width vanishes with the entrance gate and strength. The exact authored first hundred remains unchanged, and zero strength remains authored growth for every policy. Where a transition reaches the opening, the correction has the entrance gate's cubic vanishing factor, preserving its first two derivatives in the exact construction.

## Honest semantics

- Softness is transition width, not permission to violate a boundary.
- Soft rules reshape admissible values inside their buffer; outside it they are exact identity. Forbidden excursions necessarily flatten to their boundary.
- Softness zero uses hard clipping and can introduce corners. A positive width does not make abrupt changes to seed or caller coordinates smooth.
- The maps are not iterative filters or idempotent projections. Apply them once to the source sample; the sampler never accumulates state.
- q' can exceed one inside the transition. Smooth limits do not guarantee every local roughness measure decreases.
- A monotone boundary map preserves movement between the mapped fine endpoint and mapped guide as stabilization increases. It does **not** preserve the unrestricted exact linear distance factor through a nonlinear buffer.
- `zero` constrains raw value, not signed deviation from B. A positive value below B can still have negative deviation.

## Replay

The algorithm identifier is `vixen-hyperfield-c2/3`. With floor none and ceiling null, prior output values are unchanged. Valid v1/v2 snapshots explicitly restore with constraints disabled; a retagged snapshot containing newer fields is rejected. Save the identifier and complete configuration. Floating arithmetic uses the established scaled tolerance, not cross-libm bitwise lockstep.
