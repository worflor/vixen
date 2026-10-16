# Constraint-state closure and interval evaluation

## Versioned composition

Let `P(U,L,H,e)` be the selected lower/upper constraint map. A constrained fork cannot generally satisfy both

`V = (1-w) P(Ua,La,Ha,ea) + w P(Ub,Lb,Hb,eb)`

and

`V = P((1-w)Ua+wUb, (1-w)La+wLb, (1-w)Ha+wHb, (1-w)ea+w*eb)`.

A descendant that reapplies `P` to the second state can therefore jump when its influence weight first becomes nonzero. This is a state-closure defect, not a gate-derivative defect.

Native algorithm `vixen-hyperfield-c2/5` blends unrestricted values, applicable bounds and width, then projects the resulting state. The guide is projected from its own unrestricted value using that same state. Derived deviation and display adapters follow the projected value. Its checkpoint identifier is `vixen-tail-c2/3`.

A smoothstep handoff has zero first and second derivatives at both endpoints. Each blended state component therefore matches the corresponding parent or child jet through order two. Composition with a C2 policy preserves those joins. Hard zero-softness contacts remain explicitly qualified by derivative availability.

Historical native algorithm4 and checkpoint2 retain applied-value blending. Their samples are not rewritten to satisfy the new invariant. A separate frozen corpus captures six constrained legacy timelines and sixty samples per language, including active checkpoint replay.

The default constructor retains algorithm4 for compatibility. Coherent construction is an explicit, shared contract in JavaScript, Python and Java; callers choose it rather than silently changing historical replay.

## Moving-bound intervals

For `R(t)=6t^3-8t^4+3t^5`, inside the lower transition strip,

`dP/dL = 1-R'(t) = -(t-1)^2(3t-1)(5t+1)`.

This changes sign. Endpoint-only evaluation in the boundary location is invalid. The interior minimum occurs at `L=y-e/3`, where `P=y-16e/81`. `research/boundary-interval-components.json` records exact component checks and the refused query spelling separately from its successful reformulation.

A real-arithmetic extremum is not by itself an enclosure of rounded program output. The implemented enclosure includes every feasible hard, soft and identity branch and evaluates the polynomial with outward primitive arithmetic. Upper-bound mapping uses the mirrored lower map.

## Raw arithmetic before normalized deviation

In floating arithmetic, raw `B+B*u`, projection, subtraction and division are not interchangeable with a homogeneous normalized formula. The native enclosure follows the actual raw baseline, product and constraint computations before forming the returned deviation. Timeline interval states separately model legacy applied-value mixing and coherent unrestricted-state mixing.

Intervals are cut at the opening endpoint and handoff boundaries. Before opening, all affected native samples have zero deviation. After opening, the original baseline applies and its exponential/Gaussian terms are enclosed directly. A query is scoped to its exact curve and evaluator; an ancestor oracle is not evidence about a transformed curve.

These are construction derivations and tested interval paths, not a blanket formal certificate for the runtime. The replayed certified ln(2) component is a separate proof artifact.
