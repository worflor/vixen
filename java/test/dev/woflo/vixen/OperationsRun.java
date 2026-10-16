package dev.woflo.vixen;

import java.util.List;
import java.util.Map;

/** Dependency-free executable assertions for the operation adapters. */
public final class OperationsRun {
    private OperationsRun() {}
    public static void main(String[] args) {
        impulseEndpointsAndReplay();
        redistributionAndConstraintPolicy();
        couplingAndViews();
    }
    private static void impulseEndpointsAndReplay() {
        Map<String, ?> recipe = Map.of("type", "impulse", "at", 100.0, "magnitude", 0.4, "relaxation", 16.0, "attack", 2.0);
        Operations.Jet start = Operations.perturbationJet(recipe, 100);
        Operations.Jet rise = Operations.perturbationJet(recipe, 102);
        Operations.Jet end = Operations.perturbationJet(recipe, 118);
        near(start.value(), 0, 0); near(start.first(), 0, 0); near(start.second(), 0, 0);
        near(rise.value(), .4, 1e-15); near(rise.first(), 0, 1e-15); near(rise.second(), 0, 1e-15);
        near(end.value(), 0, 0); near(end.first(), 0, 0); near(end.second(), 0, 0);
        Curve base = Vixen.create();
        Curve changed = base.impulse(120, .25, 16, 2);
        near(changed.sample(119).value(), base.sample(119).value(), 0);
        near(changed.sample(140).value(), base.sample(140).value(), 0);
        Curve replay = (Curve) Vixen.restore(changed.snapshotJson());
        same(changed.sample(127).value(), replay.sample(127).value());
        Curve tail = (Curve) Vixen.restore(changed.tailJson(150));
        same(changed.sample(180).value(), tail.sample(180).value());
    }
    private static void redistributionAndConstraintPolicy() {
        Curve base = Vixen.create();
        Curve moved = base.redistribute(120, 160, new double[] {1}, 2, "actualDeviation");
        Operations.Jet left = Operations.perturbationJet(Map.of("type", "redistribution", "from", 120.0, "until", 160.0,
                "coefficients", List.of(1.0), "magnitude", 2.0, "measure", "actualDeviation"), 120);
        Operations.Jet right = Operations.perturbationJet(Map.of("type", "redistribution", "from", 120.0, "until", 160.0,
                "coefficients", List.of(1.0), "magnitude", 2.0, "measure", "actualDeviation"), 160);
        near(left.value(), 0, 0); near(left.first(), 0, 0); near(left.second(), 0, 0);
        near(right.value(), 0, 0); near(right.first(), 0, 0); near(right.second(), 0, 0);
        if (moved.sample(135).value() == base.sample(135).value()) fail("redistribution did not affect its interior");
        Curve replay = (Curve) Vixen.restore(moved.snapshotJson());
        same(moved.sample(135).value(), replay.sample(135).value());
        Config d = Config.defaults();
        Config constrained = new Config(d.seed(), d.openingEndpoint(), d.introVariation(), Config.Floor.ZERO, null, d.boundarySoftness(),
                d.stabilizer(), d.strength(), d.ramp(), d.tempo(), d.contrast(), d.latent(), d.velocity());
        Curve floored = Vixen.create(constrained);
        rejects(() -> floored.redistribute(120, 160, new double[] {1}));
        floored.redistribute(120, 160, new double[] {1}, 1, "unconstrainedDeviation");
    }
    private static void couplingAndViews() {
        Config d = Config.defaults();
        Config second = new Config(d.seed(), 70, d.introVariation(), d.floor(), d.ceilingMargin(), d.boundarySoftness(), d.stabilizer(),
                d.strength(), d.ramp(), d.tempo(), d.contrast(), d.latent(), d.velocity());
        Curve a = Vixen.create(), b = Vixen.create(second);
        Operations.Group group = Vixen.couple(List.of(a, b), 1, 120, 200);
        double x = 160;
        double before = a.sample(x).deviation() + b.sample(x).deviation();
        Sample[] mixed = group.sample(x);
        double after = mixed[0].deviation() + mixed[1].deviation();
        near(after, before, 2e-14);
        if (group.domain()[1] != Math.min(a.domainEnd(), b.domainEnd())) fail("coupling did not use common domain intersection");
        Operations.Group restored = (Operations.Group) Vixen.restore(group.snapshotJson());
        same(mixed[0].value(), restored.sample(x)[0].value());
        Curve view = group.curves().get(1);
        Curve restoredView = (Curve) Vixen.restore(view.snapshotJson());
        same(view.sample(x).value(), restoredView.sample(x).value());
        Operations.Group checkpoint = (Operations.Group) Vixen.restore(group.tailJson(210));
        same(group.sample(250)[1].value(), checkpoint.sample(250)[1].value());
    }
    private static void same(double a, double b) {
        if (Double.doubleToRawLongBits(a) != Double.doubleToRawLongBits(b)) fail("expected bitwise equality: " + a + " != " + b);
    }
    private static void near(double actual, double expected, double tolerance) {
        if (!(Math.abs(actual - expected) <= tolerance)) fail("expected " + expected + " within " + tolerance + ", got " + actual);
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        fail("expected invalid operation to be rejected");
    }
    private static void fail(String message) { throw new AssertionError(message); }
}