package dev.woflo.vixen;

import java.util.*;

/** First-threshold queries whose claims depend on a caller-declared enclosure and continuity contract. */
public final class Crossing {
    private Crossing() {}

    @FunctionalInterface public interface Evaluator { double evaluate(double x); }
    /** source() binds an oracle to the exact curve or evaluator it encloses. The enclosure guarantee is an external assumption. */
    public interface EnclosureProvider {
        Object source();
        double[] enclose(double lo, double hi, String measure);
        default double[] enclosePoint(double x, String measure) { return enclose(x, x, measure); }
    }
    public enum Status { FOUND, ABSENT, UNRESOLVED }
    public record Pair(double lo, double hi) {
        public Pair { if (!Double.isFinite(lo) || !Double.isFinite(hi) || lo > hi) throw new IllegalArgumentException("Invalid finite interval"); }
        public boolean contains(double x) { return lo <= x && x <= hi; }
        public double[] array() { return new double[] {lo, hi}; }
    }
    public record Outstanding(Pair interval, String reason) {}
    public record Options(String measure, boolean continuous, double tolerance, int maxIntervals, int maxIterations, int maxDepth) {
        public static final Options DEFAULT = new Options("deviation", false, 1e-6, 10000, 10000, 64);
        public Options {
            if (measure == null || measure.isEmpty()) throw new IllegalArgumentException("measure must be nonempty");
            if (!Double.isFinite(tolerance) || tolerance <= 0) throw new IllegalArgumentException("tolerance must be finite and positive");
            if (maxIntervals < 1 || maxIterations < 1 || maxDepth < 1) throw new IllegalArgumentException("Budgets must be positive");
        }
    }
    public record Result(Status status, String reason, Pair interval, Pair certifiedBefore, Pair certifiedAbsent,
                         List<Outstanding> unresolved, List<Double> endpointValues, List<Pair> endpointEnclosures,
                         long enclosureChecks, long pointChecks, boolean continuousAssumed, boolean pointIsolated) {
        public Result {
            unresolved = List.copyOf(unresolved);
            endpointValues = List.copyOf(endpointValues);
            endpointEnclosures = List.copyOf(endpointEnclosures);
        }
        public String statusText() { return status.name().toLowerCase(Locale.ROOT); }
    }
    private record Q(double lo, double hi, int depth) {}
    private record Point(double value, Pair enclosure) {}
    private static final class Failure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final String reason;
        Failure(String reason) { super(reason); this.reason = reason; }
    }

    /** Uses the native oracle only for an untransformed native timeline with no influences. */
    public static Result nextCrossing(Curve curve, double threshold, double from, double horizon, Options options) {
        if (curve == null) throw new IllegalArgumentException("Curve is required");
        EnclosureProvider provider;
        try { provider = NativeEnclosure.forCurve(curve); }
        catch (RuntimeException ex) { return unresolved("unsupported-source", from, horizon, options); }
        return nextCrossing(curve, threshold, from, horizon, options, provider);
    }

    public static Result nextCrossing(Curve curve, double threshold, double from, double horizon,
                                      Options options, EnclosureProvider provider) {
        if (curve == null) throw new IllegalArgumentException("Curve is required");
        String measure = options == null ? Options.DEFAULT.measure() : options.measure();
        Evaluator evaluator = x -> {
            Object value = curve.sample(x).asMap().get(measure);
            if (!(value instanceof Number n)) throw new IllegalArgumentException("Missing numeric measure: " + measure);
            return n.doubleValue();
        };
        return run(curve, evaluator, provider, threshold, from, horizon, options);
    }

    public static Result nextCrossing(Evaluator evaluator, EnclosureProvider provider, double threshold,
                                      double from, double horizon, Options options) {
        if (evaluator == null) throw new IllegalArgumentException("Evaluator is required");
        return run(evaluator, evaluator, provider, threshold, from, horizon, options);
    }

    private static Result run(Object source, Evaluator evaluator, EnclosureProvider provider, double threshold,
                              double from, double horizon, Options supplied) {
        Options o = Objects.requireNonNull(supplied, "options");
        if (!Double.isFinite(threshold) || !Double.isFinite(from) || !Double.isFinite(horizon) || horizon < from)
            throw new IllegalArgumentException("Threshold and ordered query bounds must be finite");
        if (provider == null) return unresolved("enclosure-unavailable", from, horizon, o);
        if (provider.source() != source) return unresolved("source-mismatch", from, horizon, o);
        if (source instanceof Curve c && (from < c.domainStart() || horizon > c.domainEnd() || from < c.availableFrom()))
            throw new IllegalArgumentException("Query outside the available curve domain");

        ArrayDeque<Q> stack = new ArrayDeque<>();
        stack.push(new Q(from, horizon, 0));
        Map<Double, Point> points = new HashMap<>();
        long[] counts = {0, 0};
        int budget = Math.min(o.maxIntervals(), o.maxIterations());
        java.util.function.Function<Double, Point> point = x -> {
            Point old = points.get(x);
            if (old != null) return old;
            try {
                counts[1]++;
                Pair box = checked(provider.enclosePoint(x, o.measure()));
                double value = evaluator.evaluate(x);
                if (!Double.isFinite(value)) throw new Failure("invalid-evaluator");
                if (!box.contains(value)) throw new Failure("invalid-enclosure");
                Point p = new Point(value, box);
                points.put(x, p);
                return p;
            } catch (Failure f) { throw f; }
            catch (RuntimeException ex) { throw new Failure("invalid-enclosure"); }
        };
        while (!stack.isEmpty()) {
            Q q = stack.pop();
            if (counts[0] >= budget) return unresolved(q, stack, from, "budget", counts, o);
            Pair box;
            try {
                counts[0]++;
                box = checked(provider.enclose(q.lo(), q.hi(), o.measure()));
            } catch (RuntimeException ex) { return unresolved(q, stack, from, "invalid-enclosure", counts, o); }
            if (threshold < box.lo() || threshold > box.hi()) continue;
            Point left, right;
            try {
                left = point.apply(q.lo());
                right = point.apply(q.hi());
                if (!box.contains(left.value()) || !box.contains(right.value()))
                    return unresolved(q, stack, from, "invalid-enclosure", counts, o);
            } catch (Failure f) { return unresolved(q, stack, from, f.reason, counts, o); }
            if (left.enclosure().lo() == threshold && left.enclosure().hi() == threshold)
                return found(q.lo(), q.lo(), left, left, from, counts, o, true);
            double width = q.hi() - q.lo();
            if (width <= o.tolerance() / 2) {
                boolean exactRight = right.enclosure().lo() == threshold && right.enclosure().hi() == threshold;
                if (exactRight || o.continuous() && opposite(left.enclosure(), right.enclosure()))
                    return found(q.lo(), q.hi(), left, right, from, counts, o, false);
                if (!o.continuous()) return unresolved(q, stack, from, "continuity-required", counts, o);
                double lo = Math.max(from, q.lo() - o.tolerance() / 4);
                double hi = Math.min(horizon, q.hi() + o.tolerance() / 4);
                if (hi - lo <= o.tolerance()) {
                    try {
                        Point a = point.apply(lo), b = point.apply(hi);
                        if (opposite(a.enclosure(), b.enclosure())) return found(lo, hi, a, b, from, counts, o, lo == hi);
                    } catch (Failure f) { return unresolved(q, stack, from, f.reason, counts, o); }
                }
            }
            double mid = q.lo() / 2 + q.hi() / 2;
            if (q.depth() >= o.maxDepth()) return unresolved(q, stack, from, "depth", counts, o);
            if (mid == q.lo() || mid == q.hi()) return unresolved(q, stack, from, "machine-spacing", counts, o);
            stack.push(new Q(mid, q.hi(), q.depth() + 1));
            stack.push(new Q(q.lo(), mid, q.depth() + 1));
        }
        return new Result(Status.ABSENT, null, new Pair(from, horizon), null, new Pair(from, horizon),
                List.of(), List.of(), List.of(), counts[0], counts[1], o.continuous(), false);
    }

    private static Pair checked(double[] pair) {
        if (pair == null || pair.length != 2 || !Double.isFinite(pair[0]) || !Double.isFinite(pair[1]) || pair[0] > pair[1])
            throw new IllegalArgumentException("Oracle must return an ordered finite pair");
        return new Pair(pair[0], pair[1]);
    }
    private static boolean opposite(Pair a, Pair b) {
        return a.hi() < b.lo() && b.lo() > a.hi() && a.hi() < b.lo() && b.lo() > a.hi()
                ? false : (a.hi() < b.lo() && b.lo() > a.hi() && a.hi() < b.lo() && b.lo() > a.hi());
    }
    private static boolean oppositeStrict(Pair a, Pair b) {
        return a.hi() < b.lo() || b.hi() < a.lo();
    }
    private static boolean opposite(Pair a, Pair b, double threshold) {
        return a.hi() < threshold && b.lo() > threshold || a.lo() > threshold && b.hi() < threshold;
    }
    private static Result found(double lo, double hi, Point a, Point b, double from, long[] counts, Options o, boolean isolated) {
        return new Result(Status.FOUND, null, new Pair(lo, hi), new Pair(from, lo), null, List.of(),
                List.of(a.value(), b.value()), List.of(a.enclosure(), b.enclosure()), counts[0], counts[1], o.continuous(), isolated);
    }
    private static Result unresolved(Q q, ArrayDeque<Q> stack, double from, String reason, long[] counts, Options o) {
        List<Outstanding> items = new ArrayList<>();
        items.add(new Outstanding(new Pair(q.lo(), q.hi()), reason));
        for (Q rest : stack) items.add(new Outstanding(new Pair(rest.lo(), rest.hi()), "not-visited"));
        items.sort(Comparator.comparingDouble(a -> a.interval().lo()));
        return new Result(Status.UNRESOLVED, reason, new Pair(q.lo(), q.hi()), new Pair(from, q.lo()), null,
                items, List.of(), List.of(), counts[0], counts[1], o.continuous(), false);
    }
    private static Result unresolved(String reason, double from, double horizon, Options o) {
        Options safe = o == null ? Options.DEFAULT : o;
        double a = Double.isFinite(from) ? from : 0, b = Double.isFinite(horizon) && horizon >= a ? horizon : a;
        Pair p = new Pair(a, b);
        return new Result(Status.UNRESOLVED, reason, p, new Pair(a, a), null,
                List.of(new Outstanding(p, reason)), List.of(), List.of(), 0, 0, safe.continuous(), false);
    }

    /** Builds a declared Lipschitz enclosure around a midpoint evaluation. The bound is the caller's assumption. */
    public static EnclosureProvider makeLipschitzEnclosure(Evaluator evaluator, double bound, double pointError) {
        if (evaluator == null) throw new IllegalArgumentException("Evaluator is required");
        if (!Double.isFinite(bound) || bound < 0 || !Double.isFinite(pointError) || pointError < 0)
            throw new IllegalArgumentException("Invalid Lipschitz bound or point error");
        return new EnclosureProvider() {
            public Object source() { return evaluator; }
            public double[] enclose(double lo, double hi, String measure) {
                if (!Double.isFinite(lo) || !Double.isFinite(hi) || lo > hi) throw new IllegalArgumentException("Invalid interval");
                double mid = Math.max(lo,Math.min(hi,lo / 2 + hi / 2)), value = evaluator.evaluate(mid);
                if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite midpoint evaluation");
                if(lo==hi||bound==0){
                    if(pointError==0)return new double[]{value,value};
                    return new double[]{Math.nextDown(value-pointError),Math.nextUp(value+pointError)};
                }
                double d = Math.max(Math.nextUp(mid - lo), Math.nextUp(hi - mid));
                double radius = Math.nextUp(Math.nextUp(bound * d) + pointError);
                double low = Math.nextDown(value - radius), high = Math.nextUp(value + radius);
                if (!Double.isFinite(low) || !Double.isFinite(high)) throw new ArithmeticException("No finite enclosure");
                return new double[] {low, high};
            }
        };
    }
}
