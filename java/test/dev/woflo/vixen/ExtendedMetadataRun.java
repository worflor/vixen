package dev.woflo.vixen;

import java.util.*;

public final class ExtendedMetadataRun {
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static void main(String[] args) {
    Map<String, Object> target =
        (Map<String, Object>)
            VixenJson.parse(
                VixenJson.guideText(new Guide.Polynomial(new double[] {4, .2}, 0, 1, 1, 50000)));
    Extended.Signal source =
        ExtendedOps.influence(Extended.create(), target, Map.of("at", 120, "handoffWidth", 16));
    String before = source.snapshotJson();
    Map<String, Object> snapshot = source.snapshot();
    List<?> nodes = (List<?>) snapshot.get("nodes");
    Map<?, ?> frame = (Map<?, ?>) nodes.get(nodes.size() - 1),
        recipe = (Map<?, ?>) frame.get("recipe"),
        guide = (Map<?, ?>) recipe.get("guide");
    try {
      ((List) guide.get("coefficients")).set(0, 999);
    } catch (UnsupportedOperationException expected) {
    }
    if (!before.equals(source.snapshotJson()))
      throw new AssertionError("Replay metadata mutation changed source snapshot");
    ExtendedOps.Group group =
        ExtendedOps.couple(
            List.of(Extended.create(), Extended.create(Config.defaults().withSeed(Seed.of(2)))),
            .5,
            120,
            200);
    if (!group.curves().get(0).sampleNormalized(1).equals(group.sampleNormalized(1).get(0)))
      throw new AssertionError("Projection failed at domain origin");
    System.out.println("Extended replay metadata isolation and origin projection passed");
  }
}
