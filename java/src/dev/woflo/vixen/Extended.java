package dev.woflo.vixen;

import java.util.*;

public final class Extended {
  private Extended() {}

  public static final String ALGORITHM = "vixen-hyperfield-c2/6",
      TAIL_ALGORITHM = "vixen-tail-c2/4";
  public static final double MAX_DOMAIN_END = Math.scalb(1.0, 40);

  public interface Signal extends Vixen.Restored {
    Config config();

    double[] domain();

    double latestWriteTime();

    default double from() {
      return domain()[0];
    }

    default double domainEnd() {
      return domain()[1];
    }

    default double latestWrite() {
      return latestWriteTime();
    }

    default Map<String, Object> snapshotMap() {
      return snapshot();
    }

    Map<String, Object> sampleNormalized(double x);

    Map<String, Object> atNormalized(double x, double[] h);

    Signal fork(double at, String operation, Double width);

    Map<String, Object> snapshot();

    Map<String, Object> tail(double from);

    default Sample sample(double x) {
      return raw(sampleNormalized(x));
    }

    default Sample at(double x, double[] h) {
      return raw(atNormalized(x, h));
    }

    default String snapshotJson() {
      return Vixen.stringifyTree(snapshot());
    }

    default String tailJson(double from) {
      return Vixen.stringifyTree(tail(from));
    }

    default String tailJson() {
      return tailJson(domain()[0]);
    }
  }

  private record Frame(int index, Timeline.Entry entry, Leaf leaf) {}

  private record Node(Frame value, Node left, Node right, int height) {}

  private static int height(Node n) {
    return n == null ? 0 : n.height();
  }

  private static Node node(Frame f, Node a, Node b) {
    return new Node(f, a, b, 1 + Math.max(height(a), height(b)));
  }

  private static Node left(Node a) {
    Node b = a.right();
    return node(b.value(), node(a.value(), a.left(), b.left()), b.right());
  }

  private static Node right(Node a) {
    Node b = a.left();
    return node(b.value(), b.left(), node(a.value(), b.right(), a.right()));
  }

  private static Node append(Node tree, Frame value) {
    if (tree == null) return node(value, null, null);
    Node out = node(tree.value(), tree.left(), append(tree.right(), value));
    if (height(out.right()) - height(out.left()) > 1) {
      if (height(out.right().left()) > height(out.right().right()))
        out = node(out.value(), out.left(), right(out.right()));
      out = left(out);
    }
    return out;
  }

  private static Frame get(Node tree, int index) {
    while (tree != null) {
      if (index == tree.value().index()) return tree.value();
      tree = index < tree.value().index() ? tree.left() : tree.right();
    }
    throw new IllegalArgumentException("Missing history entry");
  }

  private static int before(Node tree, double x) {
    int found = -1;
    while (tree != null) {
      if (tree.value().entry().at() < x) {
        found = tree.value().index();
        tree = tree.right();
      } else tree = tree.left();
    }
    return found;
  }

  private static List<Frame> frames(Node root) {
    List<Frame> out = new ArrayList<>();
    Deque<Node> stack = new ArrayDeque<>();
    Node n = root;
    while (n != null || !stack.isEmpty()) {
      while (n != null) {
        stack.push(n);
        n = n.left();
      }
      n = stack.pop();
      out.add(n.value());
      n = n.right();
    }
    return out;
  }

  private static double number(Object v) {
    if (!(v instanceof Number n) || !Double.isFinite(n.doubleValue()))
      throw new IllegalArgumentException("Finite numeric data required");
    return n.doubleValue();
  }

  private static Wide w(double x) {
    return Wide.fromDouble(x);
  }

  private static Wide blend(Wide a, Wide b, double weight) {
    return a.add(b.sub(a).mul(w(weight)));
  }

  private static Wide bound(Wide a, Wide b, double weight) {
    if (a == null && b == null) return null;
    if (a == null || b == null) throw new IllegalArgumentException("Boundary shape changed");
    return blend(a, b, weight);
  }

  private static Map<String, Object> mix(
      Map<String, Object> a, Map<String, Object> b, double weight) {
    var p = ScaledState.decode(a);
    var q = ScaledState.decode(b);
    Map<String, Object> meta = new LinkedHashMap<>(a);
    for (String k :
        new String[] {"x", "score", "guideScore", "rawDeviation", "stabilizationWeight", "gate"}) {
      double x = number(a.get(k)), y = number(b.get(k));
      meta.put(k, x + (y - x) * weight);
    }
    return ScaledState.sample(
        new ScaledState.State(
            meta,
            blend(p.base(), q.base(), weight),
            blend(p.unconstrainedValue(), q.unconstrainedValue(), weight),
            blend(p.unconstrainedGuideValue(), q.unconstrainedGuideValue(), weight),
            bound(p.floorValue(), q.floorValue(), weight),
            bound(p.ceilingValue(), q.ceilingValue(), weight),
            blend(p.boundaryWidth(), q.boundaryWidth(), weight)));
  }

  private static Map<String, Object> leaf(Leaf leaf, double x, double[] h) {
    if (x <= leaf.domainEnd())
      return ScaledState.sampleFromScalar(h == null ? leaf.sample(x) : leaf.at(x, h));
    Config c = leaf.config();
    double t = c.openingEndpoint() == 100 ? x : 1 + (x - 1) * 99 / (c.openingEndpoint() - 1),
        score = 0,
        guideScore = 0;
    for (Ridges.Ridge r : Ridges.of(c, c.seed().u32())) {
      double p, g;
      if (h == null) {
        p = r.phase() + (t - 100) * r.slope();
        g = r.guidePhase() + (t - 100) * r.guideSlope();
      } else {
        double projection = Noise.dot(r.direction(), h);
        p = r.basePhase() + projection + (t - 100) / r.scale();
        g = r.basePhase() + projection / 32 + (t - 100) / (32 * r.scale());
      }
      score += Noise.at(c.seed().u32(), p, r.channel()) / 48;
      guideScore += Noise.at(c.seed().u32(), g, r.channel()) / 48;
    }
    double gate = Opening.smooth5((x - c.openingEndpoint()) / c.ramp()),
        raw = c.strength() * gate * (2.2 * StrictMath.tanh(c.contrast() * score)),
        ug = c.strength() * gate * (.25 * StrictMath.tanh(2 * guideScore)),
        weight = Opening.smooth5(c.stabilizer());
    double u = weight == 0 ? raw : weight == 1 ? ug : raw + (ug - raw) * weight;
    Double floor =
        c.floor() == Config.Floor.NONE
            ? null
            : c.floor() == Config.Floor.ZERO ? 0 : c.floor() == Config.Floor.BASELINE ? 1 : 1 + ug;
    Double ceiling =
        c.ceilingMargin() == null
            ? null
            : (c.floor() == Config.Floor.BASELINE ? 1 : 1 + ug) + c.ceilingMargin();
    double activation = c.strength() * gate * c.boundarySoftness(), width;
    if (floor != null && ceiling != null) {
      double gap = ceiling - floor;
      width = gap <= 0 || activation == 0 ? 0 : activation * gap / (gap + 2 * activation);
    } else width = floor == null && ceiling == null ? 0 : activation;
    Wide base = w(t).mul(WideExponential.exp(t / 100));
    Map<String, Object> meta =
        Map.of(
            "x",
            x,
            "score",
            score,
            "guideScore",
            guideScore,
            "gate",
            gate,
            "rawDeviation",
            raw,
            "stabilizationWeight",
            weight);
    return ScaledState.sample(
        new ScaledState.State(
            meta,
            base,
            base.add(base.mul(w(u))),
            base.add(base.mul(w(ug))),
            floor == null ? null : base.mul(w(floor)),
            ceiling == null ? null : base.mul(w(ceiling)),
            base.mul(w(width))));
  }

  public static Signal create(Config config, double domainEnd) {
    return new Field(config, Leaf.create(config), null, 0, null, domainEnd, 1, 0, null);
  }

  public static Signal create(Config config) {
    return create(config, MAX_DOMAIN_END);
  }

  public static Signal create() {
    return create(Config.defaults());
  }

  private static final class Field implements Signal {
    private final Config rootConfig;
    private final Leaf base;
    private final Node history;
    private final int count;
    private final Frame last;
    private final double end, from;
    private final long folded;
    private final String witness;

    Field(
        Config rootConfig,
        Leaf base,
        Node history,
        int count,
        Frame last,
        double end,
        double from,
        long folded,
        String witness) {
      if (!Double.isFinite(end)
          || end < rootConfig.openingEndpoint()
          || end > MAX_DOMAIN_END
          || !Double.isFinite(from)
          || from < 1
          || from > end) throw new IllegalArgumentException("Invalid extended domain");
      this.rootConfig = rootConfig;
      this.base = base;
      this.history = history;
      this.count = count;
      this.last = last;
      this.end = end;
      this.from = from;
      this.folded = folded;
      this.witness = witness;
    }

    public Config config() {
      return last == null ? base.config() : last.leaf().config();
    }

    public double[] domain() {
      return new double[] {from, end};
    }

    public double latestWriteTime() {
      return Math.max(from, last == null ? 1 : last.entry().at());
    }

    private void check(double x) {
      if (!Double.isFinite(x) || x < from || x > end)
        throw new IllegalArgumentException("Coordinate outside available extended domain");
    }

    public Map<String, Object> sampleNormalized(double x) {
      return evaluate(x, null);
    }

    public Map<String, Object> atNormalized(double x, double[] h) {
      return evaluate(x, Config.vector(h, 1e6));
    }

    private Map<String, Object> evaluate(double x, double[] h) {
      check(x);
      if (last == null) return leaf(base, x, h);
      if (x >= last.entry().end()) return leaf(last.leaf(), x, h);
      int k = before(history, x), j = k;
      while (j >= 0 && x < get(history, j).entry().end()) j--;
      Leaf origin = j < 0 ? base : get(history, j).leaf();
      if (x <= base.domainEnd()) {
        Sample p = h == null ? origin.sample(x) : origin.at(x, h);
        for (int i = j + 1; i <= k; i++) {
          Frame f = get(history, i);
          Sample q = h == null ? f.leaf().sample(x) : f.leaf().at(x, h);
          p =
              Sample.mix(
                  p, q, Opening.smooth5((x - f.entry().at()) / f.entry().handoffWidth()), true);
        }
        return ScaledState.sampleFromScalar(p);
      }
      Map<String, Object> p = leaf(origin, x, h);
      for (int i = j + 1; i <= k; i++) {
        Frame f = get(history, i);
        p =
            mix(
                p,
                leaf(f.leaf(), x, h),
                Opening.smooth5((x - f.entry().at()) / f.entry().handoffWidth()));
      }
      return p;
    }

    public Signal fork(double at, String operation, Double requested) {
      check(at);
      if (at < latestWriteTime() || at >= end || operation == null || operation.isEmpty())
        throw new IllegalArgumentException("Fork violates chronology");
      Seed.of(operation);
      double width = requested == null ? Math.min(16, end - at) : requested;
      if (!Double.isFinite(width)
          || width <= 0
          || width > 50000
          || at + width <= at
          || at + width > end) throw new IllegalArgumentException("Invalid handoff width");
      Seed parent = config().seed();
      long bits = Double.doubleToRawLongBits(at);
      int seed =
          Hash.hash32(
              Hash.hash32(parent.u32(), parent.isText() ? 1 : 2, Hash.seed32(operation)),
              (int) (bits >>> 32),
              (int) bits);
      Frame f =
          new Frame(
              count,
              new Timeline.Entry(at, operation, width, seed),
              Leaf.create(rootConfig.withSeed(Seed.of(seed & 0xffffffffL))));
      return new Field(
          rootConfig,
          base,
          append(history, f),
          Math.addExact(count, 1),
          f,
          end,
          from,
          folded,
          witness);
    }

    public Map<String, Object> snapshot() {
      return encode(from, folded, witness, base, frames(history));
    }

    private Map<String, Object> encode(
        double lower, long retired, String digest, Leaf origin, List<Frame> remaining) {
      if (retired > 9007199254740991L)
        throw new ArithmeticException("Folded count exceeds the safe replay representation");
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("algorithm", digest == null ? ALGORITHM : TAIL_ALGORITHM);
      out.put("config", VixenJson.parse(VixenJson.configText(rootConfig)));
      out.put("domainEnd", end);
      if (digest != null) {
        out.put("from", lower);
        Seed seed = origin.config().seed();
        out.put("seed", seed.isText() ? seed.text() : seed.unsigned());
        out.put("leafSeed", seed.unsigned());
        if (seed.isText()) out.put("leafSeed", seed.u32() & 0xffffffffL);
        out.put("foldedCount", retired);
        out.put("witness", digest);
      }
      List<Object> rows = new ArrayList<>();
      for (Frame f : remaining) {
        Timeline.Entry e = f.entry();
        rows.add(
            Map.of("at", e.at(), "operation", e.operation(), "handoffWidth", e.handoffWidth()));
      }
      out.put("lineage", List.copyOf(rows));
      return Collections.unmodifiableMap(out);
    }

    public Map<String, Object> tail(double start) {
      check(start);
      List<Frame> all = frames(history);
      int cut = -1;
      for (int i = 0; i < all.size(); i++) if (all.get(i).entry().end() <= start) cut = i;
      List<Timeline.Entry> dropped = new ArrayList<>();
      for (int i = 0; i <= cut; i++) dropped.add(all.get(i).entry());
      Leaf origin = cut < 0 ? base : all.get(cut).leaf();
      return encode(
          start,
          Math.addExact(folded, dropped.size()),
          Tail.historyChain(witness, dropped),
          origin,
          all.subList(cut + 1, all.size()));
    }
  }

  @SuppressWarnings("unchecked")
  public static Signal restore(String json) {
    return restore((Map<String, Object>) VixenJson.parse(json));
  }

  public static Signal restore(Map<String, Object> s) {
    boolean tail = TAIL_ALGORITHM.equals(s.get("algorithm"));
    Set<String> keys =
        tail
            ? Set.of(
                "algorithm",
                "from",
                "config",
                "domainEnd",
                "seed",
                "leafSeed",
                "foldedCount",
                "witness",
                "lineage")
            : Set.of("algorithm", "config", "domainEnd", "lineage");
    if (!s.keySet().equals(keys)
        || (!tail && !ALGORITHM.equals(s.get("algorithm")))
        || !(s.get("lineage") instanceof List<?> rows))
      throw new IllegalArgumentException("Invalid extended snapshot");
    Config root = VixenJson.config(s.get("config"));
    double end = number(s.get("domainEnd"));
    double from = tail ? number(s.get("from")) : 1;
    long folded = 0;
    String witness = null;
    Seed seed = root.seed();
    if (tail) {
      double n = number(s.get("foldedCount"));
      if (n < 0 || n > 9007199254740991d || n != Math.rint(n))
        throw new IllegalArgumentException("Invalid folded count");
      folded = (long) n;
      Object raw = s.get("seed");
      seed = raw instanceof String text ? Seed.of(text) : Seed.of(integer(raw, 0xffffffffL));
      if (integer(s.get("leafSeed"), 0xffffffffL) != (seed.u32() & 0xffffffffL)
          || folded == 0 && !seed.equals(root.seed())
          || folded > 0 && seed.isText()
          || !(s.get("witness") instanceof String text)
          || !text.matches("[0-9a-f]{64}"))
        throw new IllegalArgumentException("Invalid checkpoint metadata");
      witness = (String) s.get("witness");
    }
    Field field =
        new Field(
            root, Leaf.create(root.withSeed(seed)), null, 0, null, end, from, folded, witness);
    Signal replay = new Field(root, field.base, null, 0, null, end, 1, folded, witness);
    for (Object row : rows) {
      if (!(row instanceof Map<?, ?> r)
          || !r.keySet().equals(Set.of("at", "operation", "handoffWidth"))
          || !(r.get("operation") instanceof String operation))
        throw new IllegalArgumentException("Invalid lineage entry");
      replay = replay.fork(number(r.get("at")), operation, number(r.get("handoffWidth")));
    }
    Field rebuilt = (Field) replay;
    return new Field(
        root, field.base, rebuilt.history, rebuilt.count, rebuilt.last, end, from, folded, witness);
  }

  private static long integer(Object x, long max) {
    double n = number(x);
    if (n < 0 || n > max || n != Math.rint(n))
      throw new IllegalArgumentException("Invalid integer");
    return (long) n;
  }

  private static double field(Map<String, Object> p, String k) {
    return number(p.get(k));
  }

  public static Sample raw(Map<String, Object> p) {
    return new Sample(
        field(p, "x"),
        field(p, "value"),
        field(p, "base"),
        field(p, "score"),
        field(p, "guideScore"),
        field(p, "rawDeviation"),
        field(p, "guideDeviation"),
        field(p, "guideValue"),
        field(p, "stabilizationWeight"),
        field(p, "deviation"),
        field(p, "multiplier"),
        field(p, "activity"),
        field(p, "positiveMultiplier"),
        field(p, "positiveValue"),
        field(p, "gate"),
        field(p, "unconstrainedValue"),
        field(p, "unconstrainedDeviation"),
        field(p, "unconstrainedGuideValue"),
        field(p, "unconstrainedGuideDeviation"),
        p.get("floorValue") == null ? null : field(p, "floorValue"),
        p.get("ceilingValue") == null ? null : field(p, "ceilingValue"),
        field(p, "boundaryWidth"),
        (Boolean) p.get("constraintApplied"));
  }
}
