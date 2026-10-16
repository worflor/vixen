package dev.woflo.vixen;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ScaledState {
  private ScaledState() {}

  private static final Wide ZERO = w(0), ONE = w(1), HALF = w(.5);

  private static Wide w(double x) {
    return Wide.fromDouble(x);
  }

  public record State(
      Map<String, Object> metadata,
      Wide base,
      Wide unconstrainedValue,
      Wide unconstrainedGuideValue,
      Wide floorValue,
      Wide ceilingValue,
      Wide boundaryWidth) {
    public State {
      metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
      if (base == null
          || base.sign() <= 0
          || unconstrainedValue == null
          || unconstrainedGuideValue == null
          || boundaryWidth == null
          || boundaryWidth.sign() < 0) throw new IllegalArgumentException("Invalid scaled state");
    }
  }

  private static Wide ramp(Wide t) {
    return t.mul(t).mul(t).mul(w(6).add(t.mul(w(-8).add(t.mul(w(3))))));
  }

  private static Wide floor(Wide y, Wide lo, Wide e) {
    if (y.cmp(lo) <= 0) return lo;
    if (e.sign() == 0 || y.cmp(lo.add(e)) >= 0) return y;
    Wide t = y.sub(lo).div(e);
    if (t.cmp(HALF) <= 0) return lo.add(e.mul(ramp(t)));
    Wide q = ONE.sub(t);
    return y.sub(e.mul(t).mul(q).mul(q).mul(q).mul(t.mul(w(3)).add(ONE)));
  }

  public static Wide policy(Wide y, Wide lo, Wide hi, Wide e) {
    if (y == null || e == null || e.sign() < 0)
      throw new IllegalArgumentException("Invalid policy input");
    if (lo != null && hi != null && hi.cmp(lo) <= 0) return lo;
    Wide out = lo == null ? y : floor(y, lo, e);
    if (hi == null) return out;
    if (out.cmp(hi) >= 0) return hi;
    if (e.sign() == 0 || out.cmp(hi.sub(e)) <= 0) return out;
    Wide s = out.sub(hi.sub(e)).div(e);
    if (s.cmp(HALF) <= 0)
      return out.add(e.mul(ONE.sub(s)).mul(s).mul(s).mul(s).mul(w(4).sub(s.mul(w(3)))));
    return hi.sub(e.mul(ramp(hi.sub(out).div(e))));
  }

  private static Double log(Wide x) {
    if (x.sign() == 0) return null;
    double power = x.e().add(BigInteger.valueOf(52)).doubleValue();
    double value =
        StrictMath.log(Math.scalb(x.m().abs().doubleValue(), -52)) + power * StrictMath.log(2);
    return Double.isFinite(value) ? value : null;
  }

  private static Map<String, Object> signedLog(Wide x) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("sign", x.sign());
    out.put("logAbs", log(x));
    return Collections.unmodifiableMap(out);
  }

  private static double tanh(Wide x) {
    Double n = x.toDouble();
    return n != null ? StrictMath.tanh(n) : x.e().signum() > 0 ? x.sign() : 0;
  }

  private static void absolute(Map<String, Object> out, String key, Wide value) {
    out.put(key, value == null ? null : value.toDouble());
    out.put(
        "scaled" + Character.toUpperCase(key.charAt(0)) + key.substring(1),
        value == null ? null : value.toMap());
  }

  private static void deviation(Map<String, Object> out, String key, Wide value) {
    out.put(key, value.toDouble());
    out.put("scaled" + Character.toUpperCase(key.charAt(0)) + key.substring(1), value.toMap());
  }

  public static Map<String, Object> sample(State s) {
    String[] required = {"x", "score", "guideScore", "gate", "rawDeviation", "stabilizationWeight"};
    for (String name : required)
      if (!(s.metadata().get(name) instanceof Number n) || !Double.isFinite(n.doubleValue()))
        throw new IllegalArgumentException("Invalid scalar metadata: " + name);
    Wide base = s.base(),
        value = policy(s.unconstrainedValue(), s.floorValue(), s.ceilingValue(), s.boundaryWidth()),
        guide =
            policy(
                s.unconstrainedGuideValue(), s.floorValue(), s.ceilingValue(), s.boundaryWidth());
    Wide ratio = value.div(base), d = ratio.sub(ONE), g = guide.div(base).sub(ONE);
    double bounded = tanh(d), positive = StrictMath.exp(.55 * bounded);
    Map<String, Object> out = new LinkedHashMap<>(s.metadata());
    absolute(out, "base", base);
    absolute(out, "value", value);
    absolute(out, "guideValue", guide);
    absolute(out, "unconstrainedValue", s.unconstrainedValue());
    absolute(out, "unconstrainedGuideValue", s.unconstrainedGuideValue());
    absolute(out, "positiveValue", base.mul(w(positive)));
    absolute(out, "floorValue", s.floorValue());
    absolute(out, "ceilingValue", s.ceilingValue());
    absolute(out, "boundaryWidth", s.boundaryWidth());
    deviation(out, "deviation", d);
    deviation(out, "guideDeviation", g);
    deviation(out, "unconstrainedDeviation", s.unconstrainedValue().div(base).sub(ONE));
    deviation(out, "unconstrainedGuideDeviation", s.unconstrainedGuideValue().div(base).sub(ONE));
    out.put("logBase", log(base));
    out.put("signedLogValue", signedLog(value));
    out.put("multiplier", ratio.toDouble());
    out.put("activity", .5 + .5 * bounded);
    out.put("positiveMultiplier", positive);
    out.put("floorRatio", s.floorValue() == null ? null : s.floorValue().div(base).toDouble());
    out.put(
        "ceilingRatio", s.ceilingValue() == null ? null : s.ceilingValue().div(base).toDouble());
    out.put("boundaryWidthRatio", s.boundaryWidth().div(base).toDouble());
    out.put("constraintApplied", value.cmp(s.unconstrainedValue()) != 0);
    return Collections.unmodifiableMap(out);
  }

  public static State fromScalar(Sample p) {
    return new State(
        p.asMap(),
        w(p.base()),
        w(p.unconstrainedValue()),
        w(p.unconstrainedGuideValue()),
        p.floorValue() == null ? null : w(p.floorValue()),
        p.ceilingValue() == null ? null : w(p.ceilingValue()),
        w(p.boundaryWidth()));
  }

  public static Map<String, Object> sampleFromScalar(Sample p) {
    Map<String, Object> out = new LinkedHashMap<>(sample(fromScalar(p)));
    out.putAll(p.asMap());
    for (String key :
        new String[] {
          "base",
          "value",
          "guideValue",
          "positiveValue",
          "unconstrainedValue",
          "unconstrainedGuideValue",
          "floorValue",
          "ceilingValue",
          "boundaryWidth"
        }) {
      Object v = p.asMap().get(key);
      out.put(
          "scaled" + Character.toUpperCase(key.charAt(0)) + key.substring(1),
          v == null ? null : w(((Number) v).doubleValue()).toMap());
    }
    out.put("logBase", StrictMath.log(p.base()));
    out.put("signedLogValue", signedLog(w(p.value())));
    return Collections.unmodifiableMap(out);
  }

  @SuppressWarnings("unchecked")
  public static State decode(Map<String, Object> p) {
    return new State(
        p,
        decodeField(p, "base"),
        decodeField(p, "unconstrainedValue"),
        decodeField(p, "unconstrainedGuideValue"),
        decodeField(p, "floorValue"),
        decodeField(p, "ceilingValue"),
        decodeField(p, "boundaryWidth"));
  }

  @SuppressWarnings("unchecked")
  private static Wide decodeField(Map<String, Object> p, String k) {
    Object v = p.get("scaled" + Character.toUpperCase(k.charAt(0)) + k.substring(1));
    if (v == null) return null;
    return Wide.fromMap((Map<String, ?>) v);
  }
}
