# Vixen: contributor map

A deterministic procedural trajectory engine: progression and sixteen-dimensional seeded fields, stabilization, admissible outputs, immutable forks and external function guides. Keep runtime evaluation dependency-free and random-access. Mathematical meaning belongs to explicit contracts, not imagined semantics for dimensions or operation tokens.

## Source and documentation

- `README.md`: purpose, exploration, installation, integration and reference index.
- `docs/TDD.md`, `docs/RDD.md`: technical contracts and research methodology.
- `docs/reference/`: API, construction, boundaries, timelines, conditioning and guides.
- `docs/STATUS.md`: limitations and release policy.
- `vixen.mjs`, `vixen.py`: core field and fork runtime.
- `vixen-guide.mjs`, `vixen_guide.py`: numeric guide representation, compilation and capture.
- `vixen-influence.mjs`, `vixen_influence.py`: immutable guided compositions and replay.
- `vixen.d.ts`: public TypeScript surface.
- `instrument/`: template, UI and generated offline instrument with bundled SDK exports.
- `examples/`, `tests/`, `tools/`: runnable integration, acceptance and reproducible builds.
- `research/`, `legacy/`: scoped evidence and historical comparison sources, not automatic current guarantees.

## Verification

```sh
npm ci
npm test
npm run typecheck
python tools/verify.py
node tests/fuzz.mjs
python tools/mutation_probe.py
python tools/build_instrument.py
node tests/browser.mjs
npm pack
uv build --wheel
python tools/install_smoke.py
```

The install smoke selects artifacts by the current package version; older distributions may coexist without being installed accidentally. Research audits require their optional dependencies. Exercise both standalone and served UI paths when their integration changes.

## Invariants

Preserve typed seeds, numerical domains, random-access determinism, default opening replay, exact inherited history and truthful derived adapters. Optional opening variation and explicit influences have their own contracts; do not impose the default prefix guarantee on them. Save full snapshots, including nested guide data. Keep arithmetic order pinned during optimization and make compatibility decisions explicit.

Math changes require matching runtime implementations, discriminating tests and appropriate evidence. A callback capture is a sampled approximation, not a proof about arbitrary source code. Private implementation attributes are not a security sandbox. Unknown replay fields, cycles, excessive depth and invalid numeric data must fail closed.

## Presentation and delivery

Describe the object as it exists. Keep README and references free of development narration, release-by-release interjections and conversational asides. Preserve necessary replay identifiers; put historical observations under research/history or legacy. Use comments only for non-obvious invariants, not line-by-line narration; concise does not mean minified.

Verify packaged installs rather than assuming source-tree success. Do not track credentials, viewer grants, machine-specific paths, dependency trees or disposable environments. MIT licensing does not authorize changes to repository visibility, registry publication or hosted access. Preserve owner-selected settings and request approval for external release changes.
