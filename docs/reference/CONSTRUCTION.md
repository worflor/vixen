# Construction and evidence

This explains the shipped mechanism. It is not a whole-program certificate. The recorded Scryer component findings are in [research/evidence.json](../../research/evidence.json), with their original provenance and scope.

## 1. Keep an authored opening

For valid progression x:

```text
B(x) = x exp(x/100)
     - 25 exp(-(x-42)^2/32)
     - 38 exp(-(x-71)^2/28)
```

The two Gaussian notches are authored, not seed-generated. B is positive on the supported domain, but the notches mean it is not strictly increasing everywhere. The recorded positivity argument splits the domain into intervals and combines bounds on the tails with exact sign checks.

## 2. Join without visible corners

A joining polynomial must start at 0, end at 1, and have zero first and second derivatives at both ends. The zero conditions at the start leave:

```text
S(t) = a t^3 + b t^4 + c t^5

a+b+c = 1
3a+4b+5c = 0
6a+12b+20c = 0
```

Scryer returned a=10, b=-15, c=6. Thus:

```text
S(t) = 6t^5 - 15t^4 + 10t^3
S'(t) = 30t^2(1-t)^2
1-S(t) = (1-t)^3(1+3t+6t^2)
```

Recorded engine findings check range/monotonicity and the derivative bound 15/8 on [0,1], and provide the nonnegative complement witness. Outside that interval the implementation clips S to its endpoint value. The zero endpoint derivatives make the joins C2.

## 3. Turn a seed into a field

The seed builds 48 features. Each has a normalized 16-D direction d, a scale a=(4+76*r)*tempo, a phase phi, and a separate counter channel. The deterministic mixer maps seed, integer lattice index and channel to two neighboring values in [-1,1]. S interpolates them.

For x>100, let u=x-100 and h be the selected latent coordinate:

```text
Q(x,h) = mean_i noise_i(phi_i + dot(d_i,h) + u/a_i)
```

This is a scalar ridge field over a coordinate space, not sixteen independent signals. Feature scales/phases differ by seed, removing the old globally scheduled pulse centers. It is still finite-counter, non-cryptographic structured variation.

A configured path uses h=latent+u*velocity/100. Explicit `at` calls supply h directly.

## 4. Bring the continuation in gradually

```text
G(x) = S((x-100)/ramp)
dFine = strength * G(x) * 2.2 * tanh(contrast*Q)
F0 = B(x) * (1+dFine)
```

G is zero through 100. Both the opening and its C2 entrance survive arbitrary valid seeds and coordinates. Fine modulation can make the raw curve negative; that is deliberate, not a reward-system guarantee.

## 5. Stabilize onto this seed's trajectory

The broad field uses the same channels and geometry at a 32-times-broader coordinate scale:

```text
Qguide = mean_i noise_i(phi_i + (dot(d_i,h) + u/a_i)/32)
dGuide = strength * G(x) * 0.25 * tanh(2*Qguide)
T = B(x) * (1+dGuide)
w = S(stabilizer)
Fs = (1-w)*F0 + w*T
```

At zero, the existing hyperfield is preserved exactly in the same JavaScript runtime. At one, the guide remains seeded and gently varying. Maximum strength is 2, so the guide lies conservatively within [0.5B,1.5B]. It cannot make a positive B negative.

Since w is in [0,1], each point lies between F0 and T, and its distance from T is scaled by 1-w. This is an algebraic composition argument using the gate's established bounds, not a claim of a newly replayed whole-object proof.

## 6. Adapt the output

```text
activity = 0.5 + 0.5*tanh(deviation)
positiveMultiplier = exp(0.55*tanh(deviation))
positiveValue = B(x)*positiveMultiplier
```

The positive multiplier is conservatively bounded by exp(-0.55) and exp(0.55). These adapters are not clamps on raw data and do not guarantee any payout expectation.

## What the evidence does not establish

Invocation IDs identify recorded findings; they are not proof DAGs. The archived rational proof example concerns an old pulse, not this field. Whole-sampler formal verification, universal seed independence, strict monotonicity and cross-platform float lockstep are not claimed.

See [RDD.md](../RDD.md) for measured rhythm and sensitivity scope, and [TDD.md](../TDD.md) for executable implementation checks. Scryer helped supply exact constraints and inspectable evidence; it is not needed to run Vixen.
