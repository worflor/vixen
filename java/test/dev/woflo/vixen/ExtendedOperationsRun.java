package dev.woflo.vixen;

import java.util.*;

public final class ExtendedOperationsRun {
  public static void main(String[] args) {
    Extended.Signal a = Extended.create(Config.defaults()),
        b = Extended.create(Config.defaults().withSeed(Seed.of(2)));
    Map<String, Object> guide =
        ExtendedOps.polynomial(new double[] {1}, 0, 1, 1, Extended.MAX_DOMAIN_END);
    Extended.Signal guided =
        ExtendedOps.influence(a, guide, Map.of("at", 120, "amount", 1, "handoffWidth", 20));
    if (Wide.fromDouble(1)
            .cmp(Wide.fromMap((Map<String, ?>) guided.sampleNormalized(1e9).get("scaledValue")))
        != 0) throw new AssertionError("tiny guide");
    if (!guided
        .sampleNormalized(1e9)
        .equals(((Extended.Signal) Vixen.restore(guided.snapshotJson())).sampleNormalized(1e9)))
      throw new AssertionError("guide replay");
    Extended.Signal impulse =
        ExtendedOps.impulse(a, Map.of("at", 120, "magnitude", .5, "relaxation", 40, "attack", 10));
    if (!impulse
        .sampleNormalized(121)
        .equals(((Extended.Signal) Vixen.restore(impulse.tailJson(120))).sampleNormalized(121)))
      throw new AssertionError("impulse checkpoint");
    ExtendedOps.Group group = ExtendedOps.couple(List.of(guided, b), 1, 1e9, 1e9 + 100);
    if (!group
        .curves()
        .get(0)
        .sampleNormalized(1e9 + 50)
        .get("scaledUnconstrainedDeviation")
        .equals(
            group.curves().get(1).sampleNormalized(1e9 + 50).get("scaledUnconstrainedDeviation")))
      throw new AssertionError("coupling");
    Extended.Signal chain = a;
    for (int i = 0; i < 4000; i++)
      chain =
          ExtendedOps.influence(
              chain, guide, Map.of("at", 120, "amount", .001, "handoffWidth", 20));
    if (!chain
        .sampleNormalized(150)
        .equals(((Extended.Signal) Vixen.restore(chain.snapshotJson())).sampleNormalized(150)))
      throw new AssertionError("deep graph");
    Extended.Signal retired = a;
    for (int i = 0; i < 4000; i++)
      retired =
          ExtendedOps.impulse(
              retired, Map.of("at", 120, "magnitude", .1, "relaxation", 20, "attack", 5));
    if (!retired.tailJson(200).equals(a.tailJson(200)))
      throw new AssertionError("retired checkpoint");
    System.out.println(
        "Java extended guide/impulse/coupling, 4000-stage replay and retired-prefix collapse"
            + " passed");
  }
}
