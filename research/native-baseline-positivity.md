# Native baseline positivity

This is an exact-real argument for the authored baseline used by the normalized interval model. It is not a blanket certification of floating-point library functions or the whole sampler.

Every supported native dip pair lies inside these parameter boxes:

- First dip: center in [32,47], width in [4,6], depth at most 25.
- Second dip: center in [62,82], width in [4,7], depth at most 48.

These boxes contain both the original dips and every seed-specific interpolation. Let the canonical coordinate be t >= 1. Each authored member is

`B(t) = t*exp(t/100) - d1*exp(-((t-c1)/w1)^2) - d2*exp(-((t-c2)/w2)^2)`.

For y >= 0, the positive exponential series gives

`exp(y) >= A(y) = 1+y+y^2/2+y^3/6`.

Consequently `exp(-y) <= 1/A(y)`. Applying these bounds on four covering regions gives:

- t in [1,10]: `B(t) >= 1 - 25/A(121/9) - 48/A(2704/49) = 11053845090910039/11643681011628208 > 0`.
- t in [10,25]: `B(t) >= 10 - 25/A(49/36) - 48/A(1369/49) = 1204929046812422/371270148295241 > 0`.
- t in [25,47]: `B(t) >= 25*(5/4) - 25 - 48/A(225/49) = 4520437/949156 > 0`.
- t >= 47: the increasing quadratic exponential lower bound gives `B(t) >= 47*(1+47/100+(47/100)^2/2)-73 = 25623/20000 > 0`.

The fade from seeded to original authored growth is a convex blend: its weight is `1-smooth5((t-90)/10)` in [0,1]. Both members are positive by the same boxes, so the blend is positive. Opening timing is an increasing affine map from x >= 1 to t >= 1.

Thus cancellation of the positive native baseline in normalized boundary ratios is justified in the exact-real model, including after the ordinary scalar baseline ceases to be representable as a finite double. This does not make a negative final output positive, or justify taking its logarithm without a sign and absolute magnitude.

## Independent arithmetic check

Stithy Scryer 1.0.28 confirmed all four displayed rational equalities in invocation `2b10a0e4-8b8a-4f69-8ac1-93257fe5ad35`. Every resulting numerator and denominator is positive. The service checked those arithmetic components; the parameter-box argument and exponential-series inequalities above state the mathematical reasoning. An invocation identifier is provenance, not a replayable whole-program proof DAG.
