# Extended-domain signals

## Construction and representation

`createExtended(options)` in JavaScript, `create_extended(**options)` in Python and `Vixen.createExtended(config, domainEnd)` in Java construct `vixen-hyperfield-c2/6` signals. The explicit upper progression coordinate is at most `2^40` and must include the authored opening endpoint. Existing `/4` and coherent `/5` snapshots keep their own algorithms and coordinate contracts.

`sampleNormalized(x)` / `sample_normalized(x)` and explicit-coordinate variants return the constrained signal, unrestricted state, adapters, signed-log value and scaled numeric fields. The configured path and sixteen continuous controls retain their native meanings.

Scaled fields use `vixen-scaled-binary64/1`: a signed 53-bit significand with an exact integer exponent. This extends range, not significand precision. Arithmetic rounds at each operation. Finite raw values remain available; a raw field that overflows or rounds to zero is null/None in normalized output. Raw sampling throws when its required fields cannot be represented. Canonical zero is distinct from an unrepresentable nonzero value.

The authored opening uses the ordinary evaluator. Continuation forms its baseline with range-reduced scaled exponentials rather than first overflowing a raw exponential. Native finite samples preserve their source arithmetic and diagnostics.

## Numeric guides and operations

```js
import {createExtended, extendedPolynomialGuide, withExtendedInfluence,
  withExtendedImpulse, withExtendedRedistribution, coupleExtended, restore}
  from '@worflor/vixen';

const a = createExtended({seed: 'a'});
const b = createExtended({seed: 'b'});
const target = extendedPolynomialGuide([.1, -.02, .004],
  {center: 1e9, scale: 100});
const guided = withExtendedInfluence(a, target,
  {at: 1e9, handoffWidth: 20, reference: 'baseline',
   until: 1e9 + 300, releaseWidth: 50});
const group = coupleExtended([guided, b], .5, [1e9 + 20, 1e9 + 280]);
const replay = restore(group.curves[0].snapshot());
const sample = replay.sampleNormalized(1e9 + 150);
```

Extended polynomial guides evaluate Horner arithmetic in scaled form. Their numeric coefficients, center, scale and finite domain are replay data. Ordinary numeric guide specifications remain usable within their declared domains. Extended operations do not capture executable callbacks.

Blend and additive influence retain value, baseline and backbone reference semantics. Full absolute influence preserves a tiny target even against an enormous baseline. Constraints apply after composing unrestricted state. Increasing a constrained influence updates boundary softness coherently rather than reusing mapped output as unrestricted input.

Impulses preserve the prefix, rise over the attack, then return to the parent over relaxation. Redistribution uses the derivative of an endpoint-flat polynomial potential. Its conservation statement is tolerance-qualified for floating evaluation. Actual-deviation redistribution and nonzero shared coupling reject nonlinear output constraints; unrestricted redistribution explicitly declares its measure.

Coupling operates on normalized unrestricted values over a finite, C2-gated window. Its projections share one evaluated input DAG. Mixing is conservative in normalized units in exact arithmetic, with scale-aware floating error in implementation. Fork input lineages before coupling: forking a coupled projection is refused rather than inventing branch-joining semantics.

## Replay and checkpoints

Root snapshots use `/6`; native future-only checkpoints use `vixen-tail-c2/4`. They preserve typed seeds, explicit domain, the active overlap suffix, folded count and history witness. Settled native history is folded without changing the retained future.

Extended operation and coupling envelopes use `vixen-extended-operations/1` and `vixen-extended-coupling/1`. Shared or deep compositions use the flat `vixen-graph/1` schema. Readers validate numeric data and backward references; snapshots never store executable callbacks. Expired finite effects collapse when checkpointing, while active effects and sharing remain replayable.

## Capability boundaries

Extended value composition and extended coordinate range are separate from the finite analytic-jet and crossing-enclosure APIs. Their finite `/4` and `/5` contracts do not establish an extended-domain derivative or enclosure certificate. An unavailable raw value is not a zero, and a failed or unsupported analysis is not evidence of a crossing or its absence.

Cross-port fixtures exercise native openings, far coordinates, custom locations, overlapping forks, guide references, impulses, redistribution, shared projections and active/folded checkpoints. They establish implementation agreement within declared floating tolerances, not a whole-program mathematical certificate.
