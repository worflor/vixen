package dev.woflo.vixen;

/**
 * A typed seed, as Vixen keeps it: text, or a uint32 (a number). The two are different seeds even when they look alike
 * ({@code "42"} and {@code 42}), and the type is part of how a fork's future seed is derived (vixen.mjs {@code forkSeed}).
 */
public record Seed(String text, int number) {
    /** A text seed (at most 1,024 UTF-16 code units, as Vixen's). */
    public static Seed of(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Seed must be text or uint32");
        }
        if (text.length() > 1024) {
            throw new IllegalArgumentException("Text seed limit: 1024 UTF-16 code units");
        }
        return new Seed(text, 0);
    }

    /** A numeric seed: a uint32. */
    public static Seed of(long number) {
        if (number < 0 || number > 0xffffffffL) {
            throw new IllegalArgumentException("Numeric seed must be uint32");
        }
        return new Seed(null, (int) number);
    }

    public boolean isText() {
        return text != null;
    }

    /** {@code seed32}: a number is itself, text is FNV-1a over its UTF-8 bytes. */
    public int u32() {
        return isText() ? Hash.seed32(text) : number;
    }

    /** The numeric seed as an unsigned value. */
    public long unsigned() {
        return number & 0xffffffffL;
    }
}
