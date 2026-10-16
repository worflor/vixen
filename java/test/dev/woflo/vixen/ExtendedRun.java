package dev.woflo.vixen;

public final class ExtendedRun {
  public static void main(String[] args) {
    Extended.Signal root = Extended.create(Config.defaults(), Math.scalb(1, 40));
    if (!root.sample(150).equals(Vixen.createCoherent().sample(150)))
      throw new AssertionError("Finite primal changed");
    Extended.Signal branch = root.fork(1e9, "far", 500.0).fork(1e9 + 50, "overlap", 800.0);
    for (double x : new double[] {120, 1e9 + 10, 1e9 + 100, 1e9 + 1000}) {
      var p = branch.sampleNormalized(x);
      if (p.get("base") != null && x > 1e9) throw new AssertionError("Fake finite baseline");
      if (!p.equals(Extended.restore(branch.snapshotJson()).sampleNormalized(x)))
        throw new AssertionError("Extended JSON replay changed");
    }
    for (double from : new double[] {1e9 + 100, 1e9 + 1000})
      if (!branch
          .sampleNormalized(1e9 + 2000)
          .equals(Extended.restore(branch.tailJson(from)).sampleNormalized(1e9 + 2000)))
        throw new AssertionError("Extended checkpoint changed");
    Extended.Signal deep = root;
    for (int i = 0; i < 6000; i++) deep = deep.fork(1e8 + i * 20, "deep", 8.0);
    if (!Double.valueOf(1e9).equals(deep.sampleNormalized(1e9).get("x")))
      throw new AssertionError("Deep extended evaluation failed");
    java.util.Map<String, Object> full = new java.util.LinkedHashMap<>(Extended.create().tail(120));
    full.put("seed", 1L);
    full.put("leafSeed", 1L);
    full.put("foldedCount", 9007199254740991L);
    Extended.Signal counter = Extended.restore(full).fork(130, "counter", 1.0);
    try {
      counter.tail(140);
      throw new AssertionError("Unsafe folded counter emitted");
    } catch (ArithmeticException expected) {
    }
    System.out.println(
        "Java extended roots: primal preservation, far overlap, JSON replay, checkpoints and 6000"
            + " forks passed");
  }
}
