# Influence windows and deviation spaces

An influence stores a compiled numeric guide and a deterministic recipe. Callback guides are captured once when attached; replay evaluates only the captured guide data. Capture is a sampled approximation of the callback, not a guarantee about arbitrary source code.

## Timing and support

An influence activates at `at` with zero weight. Over `handoffWidth`, its weight rises with the quintic gate, then holds at `amount`. A window is enabled when either `until` or `releaseWidth` is supplied. `until` marks the start of release. A positive `releaseWidth` fades the weight to zero over that interval; at and after `until + releaseWidth`, the wrapper returns the parent sample directly. With `releaseWidth: 0`, the influence holds for `x < until` and delegates directly at `until`.

If `until` is supplied without `releaseWidth`, the release width is zero. If `releaseWidth` is supplied without `until`, release starts at `parent.domain[1] - releaseWidth`. Both controls must be finite numbers when present. The release start cannot precede the end of handoff, and the release endpoint must be representable within the parent domain.

A windowed influence keeps the parent's full domain. Its guide needs to cover the active span from activation through the release endpoint; the guide is never evaluated before activation or at/after release completion. An unwindowed influence retains the established behavior: its domain ends at the earlier of the parent's upper endpoint and the guide's upper endpoint.

The parent is returned exactly when the computed weight is zero, including at activation and at/after release completion. Nested influences created at the same time apply in creation order: the later wrapper is evaluated outside the earlier wrapper. Forks retain the original recipe and preserve their parent's historical prefix.

## Spaces

`reference` defaults to `value`, whose target is the guide's numeric value. `baseline` interprets the guide as `g` and targets `p.base * (1 + g)`. `backbone` targets `p.unconstrainedGuideValue * (1 + g)`. The blend uses the parent's existing unconstrained value and these original reference channels; it does not use another influence's target as a reference.

Unwindowed value-reference snapshots keep the schema 1 algorithm identifier and field set. Windows and non-default spaces use schema 2. Both versions are replayable; parsers must recognize both algorithm identifiers. Snapshot fields, numeric controls, and guide data are validated before reconstruction. Restore walks nested influence snapshots iteratively and rejects cycles without imposing a fixed composition-depth limit.

## Checkpoints and API hooks

An influence tail wraps `parent.tail(from)` in the influence snapshot format. It retains permanent and still-active or future recipes, including an activation earlier than the checkpoint. A window whose release endpoint is at or before the checkpoint can be dropped. The restored wrapper's lower coordinate bound is the checkpoint's `from` value.

The host timeline restore dispatcher must route both influence algorithm identifiers to `restoreInfluence` / `restore_influence`, and route the wrapped parent snapshot through its existing restore function. The host timeline must provide `tail(from)` with a checkpoint domain beginning at `from`.

Influence wrappers expose their sampling and inspection methods explicitly. They do not forward parent methods that derive ranges or analytic jets from unmodified samples. When the host adds normalized sampling or analytic jets, it must provide influence-aware hooks before those methods are exposed through an influence wrapper. No constant-time cost is claimed: evaluating a composition can require work proportional to the active nested guides.
