# Status and known boundaries

The shipped scope is a dependency-free sampler, matching JavaScript/Python implementations, a trajectory stabilizer and an offline instrument. Applications in README are integration ideas, not evidence of production adoption.

## Before any public release

- Owner chooses an explicit redistribution license, or deliberately leaves source visible without granting one.
- Review the tracked tree and history, package contents, evidence provenance and documentation links.
- Any change to privacy, registries, releases or hosted demos requires explicit owner approval.

## Useful unresolved work

- No replayable whole-sampler composition certificate.
- No fixed-point/integer lockstep contract; floats use declared tolerance.
- No guarantee of strict monotonicity, independent dimensions or universal lack of rhythm.
- Host-game balance and high-stabilization differentiation need evaluation in actual projects.
- Research audits use NumPy; runtime does not.

No extra control or abstraction is planned merely to complete a framework. Let concrete integrations identify the next useful change.
