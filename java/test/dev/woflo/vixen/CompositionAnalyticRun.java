package dev.woflo.vixen;

import java.util.LinkedHashMap;
import java.util.Map;

/** Standalone checks for the analytic composition bridge. */
public final class CompositionAnalyticRun {
    private CompositionAnalyticRun() {
    }

    public static void main(String[] args) {
        testBlend();
        testPolynomialFullTarget();
        testZeroWindowIdentity();
        testAdditiveRelease();
        testImpulsePerturbation();
        testCoupling();
        System.out.println("CompositionAnalyticRun passed");
    }

    private static Analytic.Result parent(double x, double base, double deviation, double d1, double d2) {
        double base1 = .2;
        double base2 = .05;
        double value = base * (1 + deviation);
        double value1 = base1 * (1 + deviation) + base * d1;
        double value2 = base2 * (1 + deviation) + 2 * base1 * d1 + base * d2;
        double guideDeviation = deviation * .4;
        double guideValue = base * (1 + guideDeviation);
        double guide1 = base1 * (1 + guideDeviation) + base * (.4 * d1);
        double guide2 = base2 * (1 + guideDeviation) + 2 * base1 * (.4 * d1) + base * (.4 * d2);
        double positiveMultiplier = StrictMath.exp(.55 * StrictMath.tanh(deviation));
        double positiveValue = base * positiveMultiplier;
        Sample sample = new Sample(x, value, base, .12 + deviation, .03, deviation, guideDeviation, guideValue,
                .25, deviation, 1 + deviation, .5 + .5 * StrictMath.tanh(deviation), positiveMultiplier,
                positiveValue, .5, value, deviation, guideValue, guideDeviation, null, null, 0, false);

        Map<String, Analytic.FieldJet> fields = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : sample.asMap().entrySet()) {
            if (entry.getValue() instanceof Number number) {
                fields.put(entry.getKey(), new Analytic.FieldJet(number.doubleValue(), 0.0, 0.0));
            }
        }
        set(fields, "x", x, 1, 0);
        set(fields, "base", base, base1, base2);
        set(fields, "value", value, value1, value2);
        set(fields, "unconstrainedValue", value, value1, value2);
        set(fields, "deviation", deviation, d1, d2);
        set(fields, "unconstrainedDeviation", deviation, d1, d2);
        set(fields, "rawDeviation", deviation, d1, d2);
        set(fields, "guideValue", guideValue, guide1, guide2);
        set(fields, "unconstrainedGuideValue", guideValue, guide1, guide2);
        set(fields, "guideDeviation", guideDeviation, .4 * d1, .4 * d2);
        set(fields, "unconstrainedGuideDeviation", guideDeviation, .4 * d1, .4 * d2);
        set(fields, "positiveValue", positiveValue, 0, 0);
        return new Analytic.Result(sample, fields, value1, value2,
                new Analytic.Availability(true, true), new Analytic.Differentiability(true, true, null));
    }

    private static void set(Map<String, Analytic.FieldJet> fields, String name, double value, double d1, double d2) {
        fields.put(name, new Analytic.FieldJet(value, d1, d2));
    }

    private static Sample transformed(Sample p, double deviation) {
        double base = p.base();
        double value = base + base * deviation;
        double actual = deviation;
        double positive = StrictMath.exp(.55 * StrictMath.tanh(actual));
        return new Sample(p.x(), value, base, p.score(), p.guideScore(), p.rawDeviation(), p.guideDeviation(),
                p.guideValue(), p.stabilizationWeight(), actual, 1 + actual,
                .5 + .5 * StrictMath.tanh(actual), positive, base * positive, p.gate(), value, actual,
                p.unconstrainedGuideValue(), p.unconstrainedGuideDeviation(), p.floorValue(), p.ceilingValue(),
                p.boundaryWidth(), false);
    }

    private static void testBlend() {
        Analytic.Result a = parent(4, 10, .1, .01, .02);
        Analytic.Result b = parent(4, 12, .3, .03, .04);
        Operations.Jet weight = new Operations.Jet(.5, .25, .1);
        Sample exact = Sample.mix(a.sample(), b.sample(), weight.value());
        Analytic.Result result = CompositionAnalytic.blend(a, b, weight, exact, false);
        double expected = a.fields().get("value").d1()
                + (b.fields().get("value").value() - a.fields().get("value").value()) * weight.first()
                + (b.fields().get("value").d1() - a.fields().get("value").d1()) * weight.value();
        near(result.fields().get("value").d1(), expected, 1e-12, "blend slope");
        same(result.sample(), exact, "blend exact primal");
    }

    private static void testPolynomialFullTarget() {
        Analytic.Result p = parent(4, 10, .1, .01, .02);
        Guide guide = new Guide.Polynomial(new double[] {4, 2}, 4, 2, 1, 100);
        Influence recipe = Influence.of(2, 1, 2, guide);
        Sample exact = recipe.apply(p.sample(), 4, Config.defaults());
        Analytic.Result result = CompositionAnalytic.influence(p, recipe, 4, Config.defaults(), exact);
        near(result.sample().value(), 4, 0, "full polynomial target primal");
        near(result.fields().get("value").d1(), 1, 1e-12, "full polynomial target slope");
        near(result.fields().get("value").d2(), 0, 1e-12, "full polynomial target curvature");
    }

    private static void testZeroWindowIdentity() {
        Analytic.Result p = parent(9, 10, .1, .01, .02);
        Guide guide = new Guide.Polynomial(new double[] {3}, 0, 1, 2, 8);
        Influence recipe = Influence.windowed(2, 1, 2, 6, 2, guide, Influence.Reference.BASELINE);
        Sample exact = recipe.apply(p.sample(), 9, Config.defaults());
        Analytic.Result result = CompositionAnalytic.influence(p, recipe, 9, Config.defaults(), exact);
        same(result.sample(), exact, "zero-window exact primal");
        near(result.fields().get("value").d1(), p.fields().get("value").d1(), 0, "zero-window slope");
        near(result.fields().get("value").d2(), p.fields().get("value").d2(), 0, "zero-window curvature");
        near(result.fields().get("guideScore").d1(), p.fields().get("guideScore").d1(), 0, "seeded guide metadata");
    }

    private static void testAdditiveRelease() {
        Analytic.Result p = parent(7, 10, .1, .01, .02);
        Guide guide = new Guide.Polynomial(new double[] {3}, 0, 1, 1, 10);
        Influence recipe = Influence.additive(2, .8, 2, 6, 2, guide);
        Sample exact = recipe.apply(p.sample(), 7, Config.defaults());
        Analytic.Result result = CompositionAnalytic.influence(p, recipe, 7, Config.defaults(), exact);
        double weight = .4;
        double weight1 = -.75;
        double expected = p.fields().get("unconstrainedValue").d1()
                + p.fields().get("base").d1() * weight * 3
                + p.sample().base() * weight1 * 3;
        near(result.fields().get("unconstrainedValue").d1(), expected, 1e-12, "additive release slope");
        same(result.sample(), exact, "additive release exact primal");
    }

    private static void testImpulsePerturbation() {
        Analytic.Result p = parent(2.5, 10, .1, .01, .02);
        Operations.Jet delta = Operations.perturbationJet(Map.of("type", "impulse", "at", 2.0,
                "magnitude", .1, "relaxation", 4.0, "attack", 1.0), 2.5);
        double deviation = p.sample().unconstrainedDeviation() + delta.value();
        Sample exact = transformed(p.sample(), deviation);
        Analytic.Result result = CompositionAnalytic.perturb(p, delta, Config.defaults(), exact);
        double expected = p.fields().get("base").d1() * (1 + deviation)
                + p.sample().base() * (p.fields().get("unconstrainedDeviation").d1() + delta.first());
        near(result.fields().get("unconstrainedValue").d1(), expected, 1e-12, "impulse slope");
        near(result.fields().get("guideScore").value(), p.sample().guideScore(), 0, "impulse guide metadata");
        same(result.sample(), exact, "impulse exact primal");
    }

    private static void testCoupling() {
        Analytic.Result a = parent(5, 10, .1, .01, .02);
        Analytic.Result b = parent(5, 20, .3, .03, .04);
        Operations.Jet weight = new Operations.Jet(.25, .4, .2);
        double mean = .2;
        double aDeviation = (1 - weight.value()) * .1 + weight.value() * mean;
        double bDeviation = (1 - weight.value()) * .3 + weight.value() * mean;
        Sample exactA = transformed(a.sample(), aDeviation);
        Sample exactB = transformed(b.sample(), bDeviation);
        Analytic.Result[] results = CompositionAnalytic.couple(new Analytic.Result[] {a, b}, weight,
                new Sample[] {exactA, exactB});
        double mean1 = .02;
        double aDeviation1 = (1 - weight.value()) * .01 + weight.value() * mean1
                + weight.first() * (mean - .1);
        double expected = a.fields().get("base").d1() * (1 + aDeviation)
                + a.sample().base() * aDeviation1;
        near(results[0].fields().get("value").d1(), expected, 1e-12, "coupling slope");
        same(results[0].sample(), exactA, "coupling exact primal");
        near(results[0].fields().get("guideScore").value(), a.sample().guideScore(), 0, "coupling guide metadata");
    }

    private static void near(double actual, double expected, double tolerance, String name) {
        if (!(Math.abs(actual - expected) <= tolerance)) {
            throw new AssertionError(name + ": expected " + expected + ", got " + actual);
        }
    }

    private static void same(Object actual, Object expected, String name) {
        if (actual != expected) {
            throw new AssertionError(name + ": the supplied primal object was not retained");
        }
    }
}
