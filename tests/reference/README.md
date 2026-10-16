# Legacy compatibility reference

`v1_4_vixen.py` is the unmodified Python evaluator from commit
`3f6a668`, covered by the repository's MIT license. Its SHA-256 is pinned in
`test_python_legacy_timeline.py`:

```
9be0c2e1be387b0157b57352356ce31c25cca0eb650eb4566eb2e38be2337e67
```

The compatibility test compares the current runtime and active checkpoints to
this evaluator bit-for-bit on the same interpreter and math library, using the
frozen cases plus seeded coordinates. It does not regenerate expected values.
A separate check compares the frozen Windows records across platforms with
relative tolerance `8e-14` and absolute tolerance `1e-14`, because platform math
libraries need not return identical final bits for transcendental functions.
Snapshots, field names, flags and nullability remain exact.
