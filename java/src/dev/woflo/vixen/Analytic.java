package dev.woflo.vixen;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** First- and second-order analytic jets for native leaves. */
public final class Analytic {
    private Analytic() {
    }

    /** A numeric sample field and its independently available derivatives. */
    public record FieldJet(double value, Double d1, Double d2) {
    }

    /** Whether the value's first and second derivatives are available. */
    public record Availability(boolean slope, boolean curvature) {
    }

    /** Differentiability of the value and of all numeric sample fields. */
    public record Differentiability(boolean value, boolean sample, String rationale) {
    }

    /** The original sample together with its analytic field jets. */
    public record Result(Sample sample, Map<String, FieldJet> fields, Double slope, Double curvature,
                         Availability availability, Differentiability differentiability) {
        public Result {
            Objects.requireNonNull(sample, "sample");
            Objects.requireNonNull(fields, "fields");
            Objects.requireNonNull(availability, "availability");
            Objects.requireNonNull(differentiability, "differentiability");
            fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        }

        /** The shared explanation for unavailable derivatives, if one applies. */
        public String rationale() {
            return differentiability.rationale();
        }
    }

    /** Analytic derivatives at the leaf's configured latent position. */
    public static Result leaf(Leaf curve, double x) {
        return evaluate(curve, x, null, false);
    }

    /** Analytic derivatives at an explicit sixteen-dimensional latent position. */
    public static Result leaf(Leaf curve, double x, double[] coordinates) {
        return evaluate(curve, x, coordinates, true);
    }

    private static Result evaluate(Leaf curve, double x, double[] coordinates, boolean explicitCoordinates) {
        if (curve == null) {
            throw new IllegalArgumentException("A native leaf is required");
        }
        if (!Double.isFinite(x)) {
            throw new IllegalArgumentException("x must be finite");
        }
        Config c = curve.config();
        double[] h = explicitCoordinates ? Config.vector(coordinates, 1_000_000) : null;
        Sample sample = explicitCoordinates ? curve.at(x, h) : curve.sample(x);
        int seed = c.seed().u32();
        Ridges.Ridge[] ridges = Ridges.of(c, seed);
        Opening.Dip[] dips = Opening.dips(seed, c.introVariation());

        Jet X = new Jet(x, 1, 0);
        double endpoint = c.openingEndpoint();
        boolean scoreJump = x == endpoint;
        Jet score = constant(0);
        Jet guideScore = constant(0);
        Jet clock = endpoint == 100
                ? X
                : add(constant(1), mul(sub(X, 1), 99 / (endpoint - 1)));

        if (x > endpoint) {
            Jet u = sub(clock, 100);
            for (Ridges.Ridge ridge : ridges) {
                Jet p;
                Jet g;
                if (!explicitCoordinates) {
                    p = add(constant(ridge.phase()), mul(u, ridge.slope()));
                    g = add(constant(ridge.guidePhase()), mul(u, ridge.guideSlope()));
                } else {
                    double projection = 0;
                    double[] direction = ridge.direction();
                    for (int i = 0; i < 16; i++) {
                        projection += direction[i] * h[i];
                    }
                    p = add(add(constant(ridge.basePhase()), projection), div(u, ridge.scale()));
                    g = add(add(constant(ridge.basePhase()), projection / 32), div(u, 32 * ridge.scale()));
                }
                score = add(score, mul(noise(p, seed, ridge.channel()), 1.0 / 48));
                guideScore = add(guideScore, mul(noise(g, seed, ridge.channel()), 1.0 / 48));
            }
        }

        Jet gate = smooth5(div(sub(X, endpoint), c.ramp()));
        Jet raw = mul(mul(mul(gate, c.strength() * 2.2), tanh(mul(score, c.contrast()))), 1);
        Jet unconstrainedGuideDeviation = mul(mul(gate, c.strength() * .25), tanh(mul(guideScore, 2)));
        double stabilizationWeight = Opening.smooth5(c.stabilizer());
        Jet unconstrainedDeviation = stabilizationWeight == 0
                ? raw
                : stabilizationWeight == 1
                    ? unconstrainedGuideDeviation
                    : add(raw, mul(sub(unconstrainedGuideDeviation, raw), stabilizationWeight));

        Jet base = authored(clock, c, dips);
        Jet unconstrainedValue = add(base, mul(base, unconstrainedDeviation));
        Jet unconstrainedGuideValue = add(base, mul(base, unconstrainedGuideDeviation));

        Jet floor = c.floor() == Config.Floor.NONE
                ? null
                : c.floor() == Config.Floor.ZERO
                    ? constant(0)
                    : c.floor() == Config.Floor.BASELINE
                        ? base
                        : unconstrainedGuideValue;
        Jet ceiling = c.ceilingMargin() == null
                ? null
                : add(c.floor() == Config.Floor.BASELINE ? base : unconstrainedGuideValue,
                      mul(base, c.ceilingMargin()));

        Jet a = mul(gate, c.strength() * c.boundarySoftness());
        Jet boundaryWidth = constant(0);
        if (floor != null && ceiling != null) {
            Jet gap = div(sub(ceiling, floor), base);
            boundaryWidth = gap.value <= 0 || a.value == 0
                    ? constant(0)
                    : mul(base, div(mul(a, gap), add(gap, mul(a, 2))));
        } else if (floor != null || ceiling != null) {
            boundaryWidth = mul(base, a);
        }

        Applied valueApplied = applyBoundary(unconstrainedValue, floor, ceiling, boundaryWidth);
        Applied guideApplied = applyBoundary(unconstrainedGuideValue, floor, ceiling, boundaryWidth);
        Jet value = valueApplied.value();
        Jet guideValue = guideApplied.value();
        boolean constrained = c.floor() != Config.Floor.NONE || c.ceilingMargin() != null;
        Jet deviation = constrained ? sub(div(value, base), 1) : unconstrainedDeviation;
        Jet guideDeviation = constrained ? sub(div(guideValue, base), 1) : unconstrainedGuideDeviation;
        Jet positiveMultiplier = exp(mul(tanh(deviation), .55));

        Map<String, Jet> jets = new LinkedHashMap<>();
        jets.put("x", X);
        jets.put("value", value);
        jets.put("base", base);
        jets.put("score", score);
        jets.put("guideScore", guideScore);
        jets.put("rawDeviation", raw);
        jets.put("guideDeviation", guideDeviation);
        jets.put("guideValue", guideValue);
        jets.put("stabilizationWeight", constant(Opening.smooth5(c.stabilizer())));
        jets.put("deviation", deviation);
        jets.put("multiplier", add(deviation, 1));
        jets.put("activity", add(mul(tanh(deviation), .5), .5));
        jets.put("positiveMultiplier", positiveMultiplier);
        jets.put("positiveValue", mul(base, positiveMultiplier));
        jets.put("gate", gate);
        jets.put("unconstrainedValue", unconstrainedValue);
        jets.put("unconstrainedDeviation", unconstrainedDeviation);
        jets.put("unconstrainedGuideValue", unconstrainedGuideValue);
        jets.put("unconstrainedGuideDeviation", unconstrainedGuideDeviation);
        jets.put("floorValue", floor);
        jets.put("ceilingValue", ceiling);
        jets.put("boundaryWidth", boundaryWidth);

        Set<String> unavailable = new LinkedHashSet<>();
        if (valueApplied.hard()) {
            Collections.addAll(unavailable, "value", "deviation", "multiplier", "activity", "positiveMultiplier", "positiveValue");
        }
        if (guideApplied.hard()) {
            Collections.addAll(unavailable, "guideValue", "guideDeviation");
        }
        if (scoreJump) {
            unavailable.add("score");
            unavailable.add("guideScore");
        }

        Map<String, FieldJet> fields = new LinkedHashMap<>();
        boolean allSampleDerivatives = true;
        for (Map.Entry<String, Object> entry : sample.asMap().entrySet()) {
            if (!(entry.getValue() instanceof Number number)) {
                continue;
            }
            Jet jet = jets.get(entry.getKey());
            boolean valid = jet != null && !unavailable.contains(entry.getKey());
            Double d1 = valid && Double.isFinite(jet.d1) ? jet.d1 : null;
            Double d2 = valid && Double.isFinite(jet.d2) ? jet.d2 : null;
            fields.put(entry.getKey(), new FieldJet(number.doubleValue(), d1, d2));
            allSampleDerivatives &= d1 != null && d2 != null;
        }

        FieldJet valueField = fields.get("value");
        Double slope = valueField == null ? null : valueField.d1();
        Double curvature = valueField == null ? null : valueField.d2();
        Availability availability = new Availability(slope != null, curvature != null);
        String rationale = valueApplied.hard()
                ? "A zero-width constraint is evaluated at its hard boundary."
                : scoreJump
                    ? "The score branch changes at the opening endpoint; score field derivatives are undefined there."
                    : null;
        Differentiability differentiability = new Differentiability(
                availability.slope() && availability.curvature(), allSampleDerivatives, rationale);
        return new Result(sample, fields, slope, curvature, availability, differentiability);
    }

    private static Jet authored(Jet t, Config c, Opening.Dip[] dips) {
        Jet a = sub(t, 42);
        Jet b = sub(t, 71);
        Jet old = sub(
                sub(mul(t, exp(div(t, 100))), mul(exp(neg(div(mul(a, a), 32))), 25)),
                mul(exp(neg(div(mul(b, b), 28))), 38));
        if (c.introVariation() == 0 || t.value >= 100) {
            return old;
        }
        Jet fresh = mul(t, exp(div(t, 100)));
        for (Opening.Dip dip : dips) {
            Jet z = div(sub(t, dip.center()), dip.width());
            fresh = sub(fresh, mul(exp(neg(mul(z, z))), dip.depth()));
        }
        Jet weight = sub(constant(1), smooth5(div(sub(t, 90), 10)));
        return add(old, mul(sub(fresh, old), weight));
    }

    private static Jet noise(Jet p, int seed, int channel) {
        double k = StrictMath.floor(p.value);
        Jet t = sub(p, k);
        double a = 2 * Hash.unit(seed, Hash.toUint32(k), channel) - 1;
        double b = 2 * Hash.unit(seed, Hash.toUint32(k + 1), channel) - 1;
        return add(constant(a), mul(sub(constant(b), constant(a)), smooth5(t)));
    }

    private record Jet(double value, double d1, double d2) {
    }

    private record Applied(Jet value, boolean hard) {
    }

    private static Jet constant(double value) {
        return new Jet(value, 0, 0);
    }

    private static Jet add(Jet a, Jet b) {
        return new Jet(a.value + b.value, a.d1 + b.d1, a.d2 + b.d2);
    }

    private static Jet add(Jet a, double b) {
        return add(a, constant(b));
    }

    private static Jet sub(Jet a, Jet b) {
        return new Jet(a.value - b.value, a.d1 - b.d1, a.d2 - b.d2);
    }

    private static Jet sub(Jet a, double b) {
        return sub(a, constant(b));
    }

    private static Jet mul(Jet a, Jet b) {
        double d1 = a.d1 * b.value + a.value * b.d1;
        double d2 = a.d2 * b.value + 2 * a.d1 * b.d1 + a.value * b.d2;
        if (finite(a) && finite(b)) {
            if (!Double.isFinite(d1)) {
                d1 = Floating.sumProducts(new double[][] {{a.d1, b.value}, {a.value, b.d1}});
            }
            if (!Double.isFinite(d2)) {
                d2 = Floating.sumProducts(new double[][] {{a.d2, b.value}, {2, a.d1, b.d1}, {a.value, b.d2}});
            }
        }
        return new Jet(a.value * b.value, d1, d2);
    }

    private static Jet mul(Jet a, double b) {
        return mul(a, constant(b));
    }

    private static Jet div(Jet a, Jet b) {
        double value = a.value / b.value;
        double d1 = (a.d1 - value * b.d1) / b.value;
        double d2 = (a.d2 - value * b.d2 - 2 * d1 * b.d1) / b.value;
        if (b.value != 0 && finite(a) && finite(b)) {
            if (!Double.isFinite(d1)) {
                d1 = exactSumProductsQuotient(
                        new double[][] {{a.d1, b.value}, {-1, a.value, b.d1}},
                        new double[] {b.value, b.value});
            }
            if (!Double.isFinite(d2)) {
                d2 = exactSumProductsQuotient(
                        new double[][] {{a.d2, b.value, b.value}, {-1, a.value, b.d2, b.value},
                                {-2, a.d1, b.value, b.d1}, {2, a.value, b.d1, b.d1}},
                        new double[] {b.value, b.value, b.value});
            }
        }
        return new Jet(value, d1, d2);
    }

    private static Jet div(Jet a, double b) {
        return div(a, constant(b));
    }

    private static Jet neg(Jet a) {
        return mul(a, -1);
    }

    private static Jet unary(Jet a, double value, double first, double second) {
        return new Jet(value, first * a.d1, first * a.d2 + second * a.d1 * a.d1);
    }

    private static Jet exp(Jet a) {
        double value = StrictMath.exp(a.value);
        return unary(a, value, value, value);
    }

    private static Jet tanh(Jet a) {
        double value = StrictMath.tanh(a.value);
        double z = StrictMath.exp(-2 * StrictMath.abs(a.value));
        double first = 4 * z / ((1 + z) * (1 + z));
        return unary(a, value, first, -2 * value * first);
    }

    private static Jet smooth5(Jet a) {
        if (a.value <= 0) {
            return constant(0);
        }
        if (a.value >= 1) {
            return constant(1);
        }
        double t = a.value;
        double value = t * t * t * (10 + t * (-15 + 6 * t));
        double first = 30 * t * t * (t - 1) * (t - 1);
        double second = 60 * t * (t - 1) * (2 * t - 1);
        return unary(a, value, first, second);
    }

    private static Jet boundaryRamp(Jet a) {
        if (a.value <= 0) {
            return constant(0);
        }
        if (a.value >= 1) {
            return a;
        }
        double t = a.value;
        double value = t * t * t * (6 + t * (-8 + 3 * t));
        double first = 18 * t * t - 32 * t * t * t + 15 * t * t * t * t;
        double second = 36 * t - 96 * t * t + 60 * t * t * t;
        return unary(a, value, first, second);
    }

    private static Applied hardContact(Jet y, Jet bound, boolean lower) {
        Jet z = sub(y, bound);
        if (z.d1 != 0) {
            return new Applied(bound, true);
        }
        boolean inside = lower ? z.d2 > 0 : z.d2 < 0;
        return new Applied(inside ? y : bound, false);
    }

    private static Applied floorMap(Jet y, Jet lo, Jet e) {
        if (e.value == 0 && y.value == lo.value) {
            return hardContact(y, lo, true);
        }
        if (y.value <= lo.value) {
            return new Applied(lo, false);
        }
        if (e.value == 0 || y.value >= lo.value + e.value) {
            return new Applied(y, false);
        }
        return new Applied(add(lo, mul(e, boundaryRamp(div(sub(y, lo), e)))), false);
    }

    private static Applied ceilMap(Jet y, Jet hi, Jet e) {
        if (e.value == 0 && y.value == hi.value) {
            return hardContact(y, hi, false);
        }
        if (y.value >= hi.value) {
            return new Applied(hi, false);
        }
        if (e.value == 0 || y.value <= hi.value - e.value) {
            return new Applied(y, false);
        }
        return new Applied(sub(hi, mul(e, boundaryRamp(div(sub(hi, y), e)))), false);
    }

    private static Applied applyBoundary(Jet y, Jet lo, Jet hi, Jet e) {
        if (lo != null && hi != null && hi.value <= lo.value) {
            return new Applied(lo, false);
        }
        Applied z = lo == null ? new Applied(y, false) : floorMap(y, lo, e);
        if (hi != null) {
            Applied next = ceilMap(z.value(), hi, e);
            z = new Applied(next.value(), z.hard() || next.hard());
        }
        return z;
    }

    private static boolean finite(Jet a) {
        return Double.isFinite(a.value) && Double.isFinite(a.d1) && Double.isFinite(a.d2);
    }

    private record Dyadic(BigInteger numerator, int exponent) {
    }

    private static Dyadic dyadic(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Finite binary64 operand required");
        }
        long bits = Double.doubleToRawLongBits(value);
        int encoded = (int) ((bits >>> 52) & 2047);
        long mantissa = (bits & 0xfffffffffffffL) | (encoded == 0 ? 0L : 1L << 52);
        BigInteger numerator = BigInteger.valueOf(mantissa);
        if (bits < 0) {
            numerator = numerator.negate();
        }
        return new Dyadic(numerator, encoded == 0 ? -1074 : encoded - 1075);
    }

    /** Exact sum of binary64 products divided by an exact product of binary64 factors. */
    private static double exactSumProductsQuotient(double[][] products, double[] denominatorFactors) {
        Dyadic[] terms = new Dyadic[products.length];
        for (int i = 0; i < products.length; i++) {
            BigInteger n = BigInteger.ONE;
            int exponent = 0;
            for (double factor : products[i]) {
                Dyadic f = dyadic(factor);
                n = n.multiply(f.numerator());
                exponent += f.exponent();
            }
            terms[i] = new Dyadic(n, exponent);
        }
        Dyadic numerator = exactSum(terms);
        BigInteger denominatorNumerator = BigInteger.ONE;
        int denominatorExponent = 0;
        for (double factor : denominatorFactors) {
            Dyadic f = dyadic(factor);
            denominatorNumerator = denominatorNumerator.multiply(f.numerator());
            denominatorExponent += f.exponent();
        }
        if (denominatorNumerator.signum() == 0) {
            throw new ArithmeticException("Division by zero");
        }
        return roundedRatio(numerator, new Dyadic(denominatorNumerator, denominatorExponent));
    }

    private static Dyadic exactSum(Dyadic[] terms) {
        int exponent = Integer.MAX_VALUE;
        for (Dyadic term : terms) {
            if (term.numerator().signum() != 0) {
                exponent = StrictMath.min(exponent, term.exponent());
            }
        }
        if (exponent == Integer.MAX_VALUE) {
            return new Dyadic(BigInteger.ZERO, 0);
        }
        BigInteger numerator = BigInteger.ZERO;
        for (Dyadic term : terms) {
            if (term.numerator().signum() != 0) {
                numerator = numerator.add(term.numerator().shiftLeft(term.exponent() - exponent));
            }
        }
        return new Dyadic(numerator, exponent);
    }

    private static double roundedRatio(Dyadic numerator, Dyadic denominator) {
        if (numerator.numerator().signum() == 0) {
            return 0;
        }
        boolean negative = numerator.numerator().signum() != denominator.numerator().signum();
        BigInteger n = numerator.numerator().abs();
        BigInteger d = denominator.numerator().abs();
        int exponent = numerator.exponent() - denominator.exponent();
        int k = n.bitLength() - d.bitLength();
        int comparison = k >= 0 ? n.compareTo(d.shiftLeft(k)) : n.shiftLeft(-k).compareTo(d);
        if (comparison < 0) {
            k--;
        }
        int shift = StrictMath.max(k + exponent - 52, -1074);
        int scale = exponent - shift;
        if (scale >= 0) {
            n = n.shiftLeft(scale);
        } else {
            d = d.shiftLeft(-scale);
        }
        BigInteger[] quotient = n.divideAndRemainder(d);
        int halfway = quotient[1].shiftLeft(1).compareTo(d);
        if (halfway > 0 || (halfway == 0 && quotient[0].testBit(0))) {
            quotient[0] = quotient[0].add(BigInteger.ONE);
        }
        double value = StrictMath.scalb(quotient[0].doubleValue(), shift);
        return negative ? -value : value;
    }
}
