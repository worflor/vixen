# Technical design

## Runtime and numeric modes

JavaScript, Python and Java implement the same seeded, random-access field without runtime dependencies. The configuration resolves a typed seed, sixteen continuous coordinates, configured travel, authored opening, native modulation and output policy. Instances retain immutable source data.

Historical `/4` arithmetic remains explicit for compatibility. Coherent `/5` composes unrestricted value, unrestricted guide and boundary state, then applies output policy once. Extended `/6` carries that state in signed 53-bit scaled arithmetic and supports an explicit progression domain through `2^40`. Representation range is not arbitrary significand precision. [Extended contracts](reference/EXTENDED.md).

## Timelines and compositions

Native fork history uses persistent indexed storage, not recursive parent calls. Present-time settled sampling selects the current leaf directly. Historical queries select the relevant suffix; overlapping handoffs retain the required active sources. A fork preserves the inherited prefix, coordinates, seed derivation and diagnostics.

Unary influences and operations use immutable DAG nodes with temporal identity intervals and jump metadata. Shared coupling evaluates each input node once per query. Evaluation, graph replay, fork rebuilding and checkpoint traversal are iterative. Projection forks refuse ambiguous branch semantics; input lineages must branch before coupling.

Complete snapshots preserve root configuration, lineage, numeric guides, operation recipes and sharing. Deep or shared compositions use flat `vixen-graph/1` records with backward references. Future-only checkpoints fold settled native history with a witness and retain active effects. Expired finite effects are removed without changing the retained future. Latest-leaf configuration alone is not replay data.

## Guides and analysis

Polynomial and finite Taylor guides represent authored coefficients. Captured callbacks become numeric shared-jet quintic data, never executable replay source. Relative and additive influence distinguish value, baseline and backbone references. Output adapters derive from the constrained signal.

Finite curve jets propagate analytic first and second derivatives through native fields, guides, boundaries, forks and supported operations. Availability is order-specific. Hard contacts, overflow and unsupported analysis are not replaced with finite differences. Extended value composition does not itself establish extended analytic or interval capability.

Crossing queries require an applicable enclosure oracle and expose unresolved/resource states. Occupancy calibration names its finite population, comparison convention and witness. Correlation validation uses exact binary64 inputs for the PSD decision; output constraints do not preserve an unrestricted correlation guarantee.

## Invariants

- Sampling order consumes no random stream.
- Historical defaults and valid historical replay remain unchanged.
- Prefix samples and coordinates remain exact within a backend.
- Coherent compositions apply policy to unrestricted state, not already-mapped observations.
- Soft C2 claims concern the declared real construction. Hard clipping may introduce corners; floating execution has separately tested error.
- Integer hash and branch seed derivation are exact across ports. Floating agreement uses scale-aware tolerances.
- An unrepresentable nonzero raw value is unavailable, never an invented zero or infinity.
- Domain, chronology, numeric types and replay references are validated explicitly.

## Verification

```sh
npm ci
npm test
npm run typecheck
npm run test:python
npm run test:java
npm run test:standalone
npm run test:fuzz
python tools/mutation_probe.py
python tools/verify.py
npm run build:instrument
node tests/browser.mjs
node tests/browser-replay.mjs
python tools/test_java_distribution.py
npm pack --ignore-scripts
uv build --wheel
python tools/install_smoke.py
python tools/test_installed_features.py
```

Frozen fixtures distinguish historical compatibility from newly recorded cross-port conformance. Exact rational, high-precision decimal and independent graph-model tests exercise components separately from application-level samples. Mutation probes use disposable copies and distinguish assertion failures from syntax failures. Depth and traversal-work tests cover active, mixed and retired histories, not only elapsed time.

The instrument embeds tested source and exports standalone SDKs. Browser acceptance covers both `file:` and loopback-served use. Installed-package and isolated-JAR probes execute outside source-tree import paths. Hosted CI is a separate delivery condition, not implied by local acceptance.

## Repository boundaries

README is the front door; references describe current contracts. RDD states research and evidence rules. Proofs and observations live under `research`, historical implementations under `legacy`, and Java source under `java`. Build outputs and private operational records are not source dependencies.

Visibility, registry publication and licensing are independent controls. Source is MIT-licensed; npm publication remains disabled. Public releases require owner approval. Mathematical component certificates do not certify the whole runtime.
