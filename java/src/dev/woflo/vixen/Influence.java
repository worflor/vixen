package dev.woflo.vixen;

/**
 * An influence (vixen-influence.mjs, {@code vixen-influence-c2/1}) with the two extensions the thread asks of Vixen (work items V3 and V4,
 * specified here until Vixen ships them, written as {@code vixen-influence-c2/2} when either is used):
 * <ul>
 * <li><b>Windowed (V3).</b> The pull rises over {@code handoffWidth} from {@code at}, is held until {@code until}, and is released over
 * {@code releaseWidth} on the same quintic: the weight is {@code amount * S((x-at)/H) * (1 - S((x-until)/R))}. Past
 * {@code until + releaseWidth} the influence is transparent (the parent's sample is returned as it is) and the guide's finite domain no
 * longer bounds the timeline. An influence with no {@code until} holds for ever (Vixen's own).</li>
 * <li><b>Deviation units (V4).</b> The guide names a target deviation {@code d(x)} from a reference: {@link Reference#BASELINE} (the value
 * target {@code base * (1 + d)}) or {@link Reference#BACKBONE} (the seeded backbone's value plus {@code base * d}), so a constant or a low
 * polynomial is valid over the whole domain and scale-free. {@link Reference#VALUE} is Vixen's own: the guide is the value.</li>
 * </ul>
 */
public record Influence(double at, double amount, double handoffWidth, Guide guide, Reference reference, double until, double releaseWidth, Mode mode) {

    public enum Mode { BLEND, ADD }

    public Influence(double at, double amount, double handoffWidth, Guide guide, Reference reference, double until, double releaseWidth) {
        this(at, amount, handoffWidth, guide, reference, until, releaseWidth, Mode.BLEND);
    }

    public Influence {
        if (guide == null || reference == null || mode == null) throw new IllegalArgumentException("Guide, reference and mode are required");
    }

    public static Influence additive(double at, double amount, double handoffWidth, double until, double releaseWidth, Guide guide) {
        return new Influence(at, amount, handoffWidth, guide, Reference.BASELINE, until, releaseWidth, Mode.ADD);
    }

    public String algorithm() { return mode == Mode.ADD ? ALGORITHM_ADDITIVE : extended() ? ALGORITHM_WINDOWED : ALGORITHM; }

    /** What a guide's number means. */
    public enum Reference {
        VALUE("value"), BASELINE("baseline"), BACKBONE("backbone");

        public final String word;

        Reference(String word) {
            this.word = word;
        }

        public static Reference parse(String word) {
            for (Reference r : values()) {
                if (r.word.equals(word)) {
                    return r;
                }
            }
            throw new IllegalArgumentException("Unknown influence reference");
        }
    }

    public static final String ALGORITHM = "vixen-influence-c2/1";
    public static final String ALGORITHM_WINDOWED = "vixen-influence-c2/2";
    public static final String ALGORITHM_ADDITIVE = "vixen-influence-c2/3";

    /** Vixen's own influence: a value guide that holds for ever. */
    public static Influence of(double at, double amount, double handoffWidth, Guide guide) {
        return new Influence(at, amount, handoffWidth, guide, Reference.VALUE, Double.POSITIVE_INFINITY, 0);
    }

    /** A windowed influence in deviation units. */
    public static Influence windowed(double at, double amount, double handoffWidth, double until, double releaseWidth, Guide guide, Reference reference) {
        return new Influence(at, amount, handoffWidth, guide, reference, until, releaseWidth);
    }

    public boolean isWindowed() {
        return until != Double.POSITIVE_INFINITY;
    }

    /** True when the schema needs {@link #ALGORITHM_WINDOWED}. */
    public boolean extended() {
        return mode == Mode.ADD || isWindowed() || reference != Reference.VALUE;
    }

    /** The first coordinate at which the influence is transparent for good: {@code until + releaseWidth}, or +infinity. */
    public double end() {
        return isWindowed() ? until + releaseWidth : Double.POSITIVE_INFINITY;
    }

    static double gate(double t) {
        return t <= 0 ? 0 : t >= 1 ? 1 : t * t * t * (10 + t * (-15 + 6 * t));
    }

    private static void finite(double v, double lo, double hi) {
        if (Double.isNaN(v) || Double.isInfinite(v) || v < lo || v > hi) {
            throw new IllegalArgumentException("Influence number outside its finite domain");
        }
    }

    /** Validates the fields against a curve's domain (the rules of {@code makeInfluence}); returns the effective upper domain end. */
    double validate(double curveLo, double curveHi) {
        finite(at, curveLo, curveHi);
        finite(amount, 0, 1);
        finite(handoffWidth, Double.MIN_VALUE, 50000);
        if (guide.lo() > at || at + handoffWidth <= at) {
            throw new IllegalArgumentException("Guide must cover a representable handoff inside the domain");
        }
        if (isWindowed()) {
            finite(until, at, curveHi);
            finite(releaseWidth, Double.MIN_VALUE, 50000);
            if (until < at + handoffWidth) {
                throw new IllegalArgumentException("A window holds after its handoff");
            }
            if (until + releaseWidth <= until || until + releaseWidth > guide.hi() || until + releaseWidth > curveHi) {
                throw new IllegalArgumentException("Guide must cover the window and its release inside the domain");
            }
            return curveHi;
        }
        double end = Math.min(curveHi, guide.hi());
        if (at + handoffWidth > end) {
            throw new IllegalArgumentException("Guide must cover a representable handoff inside the domain");
        }
        return end;
    }

    /** The weight at {@code x}: zero before {@code at} and from {@link #end()}. */
    public double weight(double x) {
        double a;
        if (isWindowed()) {
            if (x >= until + releaseWidth) {
                return 0;
            }
            a = amount * gate((x - at) / handoffWidth) * (1 - gate((x - until) / releaseWidth));
        } else {
            a = amount * gate((x - at) / handoffWidth);
        }
        return a;
    }

    /** Applies the pull to the parent's sample {@code p} at {@code x}: the parent itself where the weight is zero. */
    Sample apply(Sample p, double x, Config cfg) {
        double a = weight(x);
        if (a == 0) {
            return p;
        }
        double g = guide.evaluate(x);
        if (mode == Mode.ADD && zeroGuide()) return p;
        double target = mode == Mode.ADD ? g : switch (reference) {
            case VALUE -> g;
            case BASELINE -> p.base() * (1 + g);
            case BACKBONE -> p.unconstrainedGuideValue() + p.base() * g;
        };
        return affected(p, target, a, cfg, mode == Mode.ADD, reference, g);
    }

    private boolean zeroGuide() {
        if (guide instanceof Guide.Polynomial p) {
            for (double c:p.coefficients()) if (c!=0) return false;
        } else if (guide instanceof Guide.Sampled s) {
            for (Guide.Knot k:s.knots()) if (k.y()!=0 || k.d1()!=0 || k.d2()!=0) return false;
        } else return false;
        return true;
    }

    private static double ramp(double t) {
        return t <= 0 ? 0 : t >= 1 ? t : t * t * t * (6 + t * (-8 + 3 * t));
    }

    private static double policy(double y, Double lo, Double hi, double e) {
        if (lo != null && hi != null && hi <= lo) {
            return lo;
        }
        if (lo != null) {
            if (y <= lo) {
                y = lo;
            } else if (e > 0 && y < lo + e) {
                y = lo + e * ramp((y - lo) / e);
            }
        }
        if (hi != null) {
            if (y >= hi) {
                y = hi;
            } else if (e > 0 && y > hi - e) {
                y = hi - e * ramp((hi - y) / e);
            }
        }
        return y;
    }

    private static Sample affected(Sample p, double target, double a, Config cfg, boolean additive, Reference reference, double guideOutput) {
        double extra = p.base() * a * cfg.boundarySoftness();
        Double lo = p.floorValue();
        Double hi = p.ceilingValue();
        double width = p.boundaryWidth();
        if (lo != null && hi != null) {
            double room = Math.max(0, (hi - lo) / 2 - width);
            if (extra > 0 && room > 0) {
                double increment=extra*room/(room+extra);
                if(!Double.isFinite(increment))increment=extra<=room?extra/(1+extra/room):room/(1+room/extra);
                width+=increment;
            }
        } else if (lo != null || hi != null) {
            width += extra;
        }
        double u = additive ? p.unconstrainedValue() + (reference == Reference.VALUE ? a * target : p.base() * (a * target))
                : a == 1 ? target : (1 - a) * p.unconstrainedValue() + a * target;
        if(!Double.isFinite(u)){
            double[] prior=additive?new double[]{p.unconstrainedValue()}:new double[]{1-a,p.unconstrainedValue()};
            if(reference==Reference.VALUE)u=Floating.sumProducts(new double[][]{prior,{a,guideOutput}});
            else if(additive)u=Floating.sumProducts(new double[][]{prior,{p.base(),a,guideOutput}});
            else if(reference==Reference.BASELINE)u=Floating.sumProducts(new double[][]{prior,{p.base(),a,1+guideOutput}});
            else u=Floating.sumProducts(new double[][]{prior,{a,p.unconstrainedGuideValue()},{p.base(),a,guideOutput}});
        }
        if(!Double.isFinite(u)||!Double.isFinite(width))throw new IllegalArgumentException("Influence sample exceeds the finite numeric range");
        double value = policy(u, lo, hi, width);
        double deviation = (value - p.base()) / p.base();
        double positiveMultiplier = StrictMath.exp(.55 * StrictMath.tanh(deviation));
        double guideValue = policy(p.unconstrainedGuideValue(), lo, hi, width);
        return new Sample(p.x(), value, p.base(), p.score(), p.guideScore(), p.rawDeviation(), (guideValue - p.base()) / p.base(), guideValue,
                p.stabilizationWeight(), deviation, 1 + deviation, .5 + .5 * StrictMath.tanh(deviation), positiveMultiplier,
                p.base() * positiveMultiplier, p.gate(), u, (u - p.base()) / p.base(), p.unconstrainedGuideValue(),
                p.unconstrainedGuideDeviation(), lo, hi, width, value != u);
    }
}
