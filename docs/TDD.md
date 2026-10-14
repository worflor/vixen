# Technical design

## Runtime

`vixen.mjs` and `vixen.py` implement the same random-access object with no runtime dependencies. A configuration resolves typed seed, sixteen-dimensional position/travel, progression clock, amplitude/coherence controls, authored opening and output policy. Instances are immutable through the public API.

Leaf evaluation averages counter-addressed C2 ridge interpolation and a broader guide field. The opening clock drives baseline, projections and configured travel. Stabilization blends fine and broad deviation; boundaries act on the resulting scalar signal. Adapters derive from the actual constrained value and baseline.

## Function guides

Optional guide/compiler and influence modules leave root numerical evaluation unchanged. Polynomial and finite Taylor guides represent supplied coefficients; captured callbacks become immutable shared-jet quintic data. Influences compose unrestricted values, then apply output policy and recompute dependent adapters. Guided snapshots store the underlying timeline plus data and controls; no callback is replayed. The instrument bundles these modules into self-contained JavaScript and Python exports. See [guide contracts](reference/GUIDES.md).

## Timelines

A fork retains its parent object and creates an operation-derived future leaf. History delegates directly to the parent. Within the handoff, the quintic gate blends actual values, references and diagnostics. Only source fields are blended; dependent adapters are recomputed. Coordinate vectors are normalized at the public boundary and passed through private evaluation without repeated allocation.

A complete snapshot describes its replay schema and composition: core configuration and fork lineage, or a guided envelope containing the underlying snapshot, numeric guide data and attachment controls. Leaf configuration alone cannot reconstruct history. Envelope fields are validated consistently before version dispatch. Depth and data ranges bound replay work. Supported historical schemas remain explicit; malformed or wrongly retagged snapshots fail closed.

## Contracts

- Defaults retain the pinned authored opening and numerical behavior.
- Samplers are independent of evaluation order; no mutable RNG stream.
- Prefix history, coordinates and all diagnostic fields remain exact within a backend.
- Every adapter corresponds to the constrained signal.
- Valid soft constructions have C2 joins in exact arithmetic; hard clipping can introduce corners.
- Spatial sensitivity metadata concerns unrestricted deviation, not arbitrary moving-boundary compositions.
- Hash and branch seed derivation are exact across ports. Floats use scale-aware tolerance, not cross-libm lockstep.
- Domains include the translated clock's supported extent; no extrapolation beyond them.

## Verification

```sh
npm test
npm run typecheck
python -m unittest discover -s tests -p 'test_python*.py'
python tests/cross_port.py
node tests/fuzz.mjs
python tools/mutation_probe.py
python tools/verify.py
python tools/build_instrument.py
node tests/browser.mjs
npm pack
uv build --wheel
python tools/install_smoke.py
```

Fuzzing is deterministic and records its seed and actual observation count. Mutation probes use disposable copies and distinguish assertion failures from syntax failures. Optimization is checked against a pinned implementation with exact sample/diagnostic equality before cross-port and browser acceptance. Benchmarks state workload, repetitions, runtime and variation.

The instrument embeds the tested source. Its exports preserve complete lineage; display changes do not recreate the root. Root changes require explicit reset once branched. Standalone and served-compartment checks have distinct interaction scope.

## Documentation media

`uv run --with pillow python tools/build_animation.py` rebuilds the sampled README GIF. Its six seed cuts share fixed continuation axes; the opening is a labelled zoom. Pillow and system fonts are build tools, not runtime dependencies.

## Repository boundaries

README is the front door. API and mathematical references describe current behavior. RDD states research questions and evidence rules. Raw observations and proofs live under `research`; historical implementations and observations remain separate from current docs. Runtime comments explain only non-obvious invariants.

Repository visibility, registry publication and licensing are independent controls. MIT applies to the source; registry publication remains disabled. Public releases require owner approval. Host-specific paths, secrets, access grants, dependencies and disposable environments are not tracked.
