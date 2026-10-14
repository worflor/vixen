# Sensitivity and conditioning

Continuity controls joins. Conditioning controls how rapidly a valid curve can change. A smooth construction is not necessarily a gentle one.

## Handoff gate

For `S(t)=10t^3-15t^4+6t^5` on [0,1]:

```text
0 <= S'(t) <= 15/8
|S''(t)| <= 10/sqrt(3)
```

These are exact bounds. The squared curvature inequality is polynomial: `(60t-180t^2+120t^3)^2 <= 100/3`. Scryer decides it over the entire interval, not sampled points. The gate has exact third-order contact with zero at entry; the complement has third-order contact at exit. Polynomial-contact evidence can be replayed independently.

For parent P, future Q, handoff width H and `u=(x-at)/H`:

```text
F = (1-S(u))*P + S(u)*Q
F' = (1-S)*P' + S*Q' + S'*(Q-P)/H
F'' = (1-S)*P'' + S*Q'' + 2*S'*(Q'-P')/H + S''*(Q-P)/H^2
```

If an interval supplies value gap D, slope gap D1, endpoint slope bound V and curvature bound A, the blend obeys:

```text
|F'| <= V + 15*D/(8*H)
|F''| <= A + 15*D1/(4*H) + 10*D/(sqrt(3)*H^2)
```

These are conditional bounds: D, D1, V and A must bound the whole handoff interval. Values at a single point do not establish them. Smaller H increases the additional slope and curvature terms. IEEE-scale handoffs have few representable interior points; no floating implementation supplies continuous numerical derivatives on that grid.

## Spatial field sensitivity

Each lattice interpolation is `a+(b-a)*S(t)`, with endpoints in [-1,1]. Its slope is at most `15/4`. Each ridge direction is unit length and the score averages 48 ridges, so in the exact-real model:

```text
||gradient_h(score)|| <= 15/4
||gradient_h(guideScore)|| <= 15/128
```

The guide samples the same field at 32-times-broader coordinates. With stabilization weight W, strength s, contrast c and entrance gate g, unrestricted normalized deviation obeys:

```text
||gradient_h(unconstrainedDeviation)||
  <= s*g*((1-W)*33*c/4 + W*15/256)
```

A global bound replaces g by one. This quantifies sensitivity without assigning semantic labels to dimensions. It is a bound on the unrestricted diagnostic, not on every constrained output. Moving floors, ceilings and their buffers have their own derivative terms. Branch weights depend on progression, not spatial coordinates, so convex branch composition preserves this spatial bound.

For configured travel, canonical clock slope is `lambda=99/(openingEndpoint-1)` and:

```text
|score'(x)| <= (15/4)*lambda*(1/(4*tempo) + ||velocity||/100)
```

The bound uses the smallest possible ridge scale, not a claim that every ridge attains it. Coordinate-path equivalence is numerical within the declared port tolerance; cached projection and explicit dot products have different floating evaluation orders.

## Numerical scope

The formulas describe exact-real constructions. Finite tests establish sampled implementation properties. Component proof replay does not certify the complete sampler, every numeric backend, or host-game balance. Hard clipping introduces corners, finite hashes can collide, and narrow timing controls can amplify derivatives even when value and curvature join correctly.
