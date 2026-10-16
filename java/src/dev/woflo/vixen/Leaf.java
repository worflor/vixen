package dev.woflo.vixen;

import java.util.concurrent.atomic.AtomicLong;

/**
 * The hyperfield leaf, {@code vixen-hyperfield-c2/4} (vixen.mjs {@code createLeaf}): a seeded field of 48 ridges in sixteen dimensions
 * under an authored baseline, with an entrance gate, stabilization and the boundary policies. Immutable; a pure function of its
 * configuration and the coordinate. The instrument, callback capture and {@code inspect} are not ported.
 */
public final class Leaf {
    public static final String ALGORITHM = "vixen-hyperfield-c2/4";

    private final Config c;
    private final int seed;
    private final double[] domain;
    private final Opening.Dip[] dips;
    private final Ridges.Ridge[] ridges;
    private final double stabilizationWeight;
    private final Bounds bounds;
    private final AtomicLong work;

    /** The envelope the leaf promises (vixen.mjs {@code bounds}). */
    public record Bounds(double unconstrainedSpatialSlope, double deviationLow, double deviationHigh) {
    }

    private Leaf(Config c, AtomicLong work) {
        this.c = c;
        this.work = work;
        this.seed = c.seed().u32();
        this.stabilizationWeight = Opening.smooth5(c.stabilizer());
        this.domain = new double[] {1, Math.min(50000, 1 + (50000 - 1) * (c.openingEndpoint() - 1) / 99)};
        this.dips = Opening.dips(seed, c.introVariation());
        this.ridges = Ridges.of(c, seed);
        double w = stabilizationWeight;
        double envelope = (2.2 * (1 - w) + .25 * w) * c.strength();
        double lower = c.floor() == Config.Floor.NONE ? -envelope : c.floor() == Config.Floor.ZERO ? -1 : c.floor() == Config.Floor.BASELINE ? 0 : -.25 * c.strength();
        double upper = c.ceilingMargin() == null ? envelope : (c.floor() == Config.Floor.BASELINE ? 0 : .25 * c.strength()) + c.ceilingMargin();
        double slope = c.strength() * ((1 - w) * 33 * c.contrast() / 4 + w * 15 / 256);
        this.bounds = new Bounds(slope, lower, upper);
    }

    /** Creates a leaf. {@code work} (nullable) counts each ridge-field evaluation, for the tests that count work. */
    public static Leaf create(Config config, AtomicLong work) {
        return new Leaf(config, work);
    }

    public static Leaf create(Config config) {
        return new Leaf(config, null);
    }

    public Config config() {
        return c;
    }

    /** The canonical clock domain {@code [1, end]}. */
    public double[] domain() {
        return domain.clone();
    }

    public double domainEnd() {
        return domain[1];
    }

    public Bounds bounds() {
        return bounds;
    }

    private double clock(double x) {
        return c.openingEndpoint() == 100 ? x : 1 + (x - 1) * 99 / (c.openingEndpoint() - 1);
    }

    static void coordinate(double x) {
        if (Double.isNaN(x) || Double.isInfinite(x) || x < 1 || x > 50000) {
            throw new IllegalArgumentException("x must be finite in [1,50000]");
        }
    }

    private void check(double x) {
        coordinate(x);
        if (x > domain[1]) {
            throw new IllegalArgumentException("x exceeds the canonical clock domain");
        }
    }

    /** The sample at the leaf's own latent position and velocity. */
    public Sample sample(double x) {
        check(x);
        double score = 0;
        double guideScore = 0;
        if (x > c.openingEndpoint()) {
            if (work != null) {
                work.incrementAndGet();
            }
            double u = clock(x) - 100;
            for (Ridges.Ridge r : ridges) {
                score += Noise.at(seed, r.phase() + u * r.slope(), r.channel()) / Ridges.FEATURES;
                guideScore += Noise.at(seed, r.guidePhase() + u * r.guideSlope(), r.channel()) / Ridges.FEATURES;
            }
        }
        return result(x, score, guideScore);
    }

    /** The sample at a given latent position (vixen.mjs {@code at}). */
    public Sample at(double x, double[] h) {
        double[] latent = Config.vector(h, 1000000);
        check(x);
        double score = 0;
        double guideScore = 0;
        if (x > c.openingEndpoint()) {
            if (work != null) {
                work.incrementAndGet();
            }
            double u = clock(x) - 100;
            for (Ridges.Ridge r : ridges) {
                double projection = Noise.dot(r.direction(), latent);
                score += Noise.at(seed, r.basePhase() + projection + u / r.scale(), r.channel()) / Ridges.FEATURES;
                guideScore += Noise.at(seed, r.basePhase() + projection / 32 + u / (32 * r.scale()), r.channel()) / Ridges.FEATURES;
            }
        }
        return result(x, score, guideScore);
    }

    /** The latent position at {@code x}: the start carried along the velocity. */
    public double[] location(double x) {
        check(x);
        double t = Math.max(0, clock(x) - 100) / 100;
        double[] out = new double[16];
        for (int i = 0; i < 16; i++) {
            out[i] = c.latentRef()[i] + t * c.velocityRef()[i];
        }
        return out;
    }

    private Sample result(double x, double score, double guideScore) {
        double gate = Opening.smooth5((x - c.openingEndpoint()) / c.ramp());
        double rawDeviation = c.strength() * gate * 2.2 * StrictMath.tanh(c.contrast() * score);
        double unconstrainedGuideDeviation = c.strength() * gate * .25 * StrictMath.tanh(2 * guideScore);
        double w = stabilizationWeight;
        double u = w == 0 ? rawDeviation : w == 1 ? unconstrainedGuideDeviation : rawDeviation + (unconstrainedGuideDeviation - rawDeviation) * w;
        double base = Opening.authored(clock(x), c.introVariation(), dips);
        double unconstrainedValue = base + base * u;
        double unconstrainedGuideValue = base + base * unconstrainedGuideDeviation;
        Policy.Limits lim = Policy.limits(c, base, unconstrainedGuideValue, gate);
        double value = Policy.apply(unconstrainedValue, lim);
        double guideValue = Policy.apply(unconstrainedGuideValue, lim);
        double deviation = c.unconstrained() ? u : (value - base) / base;
        double guideDeviation = c.unconstrained() ? unconstrainedGuideDeviation : (guideValue - base) / base;
        double positiveMultiplier = StrictMath.exp(.55 * StrictMath.tanh(deviation));
        return new Sample(x, value, base, score, guideScore, rawDeviation, guideDeviation, guideValue, w, deviation, 1 + deviation,
                .5 + .5 * StrictMath.tanh(deviation), positiveMultiplier, base * positiveMultiplier, gate, unconstrainedValue, u,
                unconstrainedGuideValue, unconstrainedGuideDeviation, lim.floorValue(), lim.ceilingValue(), lim.boundaryWidth(), value != unconstrainedValue);
    }

    /** vixen.mjs {@code navigationBasis}: the plane of a navigation index, two orthonormal sixteen-vectors. */
    public static double[][] navigationBasis(int index) {
        if (index < 0 || index > 100000) {
            throw new IllegalArgumentException("Plane index must be an integer in [0,100000]");
        }
        double[] u = new double[16];
        double[] v = new double[16];
        if (index == 0) {
            u[0] = 1;
            v[1] = 1;
        } else {
            int seed = Hash.seed32("vixen-navigation");
            double[] ru = new double[16];
            for (int j = 0; j < 16; j++) {
                ru[j] = 2 * Hash.unit(seed, index, 100 + j) - 1;
            }
            u = Noise.normal(ru);
            double[] raw = new double[16];
            for (int j = 0; j < 16; j++) {
                raw[j] = 2 * Hash.unit(seed, index, 200 + j) - 1;
            }
            double proj = Noise.dot(raw, u);
            double[] w = new double[16];
            for (int j = 0; j < 16; j++) {
                w[j] = raw[j] - proj * u[j];
            }
            if (Noise.dot(w, w) < 1e-20) {
                int k = 0;
                for (int j = 0; j < 16; j++) {
                    if (Math.abs(u[j]) < Math.abs(u[k])) {
                        k = j;
                    }
                }
                for (int j = 0; j < 16; j++) {
                    w[j] = (j == k ? 1 : 0) - u[k] * u[j];
                }
            }
            v = Noise.normal(w);
        }
        return new double[][] {u, v};
    }
}
