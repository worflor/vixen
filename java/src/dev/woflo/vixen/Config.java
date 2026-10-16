package dev.woflo.vixen;

import java.util.Arrays;

/**
 * A leaf's configuration, vixen.mjs {@code DEFAULTS}: validated on construction by the rules of {@code createLeaf}. Immutable;
 * {@code latent} and {@code velocity} are copied in and out.
 */
public final class Config {
    public enum Floor {
        NONE("none"), ZERO("zero"), BASELINE("baseline"), BACKBONE("backbone");

        public final String word;

        Floor(String word) {
            this.word = word;
        }

        public static Floor parse(String word) {
            for (Floor f : values()) {
                if (f.word.equals(word)) {
                    return f;
                }
            }
            throw new IllegalArgumentException("Invalid floor");
        }
    }

    public static final int DIMENSIONS = 16;

    private final Seed seed;
    private final double openingEndpoint;
    private final double introVariation;
    private final Floor floor;
    private final Double ceilingMargin;
    private final double boundarySoftness;
    private final double stabilizer;
    private final double strength;
    private final double ramp;
    private final double tempo;
    private final double contrast;
    private final double[] latent;
    private final double[] velocity;

    public Config(Seed seed, double openingEndpoint, double introVariation, Floor floor, Double ceilingMargin, double boundarySoftness,
                  double stabilizer, double strength, double ramp, double tempo, double contrast, double[] latent, double[] velocity) {
        if (ceilingMargin != null && !(finite(ceilingMargin) && ceilingMargin >= 0 && ceilingMargin <= 16)) {
            throw new IllegalArgumentException("ceilingMargin must be null or in [0,16]");
        }
        range("openingEndpoint", openingEndpoint, 2, 50000);
        range("introVariation", introVariation, 0, 1);
        range("boundarySoftness", boundarySoftness, 0, 1);
        range("stabilizer", stabilizer, 0, 1);
        range("strength", strength, 0, 2);
        range("ramp", ramp, 1, 64);
        range("tempo", tempo, .25, 4);
        range("contrast", contrast, 0, 16);
        this.seed = seed;
        this.openingEndpoint = openingEndpoint;
        this.introVariation = introVariation;
        this.floor = floor;
        this.ceilingMargin = ceilingMargin;
        this.boundarySoftness = boundarySoftness;
        this.stabilizer = stabilizer;
        this.strength = strength;
        this.ramp = ramp;
        this.tempo = tempo;
        this.contrast = contrast;
        this.latent = vector(latent, 128);
        this.velocity = vector(velocity, 4);
    }

    private static void range(String name, double v, double lo, double hi) {
        if (!finite(v) || v < lo || v > hi) {
            throw new IllegalArgumentException(name + " must lie in [" + lo + "," + hi + "]");
        }
    }

    private static boolean finite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }

    static double[] vector(double[] a, double limit) {
        if (a == null || a.length != DIMENSIONS) {
            throw new IllegalArgumentException("A vector must contain exactly 16 coordinates");
        }
        for (double n : a) {
            if (!finite(n) || Math.abs(n) > limit) {
                throw new IllegalArgumentException("Coordinates must be finite in [-" + limit + "," + limit + "]");
            }
        }
        return a.clone();
    }

    /** vixen.mjs {@code DEFAULTS}. */
    public static Config defaults() {
        return new Config(Seed.of("vixen"), 100, 0, Floor.NONE, null, .1, 0, 1, 16, .8, 10, new double[DIMENSIONS], new double[DIMENSIONS]);
    }

    public Config withSeed(Seed s) {
        return new Config(s, openingEndpoint, introVariation, floor, ceilingMargin, boundarySoftness, stabilizer, strength, ramp, tempo, contrast,
                latent, velocity);
    }

    public Config withLatent(double[] l) {
        return new Config(seed, openingEndpoint, introVariation, floor, ceilingMargin, boundarySoftness, stabilizer, strength, ramp, tempo, contrast,
                l, velocity);
    }

    public Config withVelocity(double[] v) {
        return new Config(seed, openingEndpoint, introVariation, floor, ceilingMargin, boundarySoftness, stabilizer, strength, ramp, tempo, contrast,
                latent, v);
    }

    public Seed seed() {
        return seed;
    }

    public double openingEndpoint() {
        return openingEndpoint;
    }

    public double introVariation() {
        return introVariation;
    }

    public Floor floor() {
        return floor;
    }

    /** The ceiling margin, or null. */
    public Double ceilingMargin() {
        return ceilingMargin;
    }

    public double boundarySoftness() {
        return boundarySoftness;
    }

    public double stabilizer() {
        return stabilizer;
    }

    public double strength() {
        return strength;
    }

    public double ramp() {
        return ramp;
    }

    public double tempo() {
        return tempo;
    }

    public double contrast() {
        return contrast;
    }

    public double[] latent() {
        return latent.clone();
    }

    public double[] velocity() {
        return velocity.clone();
    }

    double[] latentRef() {
        return latent;
    }

    double[] velocityRef() {
        return velocity;
    }

    /** True when the leaf has neither floor nor ceiling, so deviation is the stabilized modulation itself. */
    public boolean unconstrained() {
        return floor == Floor.NONE && ceilingMargin == null;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Config c && seed.equals(c.seed) && openingEndpoint == c.openingEndpoint && introVariation == c.introVariation
                && floor == c.floor && java.util.Objects.equals(ceilingMargin, c.ceilingMargin) && boundarySoftness == c.boundarySoftness
                && stabilizer == c.stabilizer && strength == c.strength && ramp == c.ramp && tempo == c.tempo && contrast == c.contrast
                && Arrays.equals(latent, c.latent) && Arrays.equals(velocity, c.velocity);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(seed, openingEndpoint, floor, ceilingMargin, strength, Arrays.hashCode(latent));
    }
}
