# Additive influence

Let `P(x)` be the parent's unrestricted deviation from the authored baseline, `g(x)` the guide in deviation units, and `w(x)` the influence weight, including its amount and window. Additive mode constructs

```
P_add = P + w g
```

rather than blending `P_blend = (1-w)P + w g`. Thus additive mode does not subtract `w P` from the native course. Two additions give `P + w₁g₁ + w₂g₂`. Forking the underlying course changes `P`, not either added term. These are real-arithmetic identities; floating-point additions retain their normal order-dependent rounding.

The scalar runtime constructs `U_add = U_parent + B (w g)` before applying boundary policy, preserving the existing blend path's arithmetic. An explicitly value-space additive guide instead contributes `w g` in value units. Additive mode defaults to `reference: 'baseline'`; baseline and backbone references both describe a dimensionless increment in this mode. A zero guide or zero weight delegates the exact parent sample, including boundary metadata.

## Contact and derivatives

For `S(t)=6t⁵-15t⁴+10t³`, `S'=30t²(1-t)²`. Both first and second derivatives vanish at either endpoint. Rise and release weights therefore have second-order contact with zero at the outside endpoints, provided the guide has finite derivatives there. Product differentiation gives

```
(wg)'  = w'g + wg'
(wg)'' = w''g + 2w'g' + wg''
```

The value-space increment `Bwg` requires the additional product with the baseline jet. The exact polynomial jet identity checked in `additive-components.json` verifies these coefficient factors, not the entire runtime's differentiation implementation.

## Additive is not automatically conservative

The area added in deviation space is `∫wg`, not `∫g`. Zero unweighted guide area is insufficient when the window varies. For a normalized window rising over `[0,1/2]` and releasing over `[1/2,1]`, take

```
W(t) = S(2t)                  for 0 ≤ t ≤ 1/2
       1-S(2t-1)              for 1/2 ≤ t ≤ 1
q(t) = t²-t+1/6
```

Although `∫₀¹q=0`, symmetry and polynomial integration give `∫₀¹Wq=-3/112`. On the first half, an antiderivative of `Wq` is

```
24t⁸ - (432/7)t⁷ + (176/3)t⁶ - 24t⁵ + (10/3)t⁴.
```

The weight has area `1/2`, so replacing the constant `1/6` by `37/168` centers this particular quadratic under this particular weight. This is a construction example, not a universal correction for other windows.

For a general polynomial guide and normalized rise/release lengths `l,r`, with `l+r≤1`, define weight moments `M_k=∫₀¹W(t)tᵏdt`. Direct substitution on the three pieces gives

```
A_k = 10/(k+4) - 15/(k+5) + 6/(k+6)
C_k = 1/(k+1) - A_k
M_k = l^(k+1) A_k
    + ((1-r)^(k+1)-l^(k+1))/(k+1)
    + r Σ[j=0..k] binomial(k,j) (1-r)^(k-j) r^j C_j.
```

In particular `M₀=1-(l+r)/2≥1/2`, so weighted centering is well-defined. For `q(t)=t²-t+c`, choose `c=(M₁-M₂)/M₀`. More generally subtract the weighted mean `Σa_kM_k/M₀` from a polynomial guide. This derivation is by substitution and linearity; the complete symbolic moment formula is not a Scryer-certified runtime claim.

`redistribute` uses a different, direct conservative construction: a derivative of a compact potential with zero endpoint values. Neither additive mode nor redistribution guarantees conservation after nonlinear floors, ceilings, reward rounding or other downstream transformations. Conservation in value units also differs from conservation in deviation units when `B` varies.

## Replay

Blend recipes preserve their existing schema and arithmetic. Additive recipes use `vixen-influence-c2/3`, with mandatory `mode: 'add'` and explicit `reference`; they cannot silently be interpreted by a schema-2 reader. Completed finite windows may be removed by a tail checkpoint. Active and scheduled additive recipes must retain their mode through snapshot, tail and restore.

## Evidence boundary

`additive-components.json` records live Scryer component checks, including refused or misrouted attempts. It does not certify binary64 arithmetic, arbitrary guide smoothness, policy composition, serialization, interval enclosures or whole-program correctness. Those require their own implementation tests and contracts.
