package dev.woflo.vixen;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.Function;

public final class ExtendedOps {
  private ExtendedOps() {}

  public static final String ALGORITHM = "vixen-extended-operations/1",
      COUPLING_ALGORITHM = "vixen-extended-coupling/1",
      GUIDE_FORMAT = "vixen-extended-guide/1";
  private static final Wide ZERO = w(0), ONE = w(1);
  private static final double[] FACTOR = {0, 0, 0, 0, 1, -4, 6, -4, 1};
  private static final Map<String, Object> CONSERVATION =
      Map.of(
          "kind",
          "finite-precision-tolerance",
          "potential",
          "t^4*(1-t)^4*P(t)",
          "perturbation",
          "d/dt potential",
          "claim",
          "Finite-precision conservation is tolerance-qualified; no exact-real claim");
  private static final Map<Extended.Signal, WeakReference<EvaluationGraph.Node>> NATIVE_NODES =
      new WeakHashMap<>();

  private static Wide w(double x) {
    return Wide.fromDouble(x);
  }

  private static double finite(Object x, String name) {
    if (!(x instanceof Number n) || !Double.isFinite(n.doubleValue()))
      throw new IllegalArgumentException("Invalid " + name);
    return n.doubleValue();
  }

  private static double number(Map<String, Object> m, String key, double fallback) {
    return m.containsKey(key) ? finite(m.get(key), key) : fallback;
  }

  private static void fields(Map<String, Object> m, String allowed, String required) {
    Set<String> valid = Set.of(allowed.split(" "));
    for (String k : m.keySet())
      if (!valid.contains(k)) throw new IllegalArgumentException("Unknown field: " + k);
    if (!required.isEmpty())
      for (String k : required.split(" "))
        if (!m.containsKey(k)) throw new IllegalArgumentException("Missing field: " + k);
  }

  private static List<Double> list(double[] a) {
    List<Double> out = new ArrayList<>();
    for (double x : a) out.add(x);
    return List.copyOf(out);
  }

  private static double[] numbers(Object input, int limit) {
    if (!(input instanceof List<?> list) || list.isEmpty() || list.size() > limit)
      throw new IllegalArgumentException("Invalid coefficient list");
    double[] out = new double[list.size()];
    for (int i = 0; i < out.length; i++) out[i] = finite(list.get(i), "coefficient");
    return out;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> object(Object input) {
    if (!(input instanceof Map<?, ?>)) throw new IllegalArgumentException("Object required");
    return (Map<String, Object>) input;
  }

  public static Map<String, Object> polynomial(
      double[] coefficients, double center, double scale, double from, double until) {
    if (coefficients == null
        || coefficients.length < 1
        || coefficients.length > 65
        || !Double.isFinite(center)
        || Math.abs(center) > Extended.MAX_DOMAIN_END
        || !Double.isFinite(scale)
        || scale < 1e-6
        || scale > 1e6
        || !Double.isFinite(from)
        || !Double.isFinite(until)
        || from < 1
        || until > Extended.MAX_DOMAIN_END
        || until <= from) throw new IllegalArgumentException("Invalid extended polynomial");
    for (double x : coefficients)
      if (!Double.isFinite(x) || Math.abs(x) > 1e250)
        throw new IllegalArgumentException("Invalid guide coefficient");
    return Map.of(
        "format",
        GUIDE_FORMAT,
        "kind",
        "polynomial",
        "coefficients",
        list(coefficients),
        "center",
        center,
        "scale",
        scale,
        "domain",
        List.of(from, until));
  }

  private record Target(
      Map<String, Object> spec,
      double lo,
      double hi,
      boolean zero,
      Function<Double, Wide> evaluate) {}

  @SuppressWarnings("unchecked")
  private static Object immutable(Object input) {
    if (input instanceof Map<?, ?> m) {
      Map<String, Object> out = new LinkedHashMap<>();
      for (var e : m.entrySet()) out.put((String) e.getKey(), immutable(e.getValue()));
      return Collections.unmodifiableMap(out);
    }
    if (input instanceof List<?> values) {
      List<Object> out = new ArrayList<>();
      for (Object v : values) out.add(immutable(v));
      return Collections.unmodifiableList(out);
    }
    return input;
  }

  private static Target target(Map<String, Object> input) {
    if (GUIDE_FORMAT.equals(input.get("format"))) {
      fields(
          input,
          "format kind coefficients center scale domain",
          "format kind coefficients center scale domain");
      if (!"polynomial".equals(input.get("kind")))
        throw new IllegalArgumentException("Unsupported extended guide");
      double[] coefficients = numbers(input.get("coefficients"), 65);
      if (!(input.get("domain") instanceof List<?> d) || d.size() != 2)
        throw new IllegalArgumentException("Invalid guide domain");
      double center = finite(input.get("center"), "center"),
          scale = finite(input.get("scale"), "scale"),
          lo = finite(d.get(0), "domain"),
          hi = finite(d.get(1), "domain");
      Map<String, Object> spec = polynomial(coefficients, center, scale, lo, hi);
      boolean zero = true;
      for (double x : coefficients) zero &= x == 0;
      return new Target(
          spec,
          lo,
          hi,
          zero,
          x -> {
            if (x < lo || x > hi) throw new IllegalArgumentException("Outside guide domain");
            Wide z = w(x - center).div(w(scale)), y = ZERO;
            for (int i = coefficients.length - 1; i >= 0; i--) y = y.mul(z).add(w(coefficients[i]));
            return y;
          });
    }
    Guide g = VixenJson.guide(input);
    Map<String, Object> spec = object(VixenJson.parse(VixenJson.guideText(g)));
    boolean zero;
    if (spec.get("coefficients") instanceof List<?> c)
      zero = c.stream().allMatch(x -> ((Number) x).doubleValue() == 0);
    else
      zero =
          ((List<?>) spec.get("knots"))
              .stream()
                  .map(ExtendedOps::object)
                  .allMatch(
                      k ->
                          finite(k.get("y"), "y") == 0
                              && finite(k.get("d1"), "d1") == 0
                              && finite(k.get("d2"), "d2") == 0);
    return new Target(object(immutable(spec)), g.lo(), g.hi(), zero, x -> w(g.evaluate(x)));
  }

  private static EvaluationGraph.Node node(Extended.Signal source) {
    if (source instanceof Stage s) return s.node;
    if (source instanceof View v) return v.node;
    synchronized (NATIVE_NODES) {
      WeakReference<EvaluationGraph.Node> ref = NATIVE_NODES.get(source);
      EvaluationGraph.Node n = ref == null ? null : ref.get();
      if (n == null) {
        n =
            EvaluationGraph.opaque(
                source,
                (self, parents, x, h, mode) ->
                    h == null ? source.sampleNormalized(x) : source.atNormalized(x, h));
        NATIVE_NODES.put(source, new WeakReference<>(n));
      }
      return n;
    }
  }

  private abstract static class Adapted implements Extended.Signal {
    final Config config;
    final double from, end, latest;
    EvaluationGraph.Node node;

    Adapted(Config c, double from, double end, double latest) {
      this.config = c;
      this.from = from;
      this.end = end;
      this.latest = latest;
    }

    public Config config() {
      return config;
    }

    public double from() {
      return from;
    }

    public double domainEnd() {
      return end;
    }

    public double latestWrite() {
      return latest;
    }

    public double latestWriteTime() {
      return latest;
    }

    public double[] domain() {
      return new double[] {from, end};
    }

    public Map<String, Object> snapshot() {
      return ExtendedOps.snapshot(node, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> evaluate(double x, double[] h) {
      if (!Double.isFinite(x) || x < from || x > end)
        throw new IllegalArgumentException("Coordinate outside extended domain");
      return (Map<String, Object>)
          new EvaluationGraph().evaluate(node, x, h, EvaluationGraph.MODE_NORMALIZED);
    }

    public Map<String, Object> sampleNormalized(double x) {
      return evaluate(x, null);
    }

    public Map<String, Object> atNormalized(double x, double[] h) {
      return evaluate(x, Config.vector(h, 1e6));
    }

    public Sample sample(double x) {
      return Extended.raw(sampleNormalized(x));
    }

    public Map<String, Object> snapshotMap() {
      return ExtendedOps.snapshot(node, null);
    }

    public String snapshotJson() {
      return Vixen.stringifyTree(snapshotMap());
    }

    public Map<String, Object> tail(double from) {
      return ExtendedOps.snapshot(node, from);
    }

    public String tailJson(double from) {
      return Vixen.stringifyTree(tail(from));
    }

    public Extended.Signal fork(double at, String operation, Double width) {
      return forkGraph(this, at, operation, width == null ? Math.min(16, end - at) : width);
    }
  }

  private static final class Stage extends Adapted {
    final Extended.Signal parent;
    final String kind;
    final Map<String, Object> recipe;
    final double start, stop;
    final Target target;

    Stage(
        Extended.Signal parent,
        String kind,
        Map<String, Object> recipe,
        Target target,
        double end,
        double start,
        double stop,
        boolean identity) {
      super(parent.config(), parent.from(), end, Math.max(parent.latestWrite(), start));
      this.parent = parent;
      this.kind = kind;
      this.recipe = Collections.unmodifiableMap(new LinkedHashMap<>(recipe));
      this.target = target;
      this.start = start;
      this.stop = stop;
      node =
          EvaluationGraph.unary(
              this,
              node(parent),
              start,
              stop,
              identity,
              (self, p, x, h, mode) -> transform(object(p[0]), x));
    }

    private Map<String, Object> transform(Map<String, Object> p, double x) {
      if (kind.equals("influence"))
        return affected(
            p,
            target.evaluate.apply(x),
            weight(recipe, x),
            config,
            (String) recipe.get("reference"),
            (String) recipe.get("mode"));
      double delta = delta(recipe, x);
      if (delta == 0) return p;
      ScaledState.State s = ScaledState.decode(p);
      return ScaledState.sample(
          new ScaledState.State(
              p,
              s.base(),
              s.unconstrainedValue().add(s.base().mul(w(delta))),
              s.unconstrainedGuideValue(),
              s.floorValue(),
              s.ceilingValue(),
              s.boundaryWidth()));
    }
  }

  private static final class View extends Adapted {
    final Group group;
    final int index;

    View(Group group, int index) {
      super(group.sources.get(index).config(), group.from, group.end, group.latest);
      this.group = group;
      this.index = index;
      node =
          EvaluationGraph.unary(
              this,
              group.node,
              Double.NEGATIVE_INFINITY,
              Double.POSITIVE_INFINITY,
              false,
              (self, p, x, h, mode) -> ((List<?>) p[0]).get(index));
    }
  }

  public static final class Group implements Vixen.Restored {
    final List<Extended.Signal> sources;
    final double strength, start, stop, from, end, latest;
    final EvaluationGraph.Node node;
    final List<Extended.Signal> curves;

    Group(
        List<Extended.Signal> sources,
        double strength,
        double start,
        double stop,
        boolean restoring) {
      if (sources == null
          || sources.size() < 2
          || sources.size() > 32
          || sources.stream().anyMatch(Objects::isNull)
          || !Double.isFinite(strength)
          || strength < 0
          || strength > 1
          || !Double.isFinite(start)
          || !Double.isFinite(stop))
        throw new IllegalArgumentException("Invalid extended coupling");
      this.sources = List.copyOf(sources);
      this.strength = strength;
      this.start = start;
      this.stop = stop;
      from = sources.stream().mapToDouble(Extended.Signal::from).max().orElseThrow();
      end = sources.stream().mapToDouble(Extended.Signal::domainEnd).min().orElseThrow();
      double last = sources.stream().mapToDouble(Extended.Signal::latestWrite).max().orElse(1);
      latest = Math.max(last, start);
      if (from > end
          || start < (restoring ? 1 : Math.max(from, last))
          || stop <= start
          || stop > end)
        throw new IllegalArgumentException("Invalid coupling window or chronology");
      for (Extended.Signal s : sources)
        if (strength != 0
            && (s.config().floor() != Config.Floor.NONE || s.config().ceilingMargin() != null))
          throw new IllegalArgumentException("Coupling requires unconstrained sources");
      node =
          EvaluationGraph.multi(
              this,
              sources.stream().map(ExtendedOps::node).toList(),
              (self, parents, x, h, mode) -> coupled(parents, x));
      List<Extended.Signal> views = new ArrayList<>();
      for (int i = 0; i < sources.size(); i++) views.add(new View(this, i));
      curves = List.copyOf(views);
    }

    public List<Extended.Signal> curves() {
      return curves;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> sampleNormalized(double x) {
      if (!Double.isFinite(x) || x < from || x > end)
        throw new IllegalArgumentException("Outside coupling domain");
      return (List<Map<String, Object>>)
          new EvaluationGraph().evaluate(node, x, null, EvaluationGraph.MODE_NORMALIZED);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> atNormalized(double x, double[] h) {
      if (!Double.isFinite(x) || x < from || x > end)
        throw new IllegalArgumentException("Outside coupling domain");
      return (List<Map<String, Object>>)
          new EvaluationGraph()
              .evaluate(node, x, Config.vector(h, 1e6), EvaluationGraph.MODE_NORMALIZED);
    }

    private List<Map<String, Object>> coupled(Object[] parents, double x) {
      List<Map<String, Object>> p = new ArrayList<>();
      for (Object value : parents) p.add(object(value));
      double weight = strength * couplingGate(x, start, stop);
      if (weight == 0) return List.copyOf(p);
      List<ScaledState.State> states = p.stream().map(ScaledState::decode).toList();
      List<Wide> ratios = states.stream().map(s -> s.unconstrainedValue().div(s.base())).toList();
      Wide mean = ZERO;
      for (Wide r : ratios) mean = mean.add(r);
      mean = mean.div(w(p.size()));
      List<Map<String, Object>> out = new ArrayList<>();
      for (int i = 0; i < p.size(); i++) {
        if (ratios.get(i).cmp(mean) == 0) {
          out.add(p.get(i));
          continue;
        }
        ScaledState.State s = states.get(i);
        Wide u =
            weight == 1
                ? s.base().mul(mean)
                : s.unconstrainedValue().mul(w(1 - weight)).add(s.base().mul(mean).mul(w(weight)));
        out.add(
            ScaledState.sample(
                new ScaledState.State(
                    p.get(i),
                    s.base(),
                    u,
                    s.unconstrainedGuideValue(),
                    s.floorValue(),
                    s.ceilingValue(),
                    s.boundaryWidth())));
      }
      return List.copyOf(out);
    }

    public Map<String, Object> snapshotMap() {
      return ExtendedOps.snapshot(node, null);
    }

    public String snapshotJson() {
      return Vixen.stringifyTree(snapshotMap());
    }

    public String tailJson(double from) {
      return Vixen.stringifyTree(snapshot(node, from));
    }
  }

  public static Group couple(
      List<Extended.Signal> curves, double strength, double from, double until) {
    return new Group(curves, strength, from, until, false);
  }

  public static Extended.Signal influence(
      Extended.Signal source, Map<String, Object> guide, Map<String, Object> options) {
    return influence(source, guide, options, false);
  }

  private static Extended.Signal influence(
      Extended.Signal source,
      Map<String, Object> guide,
      Map<String, Object> options,
      boolean restoring) {
    fields(options, "at amount handoffWidth until releaseWidth reference mode", "");
    Target target = target(guide);
    double
        at =
            number(
                options,
                "at",
                Math.max(
                    source.config().openingEndpoint(),
                    Math.max(source.latestWrite(), source.from()))),
        amount = number(options, "amount", .5),
        limit = Math.min(source.domainEnd(), target.hi),
        width = number(options, "handoffWidth", Math.min(16, limit - at));
    String mode = (String) options.getOrDefault("mode", "blend"),
        reference =
            (String) options.getOrDefault("reference", mode.equals("add") ? "baseline" : "value");
    if (at < (restoring ? 1 : Math.max(source.from(), source.latestWrite()))
        || at > source.domainEnd()
        || amount < 0
        || amount > 1
        || width <= 0
        || width > 50000
        || at + width <= at
        || at + width > limit
        || target.lo > at
        || !Set.of("add", "blend").contains(mode)
        || !Set.of("value", "baseline", "backbone").contains(reference))
      throw new IllegalArgumentException("Invalid extended influence");
    boolean windowed = options.containsKey("until") || options.containsKey("releaseWidth");
    double stop = Double.POSITIVE_INFINITY;
    Map<String, Object> recipe = new LinkedHashMap<>();
    recipe.put("at", at);
    recipe.put("amount", amount);
    recipe.put("handoffWidth", width);
    recipe.put("reference", reference);
    recipe.put("mode", mode);
    recipe.put("guide", target.spec);
    if (windowed) {
      double until = finite(options.get("until"), "until"),
          release = finite(options.get("releaseWidth"), "releaseWidth");
      stop = until + release;
      if (until < at + width || release <= 0 || release > 50000 || stop <= until || stop > limit)
        throw new IllegalArgumentException("Invalid influence release");
      recipe.put("until", until);
      recipe.put("releaseWidth", release);
    }
    return new Stage(
        source,
        "influence",
        recipe,
        target,
        windowed ? source.domainEnd() : limit,
        at,
        stop,
        amount == 0 || (mode.equals("add") && target.zero));
  }

  public static Extended.Signal impulse(Extended.Signal source, Map<String, Object> input) {
    return operation(source, "impulse", input, false);
  }

  public static Extended.Signal redistribute(Extended.Signal source, Map<String, Object> input) {
    return operation(source, "redistribution", input, false);
  }

  private static Extended.Signal operation(
      Extended.Signal source, String kind, Map<String, Object> input, boolean restoring) {
    Map<String, Object> r = new LinkedHashMap<>();
    double start, stop, magnitude;
    boolean zero;
    if (kind.equals("impulse")) {
      fields(input, "type at magnitude relaxation attack", "at magnitude relaxation");
      if (input.containsKey("type") && !kind.equals(input.get("type")))
        throw new IllegalArgumentException("Operation mismatch");
      start = finite(input.get("at"), "at");
      magnitude = finite(input.get("magnitude"), "magnitude");
      double relaxation = finite(input.get("relaxation"), "relaxation"),
          attack = number(input, "attack", relaxation / 8),
          rise = start + attack;
      stop = rise + relaxation;
      if (attack <= 0 || relaxation <= 0 || rise <= start || stop <= rise)
        throw new IllegalArgumentException("Invalid impulse support");
      r.put("type", kind);
      r.put("at", start);
      r.put("magnitude", magnitude);
      r.put("relaxation", relaxation);
      r.put("attack", attack);
      zero = magnitude == 0;
    } else {
      fields(
          input,
          "type from until coefficients magnitude measure conservation certificate",
          "from until coefficients");
      if (input.containsKey("type") && !kind.equals(input.get("type")))
        throw new IllegalArgumentException("Operation mismatch");
      start = finite(input.get("from"), "from");
      stop = finite(input.get("until"), "until");
      magnitude = number(input, "magnitude", 1);
      String measure = (String) input.getOrDefault("measure", "actualDeviation");
      double[] coefficients = numbers(input.get("coefficients"), 64);
      if (!Set.of("actualDeviation", "unconstrainedDeviation").contains(measure) || stop <= start)
        throw new IllegalArgumentException("Invalid redistribution");
      if (input.containsKey("conservation") && !CONSERVATION.equals(input.get("conservation")))
        throw new IllegalArgumentException("Invalid conservation metadata");
      if (measure.equals("actualDeviation")
          && magnitude != 0
          && (source.config().floor() != Config.Floor.NONE
              || source.config().ceilingMargin() != null))
        throw new IllegalArgumentException(
            "Actual-deviation redistribution requires an unconstrained source");
      double radius = 0;
      for (double x : derivative(product(FACTOR, coefficients))) radius += Math.abs(x);
      if (!Double.isFinite(radius * Math.abs(magnitude)))
        throw new IllegalArgumentException("Redistribution bound overflow");
      r.put("type", kind);
      r.put("from", start);
      r.put("until", stop);
      r.put("magnitude", magnitude);
      r.put("coefficients", list(coefficients));
      r.put("measure", measure);
      r.put("conservation", CONSERVATION);
      zero = magnitude == 0 || Arrays.stream(coefficients).allMatch(x -> x == 0);
    }
    if (!Double.isFinite(stop)
        || start < (restoring ? 1 : Math.max(source.from(), source.latestWrite()))
        || stop > source.domainEnd()
        || stop <= source.from())
      throw new IllegalArgumentException("Invalid operation domain or chronology");
    return new Stage(source, kind, r, null, source.domainEnd(), start, stop, zero);
  }

  private static double smooth(double t) {
    return t <= 0 ? 0 : t >= 1 ? 1 : t * t * t * (10 + t * (-15 + 6 * t));
  }

  private static double ramp(double t) {
    return t <= 0 ? 0 : t >= 1 ? 1 : t * t * t * (6 + t * (-8 + 3 * t));
  }

  private static double couplingGate(double x, double a, double b) {
    if (x <= a || x >= b) return 0;
    double mid = a + (b - a) / 2, t = x <= mid ? (x - a) / (mid - a) : (b - x) / (b - mid);
    return Math.max(0, Math.min(1, smooth(t)));
  }

  private static double weight(Map<String, Object> r, double x) {
    double at = finite(r.get("at"), "at"),
        a = finite(r.get("amount"), "amount"),
        width = finite(r.get("handoffWidth"), "handoffWidth");
    if (x <= at) return 0;
    if (!r.containsKey("until")) return a * smooth((x - at) / width);
    if (x < at + width) return a * smooth((x - at) / width);
    double until = finite(r.get("until"), "until"),
        release = finite(r.get("releaseWidth"), "releaseWidth");
    return x <= until ? a : x < until + release ? a * (1 - smooth((x - until) / release)) : 0;
  }

  private static double[] product(double[] a, double[] b) {
    double[] out = new double[a.length + b.length - 1];
    for (int i = 0; i < a.length; i++)
      for (int j = 0; j < b.length; j++) {
        out[i + j] += a[i] * b[j];
        if (!Double.isFinite(out[i + j])) throw new IllegalArgumentException("Polynomial overflow");
      }
    return out;
  }

  private static double[] derivative(double[] a) {
    if (a.length < 2) return new double[] {0};
    double[] out = new double[a.length - 1];
    for (int i = 1; i < a.length; i++) out[i - 1] = a[i] * i;
    return out;
  }

  private static double horner(double[] a, double t) {
    double y = 0;
    for (int i = a.length - 1; i >= 0; i--) y = y * t + a[i];
    return y;
  }

  private static double delta(Map<String, Object> r, double x) {
    double magnitude = finite(r.get("magnitude"), "magnitude");
    if (magnitude == 0) return 0;
    if (r.get("type").equals("impulse")) {
      double at = finite(r.get("at"), "at"),
          rise = at + finite(r.get("attack"), "attack"),
          end = rise + finite(r.get("relaxation"), "relaxation");
      if (x <= at || x >= end) return 0;
      return x <= rise
          ? magnitude * smooth((x - at) / finite(r.get("attack"), "attack"))
          : magnitude * (1 - smooth((x - rise) / finite(r.get("relaxation"), "relaxation")));
    }
    double from = finite(r.get("from"), "from"), until = finite(r.get("until"), "until");
    if (x <= from || x >= until) return 0;
    double t = (x - from) / (until - from);
    double[] c = numbers(r.get("coefficients"), 64);
    return magnitude
        * (horner(derivative(FACTOR), t) * horner(c, t)
            + horner(FACTOR, t) * horner(derivative(c), t));
  }

  private static Map<String, Object> affected(
      Map<String, Object> p,
      Wide target,
      double amount,
      Config config,
      String reference,
      String mode) {
    if (amount == 0) return p;
    ScaledState.State s = ScaledState.decode(p);
    Wide base = s.base(),
        width = s.boundaryWidth(),
        extra = base.mul(w(amount)).mul(w(config.boundarySoftness()));
    if (s.floorValue() != null && s.ceilingValue() != null) {
      Wide room =
          s.ceilingValue().sub(s.floorValue()).scale(java.math.BigInteger.valueOf(-1)).sub(width);
      if (room.sign() > 0 && extra.sign() > 0)
        width = width.add(extra.mul(room).div(room.add(extra)));
    } else if (s.floorValue() != null || s.ceilingValue() != null) width = width.add(extra);
    Wide u = s.unconstrainedValue();
    if (mode.equals("add"))
      u =
          u.add(
              reference.equals("value") ? target.mul(w(amount)) : base.mul(w(amount)).mul(target));
    else {
      Wide t =
          reference.equals("value")
              ? target
              : reference.equals("baseline")
                  ? base.mul(ONE.add(target))
                  : s.unconstrainedGuideValue().add(base.mul(target));
      u = amount == 1 ? t : u.mul(w(1 - amount)).add(t.mul(w(amount)));
    }
    return ScaledState.sample(
        new ScaledState.State(
            p, base, u, s.unconstrainedGuideValue(), s.floorValue(), s.ceilingValue(), width));
  }

  private static EvaluationGraph.Node effective(EvaluationGraph.Node node, Double from) {
    if (from == null) return node;
    while (true) {
      Object owner = node.ownerPayload();
      if (owner instanceof Stage s && Double.isFinite(s.stop) && from >= s.stop) {
        node = node(s.parent);
        continue;
      }
      if (owner instanceof View v && from >= v.group.stop) {
        node = node(v.group.sources.get(v.index));
        continue;
      }
      return node;
    }
  }

  private record Visit(EvaluationGraph.Node node, boolean expanded) {}

  private static Map<String, Object> snapshot(EvaluationGraph.Node root, Double from) {
    if (from != null) {
      Object owner = root.ownerPayload();
      double lo, hi;
      if (owner instanceof Group g) {
        lo = g.from;
        hi = g.end;
      } else {
        Extended.Signal s = (Extended.Signal) owner;
        lo = s.from();
        hi = s.domainEnd();
      }
      if (!Double.isFinite(from) || from < lo || from > hi)
        throw new IllegalArgumentException("Invalid checkpoint coordinate");
    }
    root = effective(root, from);
    IdentityHashMap<EvaluationGraph.Node, Integer> ids = new IdentityHashMap<>();
    List<Map<String, Object>> frames = new ArrayList<>();
    ArrayDeque<Visit> stack = new ArrayDeque<>();
    stack.push(new Visit(root, false));
    while (!stack.isEmpty()) {
      Visit v = stack.pop();
      EvaluationGraph.Node n = effective(v.node, from);
      if (ids.containsKey(n)) continue;
      if (!v.expanded) {
        stack.push(new Visit(n, true));
        List<EvaluationGraph.Node> parents = n.parents();
        for (int i = parents.size() - 1; i >= 0; i--) {
          EvaluationGraph.Node p = effective(parents.get(i), from);
          if (!ids.containsKey(p)) stack.push(new Visit(p, false));
        }
        continue;
      }
      List<Integer> parents = n.parents().stream().map(p -> ids.get(effective(p, from))).toList();
      Map<String, Object> frame;
      Object owner = n.ownerPayload();
      if (owner instanceof Stage s)
        frame =
            Map.of(
                "algorithm",
                ALGORITHM,
                "operation",
                s.kind,
                "curve",
                parents.get(0),
                "recipe",
                s.recipe);
      else if (owner instanceof View view)
        frame =
            Map.of(
                "algorithm",
                COUPLING_ALGORITHM,
                "operation",
                "view",
                "group",
                parents.get(0),
                "index",
                view.index);
      else if (owner instanceof Group group)
        frame =
            Map.of(
                "algorithm",
                COUPLING_ALGORITHM,
                "operation",
                "group",
                "curves",
                parents,
                "strength",
                group.strength,
                "window",
                List.of(group.start, group.stop));
      else {
        Extended.Signal s = (Extended.Signal) owner;
        frame = from == null ? s.snapshotMap() : s.tail(from);
      }
      ids.put(n, frames.size());
      frames.add(frame);
      if (frames.size() > 1_000_000)
        throw new IllegalStateException("Snapshot node budget exceeded");
    }
    if (frames.size() == 1) return frames.get(0);
    return Map.of(
        "algorithm",
        CurveEvaluation.GRAPH_ALGORITHM,
        "nodes",
        List.copyOf(frames),
        "root",
        ids.get(root));
  }

  private static Extended.Signal forkGraph(
      Extended.Signal root, double at, String operation, double width) {
    if (!Double.isFinite(at)
        || at < Math.max(root.from(), root.latestWrite())
        || !Double.isFinite(width)
        || width <= 0
        || width > 50000
        || at + width <= at
        || at + width > root.domainEnd())
      throw new IllegalArgumentException("Invalid fork chronology or support");
    List<EvaluationGraph.Node> order = new EvaluationGraph().topologicalOrder(node(root));
    for (EvaluationGraph.Node n : order)
      if (n.ownerPayload() instanceof Group)
        throw new IllegalArgumentException(
            "Fork input lineages before coupling; projection forks are ambiguous");
    IdentityHashMap<EvaluationGraph.Node, Extended.Signal> out = new IdentityHashMap<>();
    for (EvaluationGraph.Node n : order) {
      Object owner = n.ownerPayload();
      Extended.Signal result;
      if (owner instanceof Stage s) {
        Extended.Signal parent = out.get(node(s.parent));
        result =
            s.kind.equals("influence")
                ? influence(parent, s.target.spec, withoutGuide(s.recipe), true)
                : operation(parent, s.kind, s.recipe, true);
      } else result = ((Extended.Signal) owner).fork(at, operation, width);
      out.put(n, result);
    }
    return out.get(node(root));
  }

  private static Map<String, Object> withoutGuide(Map<String, Object> recipe) {
    Map<String, Object> o = new LinkedHashMap<>(recipe);
    o.remove("guide");
    return o;
  }

  public static boolean accepts(Object algorithm) {
    return ALGORITHM.equals(algorithm) || COUPLING_ALGORITHM.equals(algorithm);
  }

  public static Vixen.Restored restore(
      Map<String, Object> frame, Function<Object, Vixen.Restored> read) {
    Object algorithm = frame.get("algorithm"), operation = frame.get("operation");
    if (COUPLING_ALGORITHM.equals(algorithm)) {
      if ("group".equals(operation)) {
        fields(
            frame,
            "algorithm operation curves strength window",
            "algorithm operation curves strength window");
        if (!(frame.get("curves") instanceof List<?> sources)
            || !(frame.get("window") instanceof List<?> window)
            || window.size() != 2) throw new IllegalArgumentException("Invalid coupling recipe");
        List<Extended.Signal> curves = new ArrayList<>();
        for (Object source : sources) curves.add(signal(read.apply(source)));
        return new Group(
            curves,
            finite(frame.get("strength"), "strength"),
            finite(window.get(0), "window"),
            finite(window.get(1), "window"),
            true);
      }
      if ("view".equals(operation)) {
        fields(frame, "algorithm operation group index", "algorithm operation group index");
        Vixen.Restored group = read.apply(frame.get("group"));
        double index = finite(frame.get("index"), "index");
        if (!(group instanceof Group g)
            || index != Math.rint(index)
            || index < 0
            || index >= g.curves.size())
          throw new IllegalArgumentException("Invalid coupling view");
        return g.curves.get((int) index);
      }
      throw new IllegalArgumentException("Unknown coupling operation");
    }
    if (!ALGORITHM.equals(algorithm))
      throw new IllegalArgumentException("Unknown extended operation algorithm");
    fields(frame, "algorithm operation curve recipe", "algorithm operation curve recipe");
    Extended.Signal source = signal(read.apply(frame.get("curve")));
    Map<String, Object> recipe = object(frame.get("recipe"));
    if ("influence".equals(operation)) {
      fields(
          recipe,
          "guide at amount handoffWidth reference mode until releaseWidth",
          "guide at amount handoffWidth reference mode");
      return influence(source, object(recipe.get("guide")), withoutGuide(recipe), true);
    }
    if ("impulse".equals(operation)) {
      fields(recipe, "type at magnitude relaxation attack", "type at magnitude relaxation attack");
      return operation(source, "impulse", recipe, true);
    }
    if ("redistribution".equals(operation)) {
      fields(
          recipe,
          "type from until coefficients magnitude measure conservation",
          "type from until coefficients magnitude measure conservation");
      return operation(source, "redistribution", recipe, true);
    }
    throw new IllegalArgumentException("Unknown extended operation");
  }

  private static Extended.Signal signal(Vixen.Restored value) {
    if (!(value instanceof Extended.Signal signal))
      throw new IllegalArgumentException("Extended operation requires an extended signal");
    return signal;
  }
}
