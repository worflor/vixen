# Scaled numbers

`Wide` represents a signed, normalized 53-bit significand and an exact integer binary exponent. Exponents are not limited to binary64's range. Significands are not arbitrary precision.

JavaScript exports `Wide`, `WIDE_FORMAT`, and `wideExp` from the library root. Python exports `Wide`, `WIDE_FORMAT`, and `wide_exp`. Java provides `Wide` and `WideExponential` in `dev.woflo.vixen`.

## Arithmetic

Construct from a finite binary64 value with `Wide.fromNumber`, `Wide.from_number`, or `Wide.fromDouble`. Exact integer significands and exponents can be passed to the constructor. `fromRatio` / `from_ratio` constructs an exact integer ratio with one quotient rounding into the scaled format.

Addition, subtraction, multiplication, division, and integer powers round at each operation to the format's significand precision. They are not symbolic algebra. Addition retains the gap-54 decision needed below powers of two. A later cancellation cannot recover information already rounded away by an earlier operation.

Use `toNumber` / `to_number` / `toDouble` to request binary64 output. A nonzero result that overflows or rounds to zero returns null/None, not infinity or an invented zero. The format has one canonical zero.

## Serialization

The shared object has `format: "vixen-scaled-binary64/1"`, `sign`, a binary64 `significand`, and a canonical decimal-string `exponent2`. The magnitude is `significand * 2^exponent2`. Nonzero significands lie in `[1,2)`. Decoders reject malformed exponent tokens rather than stripping whitespace or normalizing them.

Java uses `toMap` / `fromMap`; JavaScript uses `toJSON` / `decode`; Python uses `to_json` / `decode`.

## Reduced exponential

`wideExp(x)` / `wide_exp(x)` / `WideExponential.exp(x)` accepts a finite binary64 argument. It chooses an integer `k` using an audited 2048-bit ln(2) coefficient and computes the residual with exact integer arithmetic. Only the small residual is passed to the host exponential kernel. The result is a scaled number, including for arguments near either finite binary64 extreme.

The coefficient is bound to a live-replayed Scryer certificate. This proves a component enclosure and bounds the coefficient's range-reduction error; it does not certify the host exponential implementation or promise correctly rounded transcendental results. Independent 420-digit reduction fixtures exercise all ports.

These primitives do not automatically extend arbitrary curves. The explicit `/6` signal family provides a larger coordinate domain and scaled-state influences, impulses, redistribution and coupling. Representation, authoring range and analysis remain separate capabilities. See [extended signals](EXTENDED.md).
