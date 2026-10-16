package dev.woflo.vixen;

/** The authored positive baseline and its seeded dips: vixen.mjs {@code baseline}, {@code smooth5}, {@code boundaryRamp}, {@code authored}. */
final class Opening {
    private Opening() {
    }

    static double smooth5(double t) {
        if (t <= 0) {
            return 0;
        }
        if (t >= 1) {
            return 1;
        }
        return t * t * t * (10 + t * (-15 + 6 * t));
    }

    static double boundaryRamp(double t) {
        if (Double.isNaN(t) || Double.isInfinite(t)) {
            throw new IllegalArgumentException("Finite boundary coordinate required");
        }
        return t <= 0 ? 0 : t >= 1 ? t : t * t * t * (6 + t * (-8 + 3 * t));
    }

    static double baseline(double x) {
        double a = x - 42;
        double b = x - 71;
        return x * StrictMath.exp(x / 100) - 25 * StrictMath.exp(-(a * a) / 32) - 38 * StrictMath.exp(-(b * b) / 28);
    }

    /** One seeded dip of the authored opening. */
    record Dip(double center, double width, double depth) {
    }

    static Dip[] dips(int seed, double v) {
        double u00 = Hash.unit(seed, 0, 700), u01 = Hash.unit(seed, 0, 701), u02 = Hash.unit(seed, 0, 702);
        double w0 = 4 + 2 * u01;
        Dip d0 = new Dip(42 + v * (32 + 15 * u00 - 42), StrictMath.sqrt(32 + v * (w0 * w0 - 32)), 25 + v * (18 + 6 * u02 - 25));
        double u10 = Hash.unit(seed, 1, 700), u11 = Hash.unit(seed, 1, 701), u12 = Hash.unit(seed, 1, 702);
        double w1 = 4 + 3 * u11;
        Dip d1 = new Dip(71 + v * (62 + 20 * u10 - 71), StrictMath.sqrt(28 + v * (w1 * w1 - 28)), 38 + v * (35 + 13 * u12 - 38));
        return new Dip[] {d0, d1};
    }

    static double authored(double t, double introVariation, Dip[] dips) {
        double old = baseline(t);
        if (introVariation == 0 || t >= 100) {
            return old;
        }
        double sum = 0;
        for (Dip d : dips) {
            double z = (t - d.center) / d.width;
            sum = sum + d.depth * StrictMath.exp(-(z * z));
        }
        double fresh = t * StrictMath.exp(t / 100) - sum;
        double weight = 1 - smooth5((t - 90) / 10);
        return old + (fresh - old) * weight;
    }
}
