package dev.woflo.vixen;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** An immutable, finite, explicitly declared weighted population for occupancy measurements. */
public final class OccupancyPopulation {
    /** Hard row-count limit; calibration also exposes a per-call limit no larger than this. */
    public static final int MAX_POPULATION = 1_000_000;

    private static final BigInteger FIVE_TO_1074 = BigInteger.valueOf(5).pow(1074);
    private static final BigInteger TWO_TO_1074 = BigInteger.ONE.shiftLeft(1074);
    private static final MathContext RATIO_CONTEXT = new MathContext(1100, RoundingMode.HALF_EVEN);
    private static final byte[] WITNESS_PREFIX = "vixen-occupancy-population/1\u0000".getBytes(StandardCharsets.UTF_8);

    /** One finite deviation with a strictly positive finite binary64 weight. */
    public record Row(double deviation, double weight) {
        public Row {
            if (!Double.isFinite(deviation)) {
                throw new IllegalArgumentException("deviation must be finite");
            }
            if (!Double.isFinite(weight) || weight <= 0.0) {
                throw new IllegalArgumentException("weight must be finite and positive");
            }
            // JavaScript's Set and equality checks treat -0 and +0 as the same population value.
            if (deviation == 0.0) {
                deviation = 0.0;
            }
        }
    }

    private final List<Row> rows;
    private final BigInteger totalWeightUnits;
    private final double totalWeight;
    private final String witness;

    /** Creates a weighted population. Rows are copied and retained in their supplied order. */
    public OccupancyPopulation(List<Row> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException("population must be nonempty");
        }
        if (rows.size() > MAX_POPULATION) {
            throw new IllegalArgumentException("population exceeds MAX_POPULATION=" + MAX_POPULATION);
        }
        ArrayList<Row> copy = new ArrayList<>(rows.size());
        BigInteger sum = BigInteger.ZERO;
        for (Row row : rows) {
            if (row == null) {
                throw new IllegalArgumentException("population rows must not be null");
            }
            copy.add(row);
            sum = sum.add(units(row.weight()));
        }
        double roundedWeight = unitsAsDouble(sum);
        if (!Double.isFinite(roundedWeight) || roundedWeight <= 0.0) {
            throw new IllegalArgumentException("Total population weight must be finite and positive");
        }
        this.rows = List.copyOf(copy);
        this.totalWeightUnits = sum;
        this.totalWeight = roundedWeight;
        this.witness = populationWitness(this.rows);
    }

    public OccupancyPopulation(Row... rows) {
        this(rows == null ? null : List.of(rows));
    }

    /** Builds a unit-weight population from finite deviations. */
    public static OccupancyPopulation unweighted(double... deviations) {
        if (deviations == null || deviations.length == 0) {
            throw new IllegalArgumentException("population must be nonempty");
        }
        ArrayList<Row> rows = new ArrayList<>(deviations.length);
        for (double deviation : deviations) {
            rows.add(new Row(deviation, 1.0));
        }
        return new OccupancyPopulation(rows);
    }

    /** Builds a unit-weight population from a collection of finite deviations. */
    public static OccupancyPopulation unweighted(Collection<Double> deviations) {
        if (deviations == null || deviations.isEmpty()) {
            throw new IllegalArgumentException("population must be nonempty");
        }
        ArrayList<Row> rows = new ArrayList<>(deviations.size());
        for (Double deviation : deviations) {
            if (deviation == null) {
                throw new IllegalArgumentException("population deviations must not be null");
            }
            rows.add(new Row(deviation, 1.0));
        }
        return new OccupancyPopulation(rows);
    }

    /** Builds a weighted population from rows. */
    public static OccupancyPopulation weighted(Collection<Row> rows) {
        if (rows == null) {
            throw new IllegalArgumentException("population is required");
        }
        return new OccupancyPopulation(new ArrayList<>(rows));
    }

    public List<Row> rows() {
        return rows;
    }

    public int totalPopulation() {
        return rows.size();
    }

    /** Correctly rounded binary64 sum of the exact binary64 row weights. */
    public double totalWeight() {
        return totalWeight;
    }

    /** SHA-256 witness over the canonical weighted multiset, including exact binary64 values. */
    public String witness() {
        return witness;
    }

    BigInteger totalWeightUnits() {
        return totalWeightUnits;
    }

    static BigInteger units(double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException("A finite nonnegative binary64 value is required");
        }
        long bits = Double.doubleToRawLongBits(value);
        int encodedExponent = (int) ((bits >>> 52) & 0x7ffL);
        long significand = bits & 0x000f_ffff_ffff_ffffL;
        if (encodedExponent == 0x7ff) {
            throw new IllegalArgumentException("A finite binary64 value is required");
        }
        if (encodedExponent != 0) {
            significand |= 1L << 52;
        }
        if (significand == 0L) {
            return BigInteger.ZERO;
        }
        // Every finite binary64 value is an integer multiple of 2^-1074.
        int shift = encodedExponent == 0 ? 0 : encodedExponent - 1;
        return BigInteger.valueOf(significand).shiftLeft(shift);
    }

    static double unitsAsDouble(BigInteger units) {
        if (units == null || units.signum() < 0) {
            throw new IllegalArgumentException("Nonnegative exact weight units are required");
        }
        if (units.signum() == 0) {
            return 0.0;
        }
        // units * 2^-1074, expressed as an exact terminating decimal before binary64 rounding.
        BigDecimal exact = new BigDecimal(units.multiply(FIVE_TO_1074), 1074);
        return exact.doubleValue();
    }

    static double ratioAsDouble(BigInteger numerator, BigInteger denominator) {
        if (numerator == null || denominator == null || numerator.signum() < 0 || denominator.signum() <= 0
                || numerator.compareTo(denominator) > 0) {
            throw new IllegalArgumentException("Invalid exact population fraction");
        }
        if (numerator.signum() == 0) {
            return 0.0;
        }
        if (numerator.equals(denominator)) {
            return 1.0;
        }
        return new BigDecimal(numerator).divide(new BigDecimal(denominator), RATIO_CONTEXT).doubleValue();
    }

    private static String populationWitness(List<Row> rows) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(WITNESS_PREFIX);
            ByteBuffer buffer = ByteBuffer.allocate(16);
            buffer.putLong(rows.size());
            digest.update(buffer.array(), 0, Long.BYTES);

            // A population is a weighted multiset; row order does not change its witness.
            ArrayList<Row> canonical = new ArrayList<>(rows);
            canonical.sort(Comparator.comparingDouble(Row::deviation).thenComparingDouble(Row::weight));
            for (Row row : canonical) {
                buffer.clear();
                buffer.putLong(Double.doubleToRawLongBits(row.deviation()));
                buffer.putLong(Double.doubleToRawLongBits(row.weight()));
                digest.update(buffer.array());
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("SHA-256 is required by the Java runtime", impossible);
        }
    }

    private static String hex(byte[] bytes) {
        char[] alphabet = "0123456789abcdef".toCharArray();
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            out[2 * i] = alphabet[value >>> 4];
            out[2 * i + 1] = alphabet[value & 0x0f];
        }
        return new String(out);
    }
}
