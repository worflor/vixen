package dev.woflo.vixen;

import java.math.BigInteger;
import java.util.Map;

public final class ScaledStateRun {
  public static void main(String[] args) {
    Wide huge = Wide.fromDouble(1).scale(BigInteger.valueOf(10000));
    Map<String, Object> meta =
        Map.of(
            "x",
            1e9,
            "score",
            0.0,
            "guideScore",
            0.0,
            "gate",
            1.0,
            "rawDeviation",
            0.0,
            "stabilizationWeight",
            0.0);
    ScaledState.State s =
        new ScaledState.State(meta, huge, Wide.fromDouble(1), huge, null, null, Wide.fromDouble(0));
    Map<String, Object> p = ScaledState.sample(s);
    if (!Double.valueOf(1).equals(p.get("value"))
        || p.get("base") != null
        || ((Number) p.get("deviation")).doubleValue() != -1)
      throw new AssertionError("Tiny target lost");
    if (ScaledState.policy(Wide.fromDouble(1), null, huge, huge).cmp(Wide.fromDouble(1)) != 0)
      throw new AssertionError("Upper transition cancellation");
    if (ScaledState.policy(Wide.fromDouble(-1), huge.neg(), null, huge).cmp(Wide.fromDouble(-1))
        != 0) throw new AssertionError("Lower transition cancellation");
    Sample raw = Vixen.createCoherent().sample(150);
    if (!ScaledState.sampleFromScalar(raw).entrySet().containsAll(raw.asMap().entrySet()))
      throw new AssertionError("Legacy primal changed");
    System.out.println(
        "Java scaled policy: tiny signed targets, honest raw availability, immutable records and"
            + " legacy primal passed");
  }
}
