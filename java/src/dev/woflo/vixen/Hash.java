package dev.woflo.vixen;

/**
 * Vixen's integer hashing, exact: Java {@code int} arithmetic wraps as {@code Math.imul} does, and a uint32 is the {@code int}'s
 * 32 bits (compare unsigned, convert with {@link #unit}). vixen.mjs {@code seed32}, {@code hash32}, {@code unit}.
 */
public final class Hash {
    private Hash() {
    }

    /** FNV-1a over the UTF-8 bytes of a text; an unpaired surrogate encodes as U+FFFD, as {@code TextEncoder} does. */
    public static int seed32(String text) {
        if (text.length() > 1024) {
            throw new IllegalArgumentException("Text seed limit: 1024 UTF-16 code units");
        }
        int h = 0x811c9dc5;
        int n = text.length();
        for (int i = 0; i < n; i++) {
            int c = text.charAt(i);
            int cp;
            if (Character.isHighSurrogate((char) c) && i + 1 < n && Character.isLowSurrogate(text.charAt(i + 1))) {
                cp = Character.toCodePoint((char) c, text.charAt(i + 1));
                i++;
            } else if (Character.isSurrogate((char) c)) {
                cp = 0xFFFD;
            } else {
                cp = c;
            }
            if (cp < 0x80) {
                h = step(h, cp);
            } else if (cp < 0x800) {
                h = step(h, 0xC0 | (cp >> 6));
                h = step(h, 0x80 | (cp & 0x3F));
            } else if (cp < 0x10000) {
                h = step(h, 0xE0 | (cp >> 12));
                h = step(h, 0x80 | ((cp >> 6) & 0x3F));
                h = step(h, 0x80 | (cp & 0x3F));
            } else {
                h = step(h, 0xF0 | (cp >> 18));
                h = step(h, 0x80 | ((cp >> 12) & 0x3F));
                h = step(h, 0x80 | ((cp >> 6) & 0x3F));
                h = step(h, 0x80 | (cp & 0x3F));
            }
        }
        return h;
    }

    private static int step(int h, int b) {
        return (h ^ b) * 16777619;
    }

    /** {@code hash32(seed, index, channel)}. */
    public static int hash32(int seed, int index, int channel) {
        int h = seed ^ (index * 0x9e3779b9) ^ (channel * 0x85ebca6b);
        h = (h ^ (h >>> 16)) * 0x7feb352d;
        h = (h ^ (h >>> 15)) * 0x846ca68b;
        return h ^ (h >>> 16);
    }

    /** {@code hash32(...) / 2^32}: a uniform in [0, 1). */
    public static double unit(int seed, int index, int channel) {
        return (hash32(seed, index, channel) & 0xffffffffL) / 4294967296.0;
    }

    /** JavaScript's {@code ToUint32} of a finite double, as the low 32 bits: what {@code k>>>0} makes of {@code Math.floor(p)}. */
    static int toUint32(double v) {
        return (int) (long) v;
    }

    /** The future seed of a fork: {@code hash32(hash32(seed32(parent), typeTag, seed32(operation)), hi(at), lo(at))} over the IEEE-754 bits of {@code at}. */
    public static int forkSeed(Seed parent, String operation, double at) {
        long bits = Double.doubleToRawLongBits(at);
        int inner = hash32(parent.u32(), parent.isText() ? 1 : 2, seed32(operation));
        return hash32(inner, (int) (bits >>> 32), (int) bits);
    }
}
