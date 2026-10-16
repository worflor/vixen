# Java integration

The dependency-free runtime is in `java/src/dev/woflo/vixen`. Build and exercise its Java 17 distribution with:

```sh
python tools/build_java.py
python tools/test_java_distribution.py
npm run test:java
```

The builder writes `dist/vixen.jar`, includes the MIT license and targets Java class-file version 61. Use that JAR on an application's compile and runtime classpaths. No Maven repository, network service or mathematical engine is needed by a consumer.

## Finite curves

```java
import dev.woflo.vixen.*;

Curve curve = Vixen.createCoherent()
    .impulse(120, .1, 20)
    .fork(160, "take-key", 8.0);
Sample sample = curve.sample(180);
Sample[] batch = curve.sampleRange(160, 220, 61);
Analytic.Result jet = curve.analyticJet(180, null);
Curve replay = Vixen.restoreCurve(curve.snapshotJson());
```

`Vixen.create()` preserves historical arithmetic; `createCoherent()` selects constraint-state composition. Typed `Seed` values distinguish text from uint32 values. `Config` copies its vector inputs and outputs. Timeline and composition objects are immutable.

`analyticJet` reports value, first and second derivatives with order-specific availability. `jet` is the strict convenience form and throws if both derivatives are not available. Narrow handoffs can produce large derivatives; hard boundary contacts can make an order unavailable. Availability is not estimated with finite differences.

Guide data, influences, impulses, redistribution and shared coupling preserve the same replay families as the JavaScript and Python implementations. Coupled projection forks are explicitly refused. `Vixen.restore` dispatches single curves and replayable groups; use `restoreCurve` only for finite single-curve snapshots.

Crossing queries use declared intervals, tolerance and resource limits. Certified absence is scoped to an applicable enclosure oracle; unsupported composition or exhausted search is not a negative result. Occupancy calibration is bound to an explicit sample population and its witness. Seeded correlated channels validate positive semidefiniteness and do not claim correlation survives nonlinear output constraints.

## Extended signals

```java
import dev.woflo.vixen.*;
import java.util.*;

Extended.Signal a = Vixen.createExtended();
Extended.Signal b = Vixen.createExtended(Config.defaults().withSeed(Seed.of(2)));
Map<String,Object> target = ExtendedOps.polynomial(
    new double[]{.1, -.02, .004}, 1e9, 100, 1, Extended.MAX_DOMAIN_END);
Extended.Signal guided = ExtendedOps.influence(a, target,
    Map.of("at", 1e9, "handoffWidth", 20, "reference", "baseline",
           "until", 1e9 + 300, "releaseWidth", 50));
ExtendedOps.Group group = ExtendedOps.couple(
    List.of(guided, b), .5, 1e9 + 20, 1e9 + 280);
Extended.Signal selected = group.curves().get(0);
Map<String,Object> sample = selected.sampleNormalized(1e9 + 150);
Extended.Signal replay = (Extended.Signal)Vixen.restore(selected.snapshotJson());
```

Extended signals implement a separate value/representation API rather than pretending a nullable raw sample is a finite `Sample`. `sample` and `at` throw when required raw fields are unrepresentable. `sampleNormalized` and `atNormalized` expose scaled numeric data. `ExtendedOps.impulse`, `redistribute` and `couple` retain the representation through a shared, stack-safe evaluation graph.

Use `snapshotJson` for complete replay and `tailJson(from)` for future-only checkpoints. Readers preserve native fork witnesses and shared projection identity. See [extended contracts](EXTENDED.md), [scaled numbers](SCALED-NUMBERS.md) and [finite calculus](CALCULUS.md) for their distinct capability boundaries.
