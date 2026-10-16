package dev.woflo.vixen;

/** The 48 seeded ridges of a leaf (vixen.mjs ridge construction): a direction, a scale, a phase and a slope each. */
final class Ridges {
    static final int FEATURES = 48;

    /** One ridge. */
    record Ridge(int index, double[] direction, double scale, double basePhase, double phase, double slope, double guidePhase, double guideSlope,
                 int channel) {
    }

    private Ridges() {
    }

    static Ridge[] of(Config c, int seed) {
        Ridge[] ridges = new Ridge[FEATURES];
        double[] latent = c.latentRef();
        double[] velocity = c.velocityRef();
        for (int i = 0; i < FEATURES; i++) {
            double[] raw = new double[16];
            for (int j = 0; j < 16; j++) {
                raw[j] = 2 * Hash.unit(seed, i, 20 + j) - 1;
            }
            double[] direction = Noise.normal(raw);
            double scale = (4 + 76 * Hash.unit(seed, i, 1)) * c.tempo();
            double basePhase = Hash.unit(seed, i, 2) * 1024;
            double phase = basePhase + Noise.dot(direction, latent);
            double slope = 1 / scale + Noise.dot(direction, velocity) / 100;
            ridges[i] = new Ridge(i, direction, scale, basePhase, phase, slope, basePhase + Noise.dot(direction, latent) / 32, slope / 32, i + 200);
        }
        return ridges;
    }
}
