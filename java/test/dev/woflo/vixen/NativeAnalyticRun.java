package dev.woflo.vixen;

import java.util.Map;

/** Executable acceptance checks for native leaf analytic jets. */
public final class NativeAnalyticRun {
    private NativeAnalyticRun() {
    }

    public static void main(String[] args) {
        checkZeroStrengthBaseline();
        checkPolynomialChain();
        checkConstrainedVariants();
        checkExplicitCoordinates();
        checkOpeningEndpointAvailability();
        System.out.println("Native analytic checks passed");
    }

    private static void checkZeroStrengthBaseline() {
        Config c = config("analytic-baseline", 120, 0, Config.Floor.NONE, null, .1, 0, 0, 16, .8, 10,
                new double[16], new double[16]);
        Leaf leaf = Leaf.create(c);
        for (double x : new double[] {1, 12, 42, 71, 89, 100, 120}) {
            Analytic.Result result = Analytic.leaf(leaf, x);
            Sample sample = result.sample();
            same(sample.value(), sample.base(), "zero-strength value must equal the authored baseline");
            same(result.fields().get("value").value(), sample.value(), "value jet must retain the sample primal");
            same(result.fields().get("base").value(), sample.base(), "base jet must retain the sample primal");
        }
    }

    private static void checkPolynomialChain() {
        Config c = config("analytic-polynomial", 120, 0, Config.Floor.NONE, null, .1, 0, 0, 16, .8, 10,
                new double[16], new double[16]);
        Leaf leaf = Leaf.create(c);
        double x = 82;
        Analytic.Result result = Analytic.leaf(leaf, x);
        double rate = 99 / (c.openingEndpoint() - 1);
        double t = 1 + (x - 1) * rate;
        double a = t - 42;
        double b = t - 71;
        double g1 = 25 * StrictMath.exp(-(a * a) / 32);
        double g2 = 38 * StrictMath.exp(-(b * b) / 28);
        double exp = StrictMath.exp(t / 100);
        double first = exp * (1 + t / 100) + g1 * (2 * a / 32) + g2 * (2 * b / 28);
        double second = exp * (.02 + t / 10000)
                + g1 * (2.0 / 32 - 4 * a * a / (32 * 32))
                + g2 * (2.0 / 28 - 4 * b * b / (28 * 28));
        Analytic.FieldJet base = result.fields().get("base");
        near(base.d1(), first * rate, 1e-11, "authored baseline first chain derivative");
        near(base.d2(), second * rate * rate, 1e-11, "authored baseline second chain derivative");
    }

    private static void checkConstrainedVariants() {
        double[] latent = new double[16];
        double[] velocity = new double[16];
        for (int i = 0; i < 16; i++) {
            latent[i] = (i - 7.5) * .3;
            velocity[i] = (i % 5 - 2) * .02;
        }
        Config[] variants = {
                config("analytic-zero-floor", 100, 0, Config.Floor.ZERO, .05, .25, .2, 1.2, 12, .8, 10, latent, velocity),
                config("analytic-base-floor", 100, 0, Config.Floor.BASELINE, .05, .25, .2, 1.2, 12, .8, 10, latent, velocity),
                config("analytic-backbone-floor", 100, 0, Config.Floor.BACKBONE, .05, .25, .2, 1.2, 12, .8, 10, latent, velocity),
                config("analytic-ceiling", 100, 0, Config.Floor.NONE, .05, .25, .2, 1.2, 12, .8, 10, latent, velocity)
        };
        for (Config variant : variants) {
            Leaf leaf = Leaf.create(variant);
            Analytic.Result result = Analytic.leaf(leaf, 150);
            check(result.sample().equals(leaf.sample(150)), "constrained result must retain the original sample");
            for (Map.Entry<String, Object> entry : result.sample().asMap().entrySet()) {
                if (entry.getValue() instanceof Number number) {
                    Analytic.FieldJet field = result.fields().get(entry.getKey());
                    check(field != null, "numeric sample field must have a jet: " + entry.getKey());
                    same(field.value(), number.doubleValue(), "field primal must equal sample value: " + entry.getKey());
                }
            }
            try {
                result.fields().put("extra", new Analytic.FieldJet(0, 0.0, 0.0));
                throw new AssertionError("field map must be immutable");
            } catch (UnsupportedOperationException expected) {
                // Expected: result maps are immutable.
            }
        }
    }

    private static void checkExplicitCoordinates() {
        double[] latent = new double[16];
        double[] velocity = new double[16];
        double[] coordinates = new double[16];
        for (int i = 0; i < 16; i++) {
            latent[i] = i * .05;
            velocity[i] = (i - 8) * .01;
            coordinates[i] = (8 - i) * 2.5;
        }
        Leaf leaf = Leaf.create(config("analytic-explicit", 100, 0, Config.Floor.NONE, null, .1, .1, 1, 16, .8, 10,
                latent, velocity));
        Analytic.Result result = Analytic.leaf(leaf, 137, coordinates);
        check(result.sample().equals(leaf.at(137, coordinates)), "explicit-coordinate result must retain leaf.at sample");
        same(result.fields().get("score").value(), result.sample().score(), "explicit 16D score primal");
        check(result.fields().get("score").d1() != null, "explicit 16D score slope should be available away from lattice boundaries");
        expectIllegal(() -> Analytic.leaf(leaf, 137, new double[15]), "explicit coordinates must contain sixteen values");
    }

    private static void checkOpeningEndpointAvailability() {
        Leaf leaf = Leaf.create(config("analytic-endpoint", 100, 0, Config.Floor.NONE, null, .1, 0, 1, 16, .8, 10,
                new double[16], new double[16]));
        Analytic.Result result = Analytic.leaf(leaf, 100);
        check(result.fields().get("score").d1() == null && result.fields().get("score").d2() == null,
                "score derivatives are unavailable at the opening endpoint");
        check(result.fields().get("guideScore").d1() == null && result.fields().get("guideScore").d2() == null,
                "guide-score derivatives are unavailable at the opening endpoint");
        check(result.availability().slope() && result.availability().curvature(),
                "value derivatives remain available at the opening endpoint");
        check(!result.differentiability().sample() && result.differentiability().value(),
                "endpoint score branch affects sample differentiability but not value differentiability");
        check("The score branch changes at the opening endpoint; score field derivatives are undefined there."
                        .equals(result.rationale()),
                "endpoint rationale");
    }

    private static Config config(String seed, double endpoint, double intro, Config.Floor floor, Double ceiling,
                                 double softness, double stabilizer, double strength, double ramp, double tempo,
                                 double contrast, double[] latent, double[] velocity) {
        return new Config(Seed.of(seed), endpoint, intro, floor, ceiling, softness, stabilizer, strength, ramp, tempo,
                contrast, latent, velocity);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void same(double actual, double expected, String message) {
        if (Double.doubleToLongBits(actual) != Double.doubleToLongBits(expected)) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void near(Double actual, double expected, double tolerance, String message) {
        check(actual != null, message + " must be available");
        double allowed = tolerance * StrictMath.max(1, StrictMath.abs(expected));
        if (!Double.isFinite(actual) || StrictMath.abs(actual - expected) > allowed) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void expectIllegal(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }
}
