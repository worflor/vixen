package dev.woflo.vixen;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Analytic derivative composition for blends, influences, perturbations, and coupling. */
public final class CompositionAnalytic {
    private CompositionAnalytic() {
    }

    private record J(double value, double d1, double d2) {
    }

    private record Applied(J value, boolean hard) {
    }

    private static final double UNKNOWN = Double.NaN;

    private static J c(double value) {
        return new J(value, 0, 0);
    }

    private static double derivative(Double value) {
        return value != null && Double.isFinite(value) ? value : UNKNOWN;
    }

    private static double derivative(double value) {
        return Double.isFinite(value) ? value : UNKNOWN;
    }

    private static J operationJet(Operations.Jet value) {
        Objects.requireNonNull(value, "weight or perturbation jet");
        return new J(value.value(), derivative(value.first()), derivative(value.second()));
    }

    private static J field(Analytic.Result result, String name) {
        Analytic.FieldJet field = result.fields().get(name);
        if (field != null) {
            return new J(field.value(), derivative(field.d1()), derivative(field.d2()));
        }
        Object value = result.sample().asMap().get(name);
        if (value instanceof Number number) {
            return new J(number.doubleValue(), UNKNOWN, UNKNOWN);
        }
        return new J(0, UNKNOWN, UNKNOWN);
    }

    private static double sumProducts(double[][] terms) {
        double sum = 0;
        for (double[] term : terms) {
            boolean zero = false;
            boolean unknown = false;
            for (double factor : term) {
                if (factor == 0) {
                    zero = true;
                } else if (!Double.isFinite(factor)) {
                    unknown = true;
                }
            }
            if (zero) {
                continue;
            }
            if (unknown) {
                return UNKNOWN;
            }
            double product = 1;
            for (double factor : term) {
                product *= factor;
            }
            sum += product;
        }
        if (Double.isFinite(sum)) {
            return sum;
        }

        double[][] finiteTerms = new double[terms.length][];
        int count = 0;
        for (double[] term : terms) {
            boolean zero = false;
            boolean unknown = false;
            for (double factor : term) {
                if (factor == 0) {
                    zero = true;
                } else if (!Double.isFinite(factor)) {
                    unknown = true;
                }
            }
            if (zero) {
                finiteTerms[count++] = new double[] {0};
            } else if (unknown) {
                return UNKNOWN;
            } else {
                finiteTerms[count++] = term;
            }
        }
        double[][] exactTerms = new double[count][];
        System.arraycopy(finiteTerms, 0, exactTerms, 0, count);
        return Floating.sumProducts(exactTerms);
    }

    private static double divideProducts(double[][] terms, double denominator) {
        if (denominator == 0 || !Double.isFinite(denominator)) {
            return UNKNOWN;
        }
        double numerator = sumProducts(terms);
        if (Double.isNaN(numerator)) {
            return UNKNOWN;
        }
        double quotient = numerator / denominator;
        if (Double.isFinite(quotient)) {
            return quotient;
        }

        double inverse = 1 / denominator;
        double[][] scaled = new double[terms.length][];
        for (int i = 0; i < terms.length; i++) {
            double[] term = terms[i];
            boolean zero = false;
            for (double factor : term) {
                if (factor == 0) {
                    zero = true;
                    break;
                }
            }
            if (zero) {
                scaled[i] = new double[] {0};
            } else {
                scaled[i] = new double[term.length + 1];
                System.arraycopy(term, 0, scaled[i], 0, term.length);
                scaled[i][term.length] = inverse;
            }
        }
        return sumProducts(scaled);
    }

    private static J add(J a, J b) {
        return new J(a.value() + b.value(),
                sumProducts(new double[][] {{a.d1()}, {b.d1()}}),
                sumProducts(new double[][] {{a.d2()}, {b.d2()}}));
    }

    private static J add(J a, double b) {
        return add(a, c(b));
    }

    private static J sub(J a, J b) {
        return new J(a.value() - b.value(),
                sumProducts(new double[][] {{a.d1()}, {-1, b.d1()}}),
                sumProducts(new double[][] {{a.d2()}, {-1, b.d2()}}));
    }

    private static J sub(J a, double b) {
        return sub(a, c(b));
    }

    private static J mul(J a, J b) {
        double d1 = sumProducts(new double[][] {{a.d1(), b.value()}, {a.value(), b.d1()}});
        double d2 = sumProducts(new double[][] {{a.d2(), b.value()}, {2, a.d1(), b.d1()}, {a.value(), b.d2()}});
        return new J(a.value() * b.value(), d1, d2);
    }

    private static J mul(J a, double b) {
        return mul(a, c(b));
    }

    private static J div(J a, J b) {
        double value = a.value() / b.value();
        if (b.value() == 0 || !Double.isFinite(b.value())) {
            return new J(value, UNKNOWN, UNKNOWN);
        }
        double d1 = divideProducts(new double[][] {{a.d1()}, {-1, value, b.d1()}}, b.value());
        double d2 = divideProducts(new double[][] {{a.d2()}, {-1, value, b.d2()}, {-2, d1, b.d1()}}, b.value());
        return new J(value, d1, d2);
    }

    private static J div(J a, double b) {
        return div(a, c(b));
    }

    private static J unary(J a, double value, double first, double second) {
        return new J(value,
                sumProducts(new double[][] {{first, a.d1()}}),
                sumProducts(new double[][] {{first, a.d2()}, {second, a.d1(), a.d1()}}));
    }

    private static J exp(J a) {
        double value = StrictMath.exp(a.value());
        return unary(a, value, value, value);
    }

    private static J tanh(J a) {
        double value = StrictMath.tanh(a.value());
        double z = StrictMath.exp(-2 * StrictMath.abs(a.value()));
        double first = 4 * z / ((1 + z) * (1 + z));
        return unary(a, value, first, -2 * value * first);
    }

    private static J smooth5(J a) {
        if (a.value() <= 0) {
            return c(0);
        }
        if (a.value() >= 1) {
            return c(1);
        }
        double t = a.value();
        double value = t * t * t * (10 + t * (-15 + 6 * t));
        double first = 30 * t * t * (t - 1) * (t - 1);
        double second = 60 * t * (t - 1) * (2 * t - 1);
        return unary(a, value, first, second);
    }

    private static J boundaryRamp(J a) {
        if (a.value() <= 0) {
            return c(0);
        }
        if (a.value() >= 1) {
            return a;
        }
        double t = a.value();
        double value = t * t * t * (6 + t * (-8 + 3 * t));
        double first = 18 * t * t - 32 * t * t * t + 15 * t * t * t * t;
        double second = 36 * t - 96 * t * t + 60 * t * t * t;
        return unary(a, value, first, second);
    }

    private static Applied hardContact(J y, J bound, boolean lower) {
        J z = sub(y, bound);
        if (z.d1() != 0) {
            return new Applied(bound, true);
        }
        boolean inside = lower ? z.d2() > 0 : z.d2() < 0;
        return new Applied(inside ? y : bound, false);
    }

    private static Applied floorMap(J y, J lo, J width) {
        if (width.value() == 0 && y.value() == lo.value()) {
            return hardContact(y, lo, true);
        }
        if (y.value() <= lo.value()) {
            return new Applied(lo, false);
        }
        if (width.value() == 0 || y.value() >= lo.value() + width.value()) {
            return new Applied(y, false);
        }
        return new Applied(add(lo, mul(width, boundaryRamp(div(sub(y, lo), width)))), false);
    }

    private static Applied ceilMap(J y, J hi, J width) {
        if (width.value() == 0 && y.value() == hi.value()) {
            return hardContact(y, hi, false);
        }
        if (y.value() >= hi.value()) {
            return new Applied(hi, false);
        }
        if (width.value() == 0 || y.value() <= hi.value() - width.value()) {
            return new Applied(y, false);
        }
        return new Applied(sub(hi, mul(width, boundaryRamp(div(sub(hi, y), width)))), false);
    }

    private static Applied applyBoundary(J y, J lo, J hi, J width) {
        if (lo != null && hi != null && hi.value() <= lo.value()) {
            return new Applied(lo, false);
        }
        Applied result = lo == null ? new Applied(y, false) : floorMap(y, lo, width);
        if (hi != null) {
            Applied upper = ceilMap(result.value(), hi, width);
            result = new Applied(upper.value(), result.hard() || upper.hard());
        }
        return result;
    }

    private static J bound(Analytic.Result parent, String name) {
        return parent.sample().asMap().get(name) == null ? null : field(parent, name);
    }

    private static J exactBound(Sample sample, Map<String, J> fields, String name) {
        return sample.asMap().get(name) == null ? null : fields.get(name);
    }

    private static void deriveAdapters(Map<String, J> fields) {
        J value = fields.get("value");
        J base = fields.get("base");
        J deviation = div(sub(value, base), base);
        fields.put("deviation", deviation);
        fields.put("multiplier", add(c(1), deviation));
        fields.put("activity", add(c(.5), mul(c(.5), tanh(deviation))));
        fields.put("positiveMultiplier", exp(mul(c(.55), tanh(deviation))));
        fields.put("positiveValue", mul(base, fields.get("positiveMultiplier")));
        fields.put("guideDeviation", div(sub(fields.get("guideValue"), base), base));
        fields.put("unconstrainedDeviation", div(sub(fields.get("unconstrainedValue"), base), base));
        fields.put("unconstrainedGuideDeviation", div(sub(fields.get("unconstrainedGuideValue"), base), base));
    }

    private static Map<String, J> copyNumericFields(Analytic.Result source) {
        Map<String, J> fields = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.sample().asMap().entrySet()) {
            if (entry.getValue() instanceof Number) {
                fields.put(entry.getKey(), field(source, entry.getKey()));
            }
        }
        return fields;
    }

    private static Analytic.Result finish(Sample sample, Map<String, J> jets, String reason) {
        Map<String, Analytic.FieldJet> fields = new LinkedHashMap<>();
        boolean allSampleDerivatives = true;
        for (Map.Entry<String, Object> entry : sample.asMap().entrySet()) {
            if (!(entry.getValue() instanceof Number number)) {
                continue;
            }
            J jet = jets.get(entry.getKey());
            Double d1 = jet != null && Double.isFinite(jet.d1()) ? jet.d1() : null;
            Double d2 = jet != null && Double.isFinite(jet.d2()) ? jet.d2() : null;
            fields.put(entry.getKey(), new Analytic.FieldJet(number.doubleValue(), d1, d2));
            allSampleDerivatives &= d1 != null && d2 != null;
        }
        Analytic.FieldJet value = fields.get("value");
        Double slope = value == null ? null : value.d1();
        Double curvature = value == null ? null : value.d2();
        boolean valueAvailable = slope != null && curvature != null;
        Analytic.Availability availability = new Analytic.Availability(slope != null, curvature != null);
        String rationale = valueAvailable ? null
                : reason != null ? reason : "A contributing value derivative is unavailable.";
        Analytic.Differentiability differentiability = new Analytic.Differentiability(
                valueAvailable, allSampleDerivatives, rationale);
        return new Analytic.Result(sample, fields, slope, curvature, availability, differentiability);
    }

    /** Blend two analytic results using a coordinate-dependent weight. */
    public static Analytic.Result blend(Analytic.Result a, Analytic.Result b, Operations.Jet weight,
                                       Sample exactPrimal, boolean coherent) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        Objects.requireNonNull(exactPrimal, "exactPrimal");
        J w = operationJet(weight);
        Map<String, J> fields = new LinkedHashMap<>();
        String[] mixed = {"x", "value", "base", "score", "guideScore", "rawDeviation", "guideValue",
                "stabilizationWeight", "gate", "unconstrainedValue", "unconstrainedGuideValue", "boundaryWidth"};
        for (String name : mixed) {
            J left = field(a, name);
            fields.put(name, add(left, mul(sub(field(b, name), left), w)));
        }
        for (String name : new String[] {"floorValue", "ceilingValue"}) {
            if (exactPrimal.asMap().get(name) != null) {
                J left = field(a, name);
                fields.put(name, add(left, mul(sub(field(b, name), left), w)));
            }
        }

        J lo = exactBound(exactPrimal, fields, "floorValue");
        J hi = exactBound(exactPrimal, fields, "ceilingValue");
        if (coherent) {
            Applied mapped = applyBoundary(fields.get("unconstrainedValue"), lo, hi, fields.get("boundaryWidth"));
            Applied guided = applyBoundary(fields.get("unconstrainedGuideValue"), lo, hi, fields.get("boundaryWidth"));
            fields.put("value", mapped.hard() ? new J(exactPrimal.value(), UNKNOWN, UNKNOWN) : mapped.value());
            fields.put("guideValue", guided.hard() ? new J(exactPrimal.guideValue(), UNKNOWN, UNKNOWN) : guided.value());
        } else {
            J value = fields.get("value");
            if (lo != null && value.value() < lo.value()) {
                value = lo;
            }
            if (hi != null && value.value() > hi.value()) {
                value = hi;
            }
            fields.put("value", value);
        }
        deriveAdapters(fields);
        return finish(exactPrimal, fields, null);
    }

    private static J influenceWeight(Influence recipe, double x) {
        boolean windowed = recipe.isWindowed();
        double end = recipe.end();
        if (x <= recipe.at() || windowed && x >= end) {
            return c(0);
        }
        if (x < recipe.at() + recipe.handoffWidth()) {
            J t = new J((x - recipe.at()) / recipe.handoffWidth(), 1 / recipe.handoffWidth(), 0);
            return mul(c(recipe.amount()), smooth5(t));
        }
        if (!windowed || x <= recipe.until()) {
            return c(recipe.amount());
        }
        J t = new J((x - recipe.until()) / recipe.releaseWidth(), 1 / recipe.releaseWidth(), 0);
        return mul(c(recipe.amount()), sub(c(1), smooth5(t)));
    }

    private static J scalarPolicy(J value, J lo, J hi, J width) {
        if (lo != null && hi != null && hi.value() <= lo.value()) {
            return lo;
        }
        J mapped = value;
        if (lo != null) {
            if (mapped.value() <= lo.value()) {
                mapped = lo;
            } else if (width.value() > 0 && mapped.value() < lo.value() + width.value()) {
                mapped = add(lo, mul(width, boundaryRamp(div(sub(mapped, lo), width))));
            }
        }
        if (hi != null) {
            if (mapped.value() >= hi.value()) {
                mapped = hi;
            } else if (width.value() > 0 && mapped.value() > hi.value() - width.value()) {
                mapped = sub(hi, mul(width, boundaryRamp(div(sub(hi, mapped), width))));
            }
        }
        return mapped;
    }

    /** Apply an influence recipe's analytic weight, guide, target, and boundary policy. */
    public static Analytic.Result influence(Analytic.Result parent, Influence recipe, double x,
                                            Config config, Sample exactPrimal) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(recipe, "recipe");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(exactPrimal, "exactPrimal");
        if (!Double.isFinite(x)) {
            throw new IllegalArgumentException("x must be finite");
        }

        Map<String, J> fields = copyNumericFields(parent);
        J w = influenceWeight(recipe, x);
        J guide;
        if (w.value() == 0 && w.d1() == 0 && w.d2() == 0) {
            guide = new J(0, UNKNOWN, UNKNOWN);
        } else {
            Guide.Jet guideJet = recipe.guide().jet(x);
            guide = new J(guideJet.value(), derivative(guideJet.first()), derivative(guideJet.second()));
        }

        Sample p = parent.sample();
        J base = fields.get("base");
        J lo = bound(parent, "floorValue");
        J hi = bound(parent, "ceilingValue");
        J width = fields.get("boundaryWidth");
        J extra = mul(mul(base, w), c(config.boundarySoftness()));
        J nextWidth = width;
        if (lo != null && hi != null) {
            J room = sub(div(sub(hi, lo), c(2)), width);
            if (room.value() > 0 && extra.value() > 0) {
                J increment = extra.value() <= room.value()
                        ? div(extra, add(c(1), div(extra, room)))
                        : div(room, add(c(1), div(room, extra)));
                nextWidth = add(width, increment);
            } else if (room.value() == 0 && extra.value() > 0 && (room.d1() != 0 || room.d2() != 0)) {
                nextWidth = new J(width.value(), UNKNOWN, UNKNOWN);
            }
        } else if (lo != null || hi != null) {
            nextWidth = add(width, extra);
        }

        J unconstrained;
        if (recipe.mode() == Influence.Mode.ADD) {
            unconstrained = recipe.reference() == Influence.Reference.VALUE
                    ? add(fields.get("unconstrainedValue"), mul(w, guide))
                    : add(fields.get("unconstrainedValue"), mul(base, mul(w, guide)));
        } else if (w.value() == 1 && w.d1() == 0 && w.d2() == 0) {
            unconstrained = switch (recipe.reference()) {
                case VALUE -> guide;
                case BASELINE -> mul(base, add(c(1), guide));
                case BACKBONE -> add(fields.get("unconstrainedGuideValue"), mul(base, guide));
            };
        } else {
            unconstrained = mul(sub(c(1), w), fields.get("unconstrainedValue"));
            switch (recipe.reference()) {
                case VALUE -> unconstrained = add(unconstrained, mul(w, guide));
                case BASELINE -> unconstrained = add(unconstrained, mul(base, mul(w, add(c(1), guide))));
                case BACKBONE -> unconstrained = add(add(unconstrained,
                        mul(w, fields.get("unconstrainedGuideValue"))), mul(base, mul(w, guide)));
            }
        }

        Applied mapped = applyBoundary(unconstrained, lo, hi, nextWidth);
        Applied guided = applyBoundary(fields.get("unconstrainedGuideValue"), lo, hi, nextWidth);
        fields.put("boundaryWidth", nextWidth);
        fields.put("unconstrainedValue", unconstrained);
        fields.put("value", mapped.hard() ? new J(exactPrimal.value(), UNKNOWN, UNKNOWN) : mapped.value());
        fields.put("guideValue", guided.hard() ? new J(exactPrimal.guideValue(), UNKNOWN, UNKNOWN) : guided.value());

        String reason = mapped.hard()
                ? "A contributing hard constraint is not twice differentiable at this contact." : null;
        if (x == recipe.at() || recipe.isWindowed() && x == recipe.end()) {
            J scalar = scalarPolicy(field(parent, "unconstrainedValue"),
                    lo == null ? null : c(lo.value()), hi == null ? null : c(hi.value()), c(p.boundaryWidth()));
            if (scalar.value() != p.value()) {
                fields.put("value", new J(exactPrimal.value(), UNKNOWN, UNKNOWN));
                reason = "A legacy constrained-parent composition is discontinuous at this influence boundary.";
            }
            J guideScalar = scalarPolicy(field(parent, "unconstrainedGuideValue"),
                    lo == null ? null : c(lo.value()), hi == null ? null : c(hi.value()), c(p.boundaryWidth()));
            if (guideScalar.value() != p.guideValue()) {
                fields.put("guideValue", new J(exactPrimal.guideValue(), UNKNOWN, UNKNOWN));
            }
        }
        deriveAdapters(fields);
        return finish(exactPrimal, fields, reason);
    }

    /** Add a perturbation jet to the parent's unconstrained deviation and reapply its policy. */
    public static Analytic.Result perturb(Analytic.Result parent, Operations.Jet delta,
                                          Config config, Sample exactPrimal) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(exactPrimal, "exactPrimal");
        J d = operationJet(delta);
        Map<String, J> fields = copyNumericFields(parent);
        J base = fields.get("base");
        J unconstrained = mul(base, add(c(1), add(fields.get("unconstrainedDeviation"), d)));
        J lo = bound(parent, "floorValue");
        J hi = bound(parent, "ceilingValue");
        Applied mapped = applyBoundary(unconstrained, lo, hi, fields.get("boundaryWidth"));
        fields.put("unconstrainedValue", unconstrained);
        fields.put("value", mapped.hard() ? new J(exactPrimal.value(), UNKNOWN, UNKNOWN) : mapped.value());
        String reason = mapped.hard()
                ? "A contributing hard constraint is not twice differentiable at this contact." : null;
        if (d.value() == 0 && (lo != null || hi != null)) {
            J limit = applyBoundary(c(parent.sample().unconstrainedValue()),
                    lo == null ? null : c(lo.value()), hi == null ? null : c(hi.value()),
                    c(parent.sample().boundaryWidth())).value();
            if (limit.value() != parent.sample().value()) {
                fields.put("value", new J(exactPrimal.value(), UNKNOWN, UNKNOWN));
                reason = "A legacy constrained-parent composition is discontinuous at this operator zero.";
            }
        }
        deriveAdapters(fields);
        return finish(exactPrimal, fields, reason);
    }

    /** Couple source deviations through an analytic per-coordinate mean and weight. */
    public static Analytic.Result[] couple(Analytic.Result[] sources, Operations.Jet weight,
                                          Sample[] exactPrimals) {
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(exactPrimals, "exactPrimals");
        if (sources.length < 2 || sources.length > 32 || exactPrimals.length != sources.length) {
            throw new IllegalArgumentException("Coupling requires matching arrays of 2 to 32 sources");
        }
        J w = operationJet(weight);
        double[] deviations = new double[sources.length];
        double[] first = new double[sources.length];
        double[] second = new double[sources.length];
        boolean allFirst = true;
        boolean allSecond = true;
        for (int i = 0; i < sources.length; i++) {
            Objects.requireNonNull(sources[i], "source");
            Objects.requireNonNull(exactPrimals[i], "exact primal");
            deviations[i] = sources[i].sample().deviation();
            J deviation = field(sources[i], "deviation");
            first[i] = deviation.d1();
            second[i] = deviation.d2();
            allFirst &= Double.isFinite(first[i]);
            allSecond &= Double.isFinite(second[i]);
        }
        J mean = new J(Floating.mean(deviations),
                allFirst ? Floating.mean(first) : UNKNOWN,
                allSecond ? Floating.mean(second) : UNKNOWN);

        Analytic.Result[] output = new Analytic.Result[sources.length];
        for (int i = 0; i < sources.length; i++) {
            Map<String, J> fields = copyNumericFields(sources[i]);
            J deviation = fields.get("deviation");
            J mixed = add(mul(sub(c(1), w), deviation), mul(w, mean));
            J value = mul(fields.get("base"), add(c(1), mixed));
            fields.put("value", value);
            fields.put("unconstrainedValue", value);
            deriveAdapters(fields);
            output[i] = finish(exactPrimals[i], fields, null);
        }
        return output;
    }
}
