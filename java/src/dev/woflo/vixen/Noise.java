package dev.woflo.vixen;

/** The counter-addressed C2 lattice noise and the 16-dimensional vectors the ridges are made of (vixen.mjs {@code noise}, {@code dot}, {@code normal}). */
final class Noise {
    private Noise() {
    }

    /** {@code noise(p, channel)}: a smooth5 interpolation between two hashed lattice values, in [-1, 1]. */
    static double at(int seed, double p, int channel) {
        double k = StrictMath.floor(p);
        double t = p - k;
        double a = 2 * Hash.unit(seed, Hash.toUint32(k), channel) - 1;
        double b = 2 * Hash.unit(seed, Hash.toUint32(k + 1), channel) - 1;
        return a + (b - a) * Opening.smooth5(t);
    }

    static double dot(double[] a, double[] b) {
        double n = 0;
        for (int j = 0; j < 16; j++) {
            n += a[j] * b[j];
        }
        return n;
    }

    static double[] normal(double[] a) {
        double n = StrictMath.sqrt(dot(a, a));
        double[] out = new double[16];
        if (n == 0) {
            out[0] = 1;
            return out;
        }
        for (int j = 0; j < 16; j++) {
            out[j] = a[j] / n;
        }
        return out;
    }
}
