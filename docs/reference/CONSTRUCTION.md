# Mathematical construction

Vixen composes scalar progression, seeded fields, trajectory controls, immutable history and external guides. The sampler is a pure random-access evaluation of those ingredients, not a stateful simulation.

## Coordinates and authored progression

Let `x` be the caller's progression coordinate, `E=openingEndpoint`, and `t` the canonical clock:

```text
t(x) = 1 + (x-1)*99/(E-1)
u = max(0, t-100)
h(x) = latent + u*velocity/100
```

At the default E=100, `t=x`. `at(x,h)` supplies a location directly; `sample(x)` follows the configured path. Accepted coordinates are bounded by the [API domain](API.md).

The fixed baseline in canonical units is:

```text
B0(t) = t*exp(t/100)
      - 25*exp(-(t-42)^2/32)
      - 38*exp(-(t-71)^2/28)
```

It is positive on the supported domain but not strictly increasing: the two Gaussian dips are intentional. `introVariation` interpolates dip centers, widths and depths toward counter-addressed seed parameters. A quintic fade on canonical [90,100] returns the varied opening to B0; at and beyond 100 the baseline is B0, including its Gaussian tails. [Parameter boxes and positivity argument](TIMELINES.md).

## Smooth joining kernel

Use the clipped quintic gate:

```text
S(z) = 0                         when z <= 0
     = 10*z^3 - 15*z^4 + 6*z^5   when 0 < z < 1
     = 1                         when z >= 1

S'(z) = 30*z^2*(1-z)^2           inside [0,1]
```

Its value changes from zero to one; first and second derivatives vanish at both ends. Range and monotonicity follow from exact polynomial identities and sign checks. This provides C2 contact for valid smooth compositions. It is distinct from the boundary kernel that joins a flat limit to an identity map.

## Seeded ridge field

A seed selects 48 normalized directions in sixteen-dimensional coordinate space, with feature-specific scales, phases and counter channels. Each feature projects the location onto its direction and reads a one-dimensional lattice interpolation:

```text
noise(p) = a + (b-a)*S(p-floor(p))
```

Here a and b are deterministic neighboring counter values in [-1,1]. No random-number stream is advanced. For the continuation, with ridge direction d_i, scale A_i and phase phi_i:

```text
Q(x,h) = mean_i noise_i(phi_i + dot(d_i,h) + u/A_i)
R(x,h) = mean_i noise_i(phi_i + (dot(d_i,h) + u/A_i)/32)
```

R reads the same channels at a broader coordinate scale. Averaging ridges produces a scalar field, not sixteen independent signals. Seed-specific scales and phases diversify its shape; the finite hash does not establish statistical independence or cryptographic unpredictability.

## Native modulation and stabilization

The entrance gate uses caller units:

```text
G(x) = S((x-E)/ramp)
dFine = strength*G*2.2*tanh(contrast*Q)
dBroad = strength*G*0.25*tanh(2*R)
F0 = B*(1+dFine)
T = B*(1+dBroad)
W = S(stabilizer)
Fs = (1-W)*F0 + W*T
```

Without a foreign influence, G preserves the configured opening and introduces continuation gradually. Fine modulation can make raw values negative. The seeded broad backbone is positive under the allowed amplitude range.

Stabilization contracts distance to T by `1-W` in the unrestricted exact-real model. It changes the attraction target, rather than merely weakening modulation around B. Full stabilization is not strict monotonicity or a universal roughness guarantee.

## Immutable temporal composition

A fork keeps the actual parent P through its attachment coordinate and derives another future Q from typed seed, operation token and exact fork-coordinate bits. During the handoff:

```text
w = S((x-at)/handoffWidth)
F = (1-w)*P + w*Q
```

Endpoint contact matches value, slope and curvature wherever the inputs are C2. Historical samples delegate directly to the parent. The operation is an address for seed derivation, not an interpreted physical action. [Lineage and replay](TIMELINES.md).

A foreign scalar guide A uses the weight `amount*S((x-at)/handoffWidth)` to pull the current unrestricted value toward A. Its guide data can represent a polynomial, a finite Taylor expansion or shared endpoint jets; a trusted callback can be captured into the latter. Later forks retain influences. [Guide construction, capture limits and replay](GUIDES.md).

## Boundaries and adapters

Output policies map the unrestricted signal into an admissible band. Their smooth kernel joins a flat boundary to an identity map; hard clipping can introduce corners. Native boundary width depends on the entrance/amplitude controls. Influence activation contributes its own width while respecting the inherited buffer and available band. [Boundary semantics](CONSTRAINTS.md), [influence composition](GUIDES.md).

After constraints, normalized deviation and adapters are:

```text
deviation = (value-B)/B
activity = 0.5 + 0.5*tanh(deviation)
positiveMultiplier = exp(0.55*tanh(deviation))
positiveValue = B*positiveMultiplier
```

The positive multiplier is bounded by exp(-0.55) and exp(0.55). It is an adapter of constrained deviation, not a clamp on raw data or a promise about rewards. Foreign guides do not replace the positive authored baseline or the meaning of underlying ridge diagnostics.

## Evidence and numerical scope

[Sensitivity and conditioning](CONDITIONING.md) distinguishes continuity from derivative size. Narrow transitions can be sharp despite C2 contact. Floating implementations evaluate an exact-real construction approximately; port tolerances and bitwise within-backend regression have separate contracts.

The [research design](../RDD.md) separates component findings, independently replayed certificates, implementation tests and sampled audits. Evidence records include [field components](../../research/evidence.json), [boundaries](../../research/constraints-evidence.json), [timelines](../../research/timelines-evidence.json) and [influence](../../research/influence-evidence.json). Provenance is not itself a proof. No whole-program certificate, universal seed independence, strict monotonicity, band limitation or float lockstep is claimed. Scryer is not required at runtime.
