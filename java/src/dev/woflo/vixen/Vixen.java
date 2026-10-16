package dev.woflo.vixen;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Root factory and replay dispatcher for native timelines and operation adapters. */
public final class Vixen {
    public static Occupancy.Calibration calibrateOccupancy(OccupancyPopulation population,double targetFraction,double outputThreshold){return Occupancy.calibrate(population,targetFraction,outputThreshold);}
    public static Correlation.Factorization validateCorrelationMatrix(double[][] matrix){return Correlation.validateCorrelationMatrix(matrix);}
    public static Channels.CorrelatedChannels createCorrelatedChannels(double[][] matrix,Config config,Seed rootSeed){return Channels.createVixenChannels(matrix,config,rootSeed,Channels.Options.defaults());}
    private Vixen() {}
    public static Extended.Signal createExtended(Config config) { return Extended.create(config); }
    public static Extended.Signal createExtended(Config config,double domainEnd) { return Extended.create(config,domainEnd); }
    public static Extended.Signal createExtended() { return Extended.create(); }

    /** A value restored from JSON. A coupling group is replayable but is not itself a single curve. */
    public interface Restored {
        String snapshotJson();
        String tailJson(double from);
        default String tailJson() { return tailJson(1); }
    }

    public static Curve create() { return create(Config.defaults()); }
    public static Curve create(Config config) {
        if (config == null) throw new IllegalArgumentException("Config is required");
        return new TimelineCurve(Timeline.create(config));
    }
    public static Curve createCoherent(){return createCoherent(Config.defaults());}
    public static Curve createCoherent(Config config){if(config==null)throw new IllegalArgumentException("Config is required");return new TimelineCurve(Timeline.createCoherent(config));}

    public static Operations.Group couple(List<Curve> curves, double strength, double from, double until) {
        return Operations.couple(curves, strength, from, until);
    }

    /** Restores a native snapshot, tail, influence frame, operation, coupling group, or coupling view. */
    public static Restored restore(String json) {
        if (json == null) throw new IllegalArgumentException("JSON is required");
        return restore(VixenJson.parse(json));
    }

    /** Also accepts parsed trees; cycles, nonfinite values, and unsupported objects are rejected. */
    public static Restored restore(Object tree) {
        validateTree(tree);
        return restoreTree(tree);
    }

    public static Curve restoreCurve(String json) {
        Restored value = restore(json);
        if (!(value instanceof Curve curve)) throw new IllegalArgumentException("Replay value is not a single curve");
        return curve;
    }

    static Restored restoreTree(Object tree) {
        Map<String, Object> m = object(tree, "replay value");
        Object algorithm = m.get("algorithm");
        if (Extended.ALGORITHM.equals(algorithm)||Extended.TAIL_ALGORITHM.equals(algorithm)) return Extended.restore(m);
        if (CurveEvaluation.GRAPH_ALGORITHM.equals(algorithm)) return CurveEvaluation.restoreGraph(m);
        if (Operations.ALGORITHM.equals(algorithm)) return Operations.restoreSnapshot(m, Vixen::restoreTree);
        if (ExtendedOps.accepts(algorithm)) return ExtendedOps.restore(m,Vixen::restoreTree);
        if (Influence.ALGORITHM.equals(algorithm) || Influence.ALGORITHM_WINDOWED.equals(algorithm) || Influence.ALGORITHM_ADDITIVE.equals(algorithm)) return restoreInfluence(m);
        if (Tail.ALGORITHM.equals(algorithm)||Tail.COHERENT_ALGORITHM.equals(algorithm)) {
            return new TimelineCurve(VixenJson.tail(stringifyTree(tree)).restore());
        }
        return new TimelineCurve(VixenJson.restore(tree));
    }

    private static Restored restoreInfluence(Map<String, Object> m) {
        boolean extended = !Influence.ALGORITHM.equals(m.get("algorithm"));
        require(m,"algorithm","curve");
        Influence influence = VixenJson.influenceFrame(m,extended);
        Restored restored = restoreTree(m.get("curve"));
        if (!(restored instanceof Curve curve)) throw new IllegalArgumentException("An influence must wrap a curve");
        return replayInfluence(curve,influence);
    }

    static Curve replayInfluence(Curve curve,Influence influence){
        if(curve instanceof TimelineCurve nativeCurve)return new TimelineCurve(nativeCurve.timeline.replayInfluence(influence));
        return new Operations.InfluenceCurve(curve,influence,true);
    }

    static Curve addInfluence(Curve curve, Influence influence) {
        if (curve == null || influence == null) throw new IllegalArgumentException("Curve and influence are required");
        if (curve instanceof TimelineCurve nativeCurve) return new TimelineCurve(nativeCurve.timeline.influence(influence));
        influence.validate(curve.domainStart(), curve.domainEnd());
        return new Operations.InfluenceCurve(curve, influence);
    }

    static Curve requireCurve(Restored value) {
        if (!(value instanceof Curve curve)) throw new IllegalArgumentException("Operation source must restore to a curve");
        return curve;
    }

    private static void validateTree(Object root) {
        record Item(Object value, boolean leave, int depth) {}
        ArrayDeque<Item> stack = new ArrayDeque<>();
        IdentityHashMap<Object, Boolean> active = new IdentityHashMap<>();
        stack.push(new Item(root, false, 0));
        int nodes = 0;
        while (!stack.isEmpty()) {
            Item item = stack.pop();
            Object v = item.value();
            if (item.leave()) { active.remove(v); continue; }
            if (v == null || v instanceof String || v instanceof Boolean) continue;
            if (v instanceof Number n) {
                if (!Double.isFinite(n.doubleValue())) throw new IllegalArgumentException("Replay contains nonfinite numeric data");
                continue;
            }
            if (item.depth() > 128 || ++nodes > 100000 || (!(v instanceof Map<?, ?>) && !(v instanceof List<?>)))
                throw new IllegalArgumentException("Replay contains unsupported or excessive data");
            if (active.containsKey(v)) throw new IllegalArgumentException("Replay cycles are not allowed");
            active.put(v, Boolean.TRUE);
            stack.push(new Item(v, true, item.depth()));
            if (v instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (!(e.getKey() instanceof String)) throw new IllegalArgumentException("Replay object keys must be text");
                    stack.push(new Item(e.getValue(), false, item.depth() + 1));
                }
            } else {
                List<?> list = (List<?>) v;
                for (int i = list.size() - 1; i >= 0; i--) stack.push(new Item(list.get(i), false, item.depth() + 1));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String name) {
        if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException(name + " must be an object");
        return (Map<String, Object>) value;
    }
    private static void only(Map<String, Object> m, java.util.Set<String> allowed, String name) {
        for (String key : m.keySet()) if (!allowed.contains(key)) throw new IllegalArgumentException("Unknown " + name + " field: " + key);
    }
    private static void require(Map<String, Object> m, String... keys) {
        for (String key : keys) if (!m.containsKey(key) || m.get(key) == null) throw new IllegalArgumentException("Missing replay field: " + key);
    }
    private static double number(Object value, String name) {
        if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue())) throw new IllegalArgumentException(name + " must be finite numeric data");
        return n.doubleValue();
    }
    private static String string(Object value, String name) {
        if (!(value instanceof String s)) throw new IllegalArgumentException(name + " must be text");
        return s;
    }

    static String stringifyTree(Object value) {
        StringBuilder out = new StringBuilder();
        appendJson(value, out, new IdentityHashMap<>(), 0);
        return out.toString();
    }
    private static void appendJson(Object value, StringBuilder out, IdentityHashMap<Object, Boolean> active, int depth) {
        if (depth > 128) throw new IllegalArgumentException("JSON nests too deeply");
        if (value == null) { out.append("null"); return; }
        if (value instanceof String s) { out.append(VixenJson.quote(s)); return; }
        if (value instanceof Boolean b) { out.append(b ? "true" : "false"); return; }
        if (value instanceof Byte||value instanceof Short||value instanceof Integer||value instanceof Long) { out.append(value.toString()); return; }
        if (value instanceof Number n) { out.append(VixenJson.num(n.doubleValue())); return; }
        if (active.put(value, Boolean.TRUE) != null) throw new IllegalArgumentException("JSON cycles are not allowed");
        if (value instanceof Map<?, ?> map) {
            out.append('{'); boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!(e.getKey() instanceof String key)) throw new IllegalArgumentException("JSON keys must be text");
                if (!first) out.append(','); first = false;
                out.append(VixenJson.quote(key)).append(':'); appendJson(e.getValue(), out, active, depth + 1);
            }
            out.append('}');
        } else if (value instanceof List<?> list) {
            out.append('[');
            for (int i = 0; i < list.size(); i++) { if (i > 0) out.append(','); appendJson(list.get(i), out, active, depth + 1); }
            out.append(']');
        } else throw new IllegalArgumentException("Unsupported JSON value");
        active.remove(value);
    }

    static final class TimelineCurve extends Curve {
        private final Timeline timeline;
        TimelineCurve(Timeline timeline) { this.timeline = timeline; }
        Timeline timeline(){return timeline;}
        @Override public Sample sample(double x) { return timeline.sample(x); }
        @Override public Sample at(double x, double[] latent) { return timeline.at(x, latent); }
        @Override public Config config() { return timeline.rootConfig(); }
        @Override public double domainStart() { return 1; }
        @Override public double domainEnd() { return timeline.domainEnd(); }
        @Override public double availableFrom() { return timeline.from(); }
        @Override double latestWriteTime(){return timeline.latestWriteTime();}
        @Override public Sample[] sampleRange(double from, double until, int count) { return timeline.sampleRange(from, until, count); }
        @Override public Curve fork(double at, String operation, Double width) { return new TimelineCurve(timeline.fork(at, operation, width)); }
        @Override public String snapshotJson() { return timeline.influences()>32?CurveEvaluation.snapshot(this,null):VixenJson.snapshotText(timeline.snapshot()); }
        @Override public String tailJson(double from) { return timeline.influences()>32?CurveEvaluation.snapshot(this,from):VixenJson.tailText(timeline.tail(from)); }
    }
}