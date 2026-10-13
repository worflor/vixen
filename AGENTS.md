# Vixen: contributor map

A small deterministic JavaScript/Python sampler with an offline instrument. Keep the narrow identity: an authored opening, irregular seeded continuation, continuous coordinate exploration, and a seeded trajectory stabilizer. It is not an AI product or a general noise framework.

## Where to look

- README.md: user-facing purpose, applications, installation and play.
- docs/TDD.md: canonical technical model, compatibility and verification.
- docs/RDD.md: canonical research intent, methodology, taste and uncertainty.
- docs/reference/API.md: accepted configuration domains and output contract.
- docs/reference/CONSTRUCTION.md: equations and component-evidence explanation.
- docs/STATUS.md: known limitations and release decisions.
- vixen.mjs / vixen.py / vixen.d.ts: runtime and declarations.
- instrument/: source template/UI and generated offline HTML.
- examples/: runnable integration sketches and CSV sweep.
- tests/: acceptance and cross-language checks.
- research/: numerical audits and scoped evidence, including historical component records.
- tools/: instrument building and installed-distribution smoke checks.
- legacy/: explicit historical replay/comparison, not automatic fallback.

## Work and verification

```sh
npm ci
npm test
npm run typecheck
python tests/cross_port.py
python tools/build_instrument.py
npm pack
uv build --wheel
python tools/install_smoke.py
```

Research audits additionally need NumPy; `npm run audit` runs them. Use an equivalent pip wheel build if uv is unavailable. Check the offline UI after UI changes.

## Invariants and judgment

Preserve typed seeds, full-config replay, algorithm identity, domains, the exact calm prefix, bounded adapters and exact prior hyperfield replay at stabilization zero. Keep runtime dependencies empty. Mathematical changes require both implementations, tests, appropriate audits, and an explicit compatibility decision.

Distinguish implementation reality, intended design, measured behavior and mathematical evidence. Historical JSON is not automatically current proof. Keep durable reasoning in the design documents, supporting lookup in reference, and bounded investigations in docs/briefs/ only when they earn a file.

Package hygiene and installed behavior matter more than a plausible source-tree demo. No credentials, access grants, machine paths, dependency trees or smoke environments in tracked content. Keep the repository private until the owner explicitly approves otherwise; do not publish to registries or add a public license on your own.
