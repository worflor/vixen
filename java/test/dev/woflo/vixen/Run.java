package dev.woflo.vixen;

import dev.woflo.vixen.test.Check;
import dev.woflo.vixen.test.Suite;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * The suite of {@code dev.woflo.vixen} (docs/TDD.md section 3, 1h; docs/briefs/core-work-packages.md TH2): the port proves itself against
 * Vixen's own cross-port and influence-parity cases, frozen under {@code fixtures/} by {@code tools/vixen_fixtures.py} with the Vixen commit they
 * came from, and then against the laws of timelines (prefix, handoff, order, depth) and the three extensions the thread asks of Vixen (the tail,
 * the windowed influence and the guide in deviation units).
 */
public final class Run {
    private static final String FIXTURES = "tests/fixtures/v1.4.0/";
    private static final double TOLERANCE = 1e-10;

    private Run() {
    }

    public static void main(String[] args) {
        Suite suite = new Suite("Vixen Java conformance")
                .add("vixen:hash-and-seed-derivation-are-exact", Run::hashAndSeeds)
                .add("vixen:samples-agree-within-the-declared-tolerance", Run::crossPort)
                .add("vixen:guided-timelines-agree-within-the-declared-tolerance", Run::influenceParity)
                .add("vixen:the-prefix-is-the-parents-bitwise", Run::prefix)
                .add("vixen:the-handoff-is-c2", Run::handoff)
                .add("vixen:sampling-is-order-independent", Run::order)
                .add("vixen:a-fork-before-its-parent-is-refused", Run::refused)
                .add("vixen:ten-thousand-forks-sample-the-present-in-constant-work", Run::constantWork)
                .add("vixen:strict-math-is-bitwise-stable", Run::golden)
                .add("vixen:a-snapshot-round-trips-through-vixens-json", Run::roundTrip)
                .add("vixen:a-tail-samples-bitwise-as-the-full-lineage", Run::tail)
                .add("vixen:a-windowed-influence-releases-to-the-parent-exactly", Run::windowed)
                .add("vixen:a-deviation-guide-is-scale-free", Run::deviation)
                .add("vixen:the-opening-is-slack", Run::opening)
                .add("vixen:a-history-query-mixes-only-the-open-handoffs", Run::history);
        System.exit(suite.report());
    }

    // ----- fixtures ----------------------------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> cases(String file) {
        Path found = null;
        for (Path p = Path.of("").toAbsolutePath(); p != null && found == null; p = p.getParent()) {
            if (Files.isRegularFile(p.resolve(FIXTURES + file))) {
                found = p.resolve(FIXTURES + file);
            }
        }
        if (found == null) {
            throw new IllegalStateException("the frozen Vixen fixtures are missing from the tree: " + FIXTURES + file + " (tools/vixen_fixtures.py writes them)");
        }
        try (InputStream in = new GZIPInputStream(Files.newInputStream(found))) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> doc = (Map<String, Object>) VixenJson.parse(text);
            return (List<Map<String, Object>>) (List<?>) doc.get("cases");
        } catch (IOException e) {
            throw new IllegalStateException("the frozen Vixen fixtures are missing; run tools/vixen_fixtures.py", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static double[] vec(Object o) {
        if (o == null) {
            return null;
        }
        List<Object> l = (List<Object>) o;
        double[] v = new double[l.size()];
        for (int i = 0; i < v.length; i++) {
            v[i] = (Double) l.get(i);
        }
        return v;
    }

    private static double scaled(double a, double b) {
        return Math.abs(a - b) / Math.max(1, Math.max(Math.abs(a), Math.abs(b)));
    }

    /** Compares every field of a sample with Vixen's, within the scaled tolerance. Returns the worst error. */
    private static double compare(Check c, Sample s, Map<String, Object> expected, String label) {
        double worst = 0;
        Map<String, Object> got = s.asMap();
        c.equal(new java.util.TreeSet<>(Sample.FIELDS), new java.util.TreeSet<>(expected.keySet()), label + ": the same fields");
        for (String key : Sample.FIELDS) {
            Object e = expected.get(key);
            Object g = got.get(key);
            if (e == null || e instanceof Boolean) {
                c.equal(e, g, label + ": " + key);
            } else {
                double err = scaled((Double) e, (Double) g);
                worst = Math.max(worst, err);
                if (!(err < TOLERANCE)) {
                    c.fail(label + ": " + key + " " + e + " vs " + g);
                }
            }
        }
        return worst;
    }

    @SuppressWarnings("unchecked")
    private static Timeline replay(Map<String, Object> cfg, Object lineage) {
        List<Timeline.Snapshot.Fork> forks = new ArrayList<>();
        for (Object o : (List<Object>) lineage) {
            Map<String, Object> f = (Map<String, Object>) o;
            forks.add(new Timeline.Snapshot.Fork((Double) f.get("at"), (String) f.get("operation"), (Double) f.get("handoffWidth")));
        }
        return Timeline.restore(new Timeline.Snapshot(VixenJson.config(cfg), forks, List.of()));
    }

    // ----- Vixen's own cases -------------------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static void hashAndSeeds(Check c) {
        List<Map<String, Object>> cs = cases("cross-port.json.gz");
        c.equal(2400, cs.size(), "vixen: the frozen cross-port file holds Vixen's 2,400 cases");
        int i = 0;
        for (Map<String, Object> cse : cs) {
            Map<String, Object> cfg = (Map<String, Object>) cse.get("config");
            Map<String, Object> expected = (Map<String, Object>) cse.get("expected");
            Seed seed = cfg.get("seed") instanceof String s ? Seed.of(s) : Seed.of((long) (double) (Double) cfg.get("seed"));
            int h = Hash.hash32(seed.u32(), i, 7);
            c.equalLong(((Double) expected.get("hash")).longValue(), h & 0xffffffffL, "vixen: hash32(seed32(seed), i, 7) for case " + i);
            Timeline t = replay(cfg, cse.get("lineage"));
            Object want = expected.get("seed");
            Seed got = t.leafConfig().seed();
            if (want instanceof String s) {
                c.that(got.isText() && got.text().equals(s), "vixen: a text seed stays text, case " + i);
            } else {
                c.that(!got.isText() && got.unsigned() == ((Double) want).longValue(), "vixen: the fork's future seed is Vixen's, case " + i + " (" + want + " vs " + got + ")");
            }
            c.near((Double) expected.get("sensitivity"), t.rootConfig() == null ? 0 : leafBounds(t), 0, "vixen: the sensitivity bound is exact, case " + i);
            i++;
        }
        // the seed of a text is FNV-1a over its UTF-8 bytes: the documented anchors
        c.equalLong(0x811c9dc5L, Hash.seed32("") & 0xffffffffL, "vixen: the empty text is the FNV offset basis");
        c.equalLong(0xe40c292cL, Hash.seed32("a") & 0xffffffffL, "vixen: FNV-1a of 'a'");
        c.equal(Hash.seed32("\ud800"), Hash.seed32("�"), "vixen: an unpaired surrogate encodes as U+FFFD, as TextEncoder does");
        c.that(Hash.seed32("é") != Hash.seed32("é"), "vixen: seeds are code units, not normalized text");
        c.that(Seed.of("42").equals(Seed.of("42")) && !Seed.of("42").equals(Seed.of(42)), "vixen: a text seed and a number are different seeds");
    }

    private static double leafBounds(Timeline t) {
        return Leaf.create(t.rootConfig()).bounds().unconstrainedSpatialSlope();
    }

    @SuppressWarnings("unchecked")
    private static void crossPort(Check c) {
        double worst = 0;
        int i = 0;
        for (Map<String, Object> cse : cases("cross-port.json.gz")) {
            Map<String, Object> cfg = (Map<String, Object>) cse.get("config");
            Map<String, Object> expected = (Map<String, Object>) cse.get("expected");
            Timeline t = replay(cfg, cse.get("lineage"));
            double x = (Double) cse.get("x");
            double[] h = vec(cse.get("h"));
            Sample s = h == null ? t.sample(x) : t.at(x, h);
            worst = Math.max(worst, compare(c, s, (Map<String, Object>) expected.get("sample"), "vixen: cross-port case " + i));
            Leaf root = Leaf.create(VixenJson.config(cfg));
            {
                double[][] plane = Leaf.navigationBasis(i % 20);
                Map<String, Object> pe = (Map<String, Object>) expected.get("plane");
                double[] u = vec(pe.get("u"));
                double[] v = vec(pe.get("v"));
                for (int j = 0; j < 16; j++) {
                    if (Math.abs(plane[0][j] - u[j]) > 1e-12 || Math.abs(plane[1][j] - v[j]) > 1e-12) {
                        c.fail("vixen: navigation basis of plane " + (i % 20) + " differs at " + j);
                        break;
                    }
                }
            }
            i++;
        }
        c.that(worst < TOLERANCE, "vixen: the worst scaled error over 2,400 cases is " + worst);
    }

    @SuppressWarnings("unchecked")
    private static void influenceParity(Check c) {
        double worst = 0;
        int i = 0;
        List<Map<String, Object>> cs = cases("influence-parity.json.gz");
        c.equal(1760, cs.size(), "vixen: 160 guided timelines sampled at eleven coordinates");
        for (Map<String, Object> cse : cs) {
            Timeline t = VixenJson.restore(cse.get("snapshot"));
            double x = (Double) cse.get("x");
            double[] h = vec(cse.get("h"));
            Sample s = h == null ? t.sample(x) : t.at(x, h);
            worst = Math.max(worst, compare(c, s, (Map<String, Object>) cse.get("expected"), "vixen: influence case " + i));
            i++;
        }
        c.that(worst < TOLERANCE, "vixen: the worst scaled error over the guided cases is " + worst);
    }

    // ----- the laws of a timeline ---------------------------------------------------------------------------------------------------

    private static void prefix(Check c) {
        double[] ones = new double[16];
        java.util.Arrays.fill(ones, 2);
        Config cfg = Config.defaults().withVelocity(filled(.1)).withLatent(filled(.5));
        Timeline root = Timeline.create(new Config(Seed.of("vixen"), 100, 1, Config.Floor.NONE, null, .1, 0, 1, 16, .8, 10, filled(.5), filled(.1)));
        Timeline a = root.fork(150, "open-door", null);
        Timeline b = a.fork(250, "take-key", null);
        for (double x : new double[] {1, 42, 100, 149.5, 150}) {
            c.equal(root.sample(x), a.sample(x), "vixen: a fork is its parent up to its coordinate, x=" + x);
            c.equal(root.at(x, ones), a.at(x, ones), "vixen: and at any latent position, x=" + x);
        }
        for (double x : new double[] {1, 150, 200, 250}) {
            c.equal(a.sample(x), b.sample(x), "vixen: a nested fork inherits its actual parent, x=" + x);
        }
        c.that(a.sample(240).value() != root.sample(240).value(), "vixen: a fork's future is another course");
        c.that(cfg != null, "vixen: defaults");
    }

    private static double[] filled(double v) {
        double[] a = new double[16];
        java.util.Arrays.fill(a, v);
        return a;
    }

    private static void handoff(Check c) {
        for (Config.Floor floor : Config.Floor.values()) {
            Config cfg = new Config(Seed.of("vixen"), 100, 0, floor, .5, .3, 0, 1, 16, .8, 10, new double[16], new double[16]);
            Timeline p = Timeline.create(cfg);
            Timeline f = p.fork(200, "door", 20.0);
            Timeline target = Timeline.create(f.leafConfig());
            c.equal(p.sample(200), f.sample(200), "vixen: the handoff begins as the parent exactly, " + floor);
            c.equal(target.sample(220), f.sample(220), "vixen: and ends as the future exactly, " + floor);
            for (double x = 190; x <= 240; x += .25) {
                Sample r = f.sample(x);
                if (r.floorValue() != null) {
                    c.that(r.value() >= r.floorValue(), "vixen: the floor holds through the join, " + floor + " x=" + x);
                }
                if (r.ceilingValue() != null) {
                    c.that(r.value() <= r.ceilingValue(), "vixen: the ceiling holds through the join, " + floor + " x=" + x);
                }
                c.near(r.activity(), .5 + .5 * Math.tanh(r.deviation()), 1e-12, "vixen: the adapters stay coherent, " + floor);
            }
            double h = .001;
            for (Object[] pair : new Object[][] {{200.0, p}, {220.0, target}}) {
                double x = (Double) pair[0];
                Timeline ref = (Timeline) pair[1];
                double d1 = (f.sample(x + h).value() - f.sample(x - h).value()) / (2 * h);
                double d2 = (ref.sample(x + h).value() - ref.sample(x - h).value()) / (2 * h);
                c.that(scaled(d1, d2) < 1e-5, "vixen: the slope is the neighbour's at the join, " + floor + " x=" + x);
                double k1 = (f.sample(x + h).value() - 2 * f.sample(x).value() + f.sample(x - h).value()) / (h * h);
                double k2 = (ref.sample(x + h).value() - 2 * ref.sample(x).value() + ref.sample(x - h).value()) / (h * h);
                c.that(scaled(k1, k2) < 1e-3, "vixen: and so is the curvature, " + floor + " x=" + x);
            }
        }
    }

    private static void order(Check c) {
        Timeline t = Timeline.create(Config.defaults()).fork(120, "a", 10.0).fork(130, "b", 30.0).fork(131, "c", 5.0).fork(300, "d", 16.0);
        double[] xs = {50, 119, 120, 125, 130, 131, 133, 140, 161, 299, 300, 305, 400, 2000};
        Sample[] forward = new Sample[xs.length];
        for (int i = 0; i < xs.length; i++) {
            forward[i] = t.sample(xs[i]);
        }
        for (int i = xs.length - 1; i >= 0; i--) {
            c.equal(forward[i], t.sample(xs[i]), "vixen: a sample does not depend on what was sampled before, x=" + xs[i]);
        }
        Timeline fresh = Timeline.restore(t.snapshot());
        for (int i = 0; i < xs.length; i++) {
            c.equal(forward[i], fresh.sample(xs[i]), "vixen: a replay samples the same, x=" + xs[i]);
        }
    }

    private static void refused(Check c) {
        Timeline root = Timeline.create(Config.defaults());
        Timeline a = root.fork(200, "branch", null);
        c.throwsAny(() -> a.fork(199, "rewind", null), "vixen: a fork before its parent is refused");
        c.throwsAny(() -> root.fork(0, "x", null), "vixen: a coordinate below 1 is refused");
        c.throwsAny(() -> root.fork(50000, "x", null), "vixen: a handoff past the domain is refused");
        c.throwsAny(() -> root.fork(200, "", null), "vixen: an empty operation is refused");
        c.throwsAny(() -> root.fork(200, "x", 0.0), "vixen: a handoff of no width is refused");
        Timeline guided = root.influence(Influence.of(160, 1, 20, new Guide.Polynomial(new double[] {3, 2}, 0, 1, 1, 500)));
        c.throwsAny(() -> guided.fork(159, "back", null), "vixen: a fork before an influence is refused");
        c.throwsAny(() -> guided.influence(Influence.of(150, 1, 20, new Guide.Polynomial(new double[] {3}, 0, 1, 1, 500))),
                "vixen: an influence before an earlier event is refused");
        c.equal(a.sample(150), root.sample(150), "vixen: a refused fork changes nothing");
    }

    private static void constantWork(Check c) {
        // depth does not enter the present's cost: n forks, and one sample past the last handoff, always cost one field evaluation
        long[] cost = new long[3];
        int[] depths = {1, 100, 10_000};
        for (int k = 0; k < depths.length; k++) {
            Timeline t = Timeline.create(Config.defaults());
            for (int i = 0; i < depths[k]; i++) {
                t = t.fork(110 + i * 2.0, "knot:" + i, 1.0);
            }
            long before = t.work();
            double present = 110 + depths[k] * 2.0 + 50;
            for (int s = 0; s < 10; s++) {
                t.sample(Math.min(present + s, 40_000));
            }
            cost[k] = t.work() - before;
        }
        c.equalLong(10, cost[0], "vixen: ten samples at depth 1 cost ten field evaluations");
        c.equalLong(10, cost[1], "vixen: and at depth 100");
        c.equalLong(10, cost[2], "vixen: and at depth 10,000: no depth cap, and no cost that grows with depth");
        // a history query inside one handoff mixes two leaves, not ten thousand
        Timeline t = Timeline.create(Config.defaults());
        for (int i = 0; i < 10_000; i++) {
            t = t.fork(110 + i * 2.0, "knot:" + i, 1.0);
        }
        long before = t.work();
        t.sample(110 + 5_000 * 2.0 + .5);
        c.equalLong(2, t.work() - before, "vixen: a query inside one handoff at depth 10,000 evaluates two leaves");
    }

    private static void golden(Check c) {
        // pinned bit patterns of the port (StrictMath: exp, tanh, sqrt, floor), the same on every JVM and every era
        Timeline t = Timeline.create(Config.defaults()).fork(300.5, "golden", 12.0);
        double[] xs = {101, 140.25, 400, 1234.5};
        long[] want = GOLDEN;
        for (int i = 0; i < xs.length; i++) {
            Sample s = t.sample(xs[i]);
            c.equalLong(want[i * 2], Double.doubleToLongBits(s.value()), "vixen: golden value bits at x=" + xs[i]);
            c.equalLong(want[i * 2 + 1], Double.doubleToLongBits(s.activity()), "vixen: golden activity bits at x=" + xs[i]);
        }
    }

    private static final long[] GOLDEN = {4643599437537971817L, 4602691196839145833L, 4654395182929094410L, 4606870582460897514L, 4673588804314965561L,
            4604028788801420256L, 4727845774292264836L, 4597820070033828504L};

    private static void roundTrip(Check c) {
        Config cfg = new Config(Seed.of(7), 100, .5, Config.Floor.BASELINE, .5, .3, .4, 1.2, 12, 1.5, 9, filled(.25), filled(-.05));
        Timeline t = Timeline.create(cfg).fork(150.125, "door 🦊", 20.0);
        t = t.influence(Influence.of(200, .6, 24, new Guide.Polynomial(new double[] {3, 2}, 0, 1, 1, 500)));
        t = t.influence(Influence.windowed(240, .5, 8, 300, 8, new Guide.Polynomial(new double[] {.1, -.2}, 240, 10, 240, 320), Influence.Reference.BASELINE));
        t = t.fork(260, "after", 10.0);
        String text = VixenJson.snapshotText(t.snapshot());
        Timeline back = VixenJson.restore(text);
        for (double x = 1; x <= 500; x += 7.3) {
            c.equal(t.sample(x), back.sample(x), "vixen: a replay through Vixen's JSON samples the same, x=" + x);
        }
        c.equal(text, VixenJson.snapshotText(back.snapshot()), "vixen: the snapshot writes itself the same");
        Timeline plain = Timeline.create(cfg).fork(150.125, "door", 20.0);
        String plainText = VixenJson.snapshotText(plain.snapshot());
        c.that(plainText.contains("\"vixen-hyperfield-c2/4\"") && !plainText.contains("influence"), "vixen: an unguided snapshot is Vixen's own schema");
        c.throwsAny(() -> VixenJson.restore(plainText.replace("\"lineage\"", "\"surprise\":1,\"lineage\"")), "vixen: an unknown field is refused");
        c.throwsAny(() -> VixenJson.restore("{\"algorithm\":\"vixen-hyperfield-c2/9\",\"config\":{}}"), "vixen: an unknown algorithm is refused");
        c.throwsAny(() -> VixenJson.restore(text.replace("\"handoffWidth\":8", "\"handoffWidth\":null")), "vixen: a null control is never read as a default");
    }

    private static void tail(Check c) {
        Config cfg = new Config(Seed.of("tail"), 100, 0, Config.Floor.NONE, null, .1, 0, 1, 16, .8, 10, filled(.2), filled(.01));
        Timeline t = Timeline.create(cfg);
        // forks and windowed influences through a long life, some overlapping
        double at = 120;
        for (int i = 0; i < 60; i++) {
            t = t.fork(at, "knot:" + i, 8.0);
            if (i % 3 == 0) {
                t = t.influence(Influence.windowed(at + 1, .5, 4, at + 30, 8, new Guide.Polynomial(new double[] {.2 - .01 * i, .1}, at, 40, at, at + 45),
                        Influence.Reference.BASELINE));
            }
            at += 11 + (i % 4);
        }
        double from = 400;
        Tail tail = t.tail(from);
        c.that(tail.open().size() < t.forks(), "vixen: closed forks are folded away, " + tail.open().size() + " of " + t.forks() + " remain");
        c.that(tail.influences().size() < t.influences(), "vixen: released influences are folded away");
        Timeline back = VixenJson.tail(VixenJson.tailText(tail)).restore();
        for (double x = from; x < 900; x += 3.7) {
            c.equal(t.sample(x), back.sample(x), "vixen: a tail samples bitwise as the full lineage, x=" + x);
            c.equal(t.sample(x), tail.restore().sample(x), "vixen: and so does the tail itself, x=" + x);
        }
        c.throwsAny(() -> back.sample(from - 1), "vixen: the past before a checkpoint is the spun record's, not the timeline's");
        // the witness proves the folded history, and chains across tails
        int closed = 0;
        for (Timeline.Entry e : t.entries()) {
            if (e.end() <= from) {
                closed++;
            }
        }
        c.that(tail.folded() >= closed, "vixen: the tail counts what it folded");
        Tail second = back.tail(from + 200);
        Timeline back2 = second.restore();
        for (double x = from + 200; x < 900; x += 5.3) {
            c.equal(t.sample(x), back2.sample(x), "vixen: a tail of a tail still samples as the full lineage, x=" + x);
        }
        c.that(!second.witness().equals(tail.witness()), "vixen: a later fold has its own witness");
        c.equal(tail.witness(), t.tail(from).witness(), "vixen: the witness is a function of the folded history");
    }

    private static void windowed(Check c) {
        Config cfg = Config.defaults();
        Timeline root = Timeline.create(cfg);
        Guide g = new Guide.Polynomial(new double[] {.3}, 0, 1, 150, 400);
        Timeline w = root.influence(Influence.windowed(150, .8, 10, 200, 10, g, Influence.Reference.BASELINE));
        for (double x : new double[] {1, 100, 150}) {
            c.equal(root.sample(x), w.sample(x), "vixen: before its attachment an influence is the parent, x=" + x);
        }
        for (double x : new double[] {210, 211, 300, 399, 5000, 49999}) {
            c.equal(root.sample(x), w.sample(x), "vixen: released, an influence is transparent bitwise, x=" + x);
        }
        c.that(w.domainEnd() == root.domainEnd(), "vixen: a released guide's finite domain no longer shortens the timeline");
        Sample held = w.sample(190);
        Sample base = root.sample(190);
        c.near(base.deviation() * .2 + .8 * .3, held.deviation(), 1e-9, "vixen: held, the deviation is pulled by the amount toward the guide");
        Timeline unreleased = root.influence(Influence.of(150, .8, 10, new Guide.Polynomial(new double[] {200}, 0, 1, 1, 400)));
        c.that(unreleased.domainEnd() == 400, "vixen: an unreleased finite guide still bounds the domain");
        c.throwsAny(() -> w.influence(Influence.windowed(150, .8, 10, 205, 10, new Guide.Polynomial(new double[] {.3}, 0, 1, 150, 210), Influence.Reference.BASELINE)),
                "vixen: a window whose guide ends before its release is refused");
        c.throwsAny(() -> root.influence(Influence.windowed(150, .8, 10, 155, 10, g, Influence.Reference.BASELINE)), "vixen: a window that releases before its handoff completes is refused");
        // the pull is C1 across the edges of the window
        double h = .001;
        for (double edge : new double[] {150, 160, 200, 210}) {
            double left = (w.sample(edge).deviation() - w.sample(edge - h).deviation()) / h;
            double right = (w.sample(edge + h).deviation() - w.sample(edge).deviation()) / h;
            double ref = (root.sample(edge + h).deviation() - root.sample(edge).deviation()) / h;
            c.that(Math.abs(left - right) < .02 + Math.abs(ref), "vixen: the weight has no corner at " + edge);
        }
    }

    private static void deviation(Check c) {
        // a constant deviation guide works over the whole domain and is scale free: the same d pulls any course to the same relative height
        Timeline root = Timeline.create(Config.defaults());
        Guide g = new Guide.Polynomial(new double[] {.25}, 0, 1, 1, 50000);
        Timeline pulled = root.influence(Influence.windowed(300, 1, 10, 40000, 10, g, Influence.Reference.BASELINE));
        for (double x : new double[] {320, 1000, 20000, 39000}) {
            Sample s = pulled.sample(x);
            c.near(.25, s.deviation(), 1e-9, "vixen: at amount one the deviation is the guide's, at any scale of the baseline, x=" + x);
        }
        Timeline backbone = root.influence(Influence.windowed(300, 1, 10, 400, 10, new Guide.Polynomial(new double[] {0}, 0, 1, 1, 50000), Influence.Reference.BACKBONE));
        Sample b = backbone.sample(350);
        c.near(b.unconstrainedGuideDeviation(), b.deviation(), 1e-9, "vixen: a zero deviation from the backbone is the backbone");
    }

    private static void opening(Check c) {
        Config cfg = Config.defaults();
        Timeline t = Timeline.create(cfg);
        for (double x = 1; x <= 100; x += 3.1) {
            Sample s = t.sample(x);
            c.that(s.deviation() == 0 && s.activity() == .5, "vixen: before the entrance gate opens the deviation is exactly zero, x=" + x);
        }
        c.that(t.sample(130).deviation() != 0, "vixen: after the opening the tide comes in");
    }

    private static void history(Check c) {
        Timeline t = Timeline.create(Config.defaults());
        for (int i = 0; i < 40; i++) {
            t = t.fork(110 + i * 20.0, "k" + i, 8.0);
        }
        long before = t.work();
        t.sample(110 + 20 * 20.0 + 3);
        c.equalLong(2, t.work() - before, "vixen: a query in the middle of the lineage mixes the one open handoff and nothing else");
        before = t.work();
        t.sample(110 + 20 * 20.0 + 12);
        c.equalLong(1, t.work() - before, "vixen: and between handoffs reads one leaf");
    }
}
