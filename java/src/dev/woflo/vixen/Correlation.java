package dev.woflo.vixen;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** Exact binary64 correlation-matrix eligibility with a separately qualified numeric factor. */
public final class Correlation {
    private Correlation() {}

    public record Factorization(List<List<Double>> matrix, List<List<Double>> factor, double tolerance,
                                double factorError, String validation) {
        public Factorization {
            matrix = immutableMatrix(matrix);
            factor = immutableMatrix(factor);
        }
    }

    public static Factorization validateCorrelationMatrix(double[][] input) {
        return validateCorrelationMatrix(input, 1e-10);
    }

    /** Tolerance qualifies only the returned floating-point factor; PSD eligibility is exact. */
    public static Factorization validateCorrelationMatrix(double[][] input, double tolerance) {
        if (!Double.isFinite(tolerance) || tolerance <= 0) {
            throw new IllegalArgumentException("Factor tolerance must be finite and positive");
        }
        if (input == null || input.length == 0) {
            throw new IllegalArgumentException("Correlation matrix must be nonempty");
        }
        int n = input.length;
        double[][] rows = new double[n][n];
        for (int i = 0; i < n; i++) {
            if (input[i] == null || input[i].length != n) {
                throw new IllegalArgumentException("Correlation matrix must be square");
            }
            for (int j = 0; j < n; j++) {
                double value = input[i][j];
                if (!Double.isFinite(value) || Math.abs(value) > 1) {
                    throw new IllegalArgumentException("Correlation coefficients must be finite in [-1,1]");
                }
                rows[i][j] = value;
            }
        }
        for (int i = 0; i < n; i++) {
            if (rows[i][i] != 1.0) {
                throw new IllegalArgumentException("Correlation diagonal must be exactly one");
            }
            for (int j = 0; j < i; j++) {
                if (rows[i][j] != rows[j][i]) {
                    throw new IllegalArgumentException("Correlation matrix must be exactly symmetric");
                }
            }
        }

        Rational[][] original = new Rational[n][n];
        Rational[][] work = new Rational[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                original[i][j] = Rational.fromDouble(rows[i][j]);
                work[i][j] = original[i][j];
            }
        }
        double[][] factor = new double[n][n];
        for (int j = 0; j < n; j++) {
            Rational pivot = work[j][j];
            if (pivot.signum() < 0) {
                throw new IllegalArgumentException("Correlation matrix is not positive semidefinite");
            }
            if (pivot.signum() == 0) {
                for (int i = j + 1; i < n; i++) {
                    if (work[i][j].signum() != 0) {
                        throw new IllegalArgumentException("Nonzero column at a zero PSD pivot");
                    }
                }
                continue;
            }
            factor[j][j] = floorFloat(pivot, true, 1.0);
            for (int i = j + 1; i < n; i++) {
                Rational column = work[i][j];
                double magnitude = floorFloat(column.multiply(column).divide(pivot), true, 1.0);
                factor[i][j] = column.signum() < 0 ? -magnitude : magnitude;
            }
            for (int i = j + 1; i < n; i++) {
                for (int k = i; k < n; k++) {
                    work[k][i] = work[k][i].subtract(work[k][j].multiply(work[i][j]).divide(pivot));
                }
            }
        }

        Rational[][] numeric = new Rational[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) numeric[i][j] = Rational.fromDouble(factor[i][j]);
        }
        Rational worst = Rational.ZERO;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                Rational covariance = Rational.ZERO;
                for (int k = 0; k <= j; k++) {
                    covariance = covariance.add(numeric[i][k].multiply(numeric[j][k]));
                }
                Rational error = covariance.subtract(original[i][j]).abs();
                if (error.compareTo(worst) > 0) worst = error;
            }
        }
        if (worst.compareTo(Rational.fromDouble(tolerance)) > 0) {
            throw new IllegalArgumentException("Numerical factor cannot meet the requested tolerance");
        }
        double factorError = ceilFloat(worst);
        return new Factorization(toList(rows), toList(factor), tolerance, factorError, "exact-binary64-ldlt");
    }

    private static List<List<Double>> toList(double[][] matrix) {
        List<List<Double>> rows = new ArrayList<>(matrix.length);
        for (double[] row : matrix) {
            List<Double> values = new ArrayList<>(row.length);
            for (double value : row) values.add(value);
            rows.add(values);
        }
        return rows;
    }

    private static List<List<Double>> immutableMatrix(List<List<Double>> matrix) {
        if (matrix == null) throw new IllegalArgumentException("Matrix is required");
        List<List<Double>> rows = new ArrayList<>(matrix.size());
        for (List<Double> row : matrix) rows.add(List.copyOf(row));
        return List.copyOf(rows);
    }

    private static double floorFloat(Rational q, boolean squared, double bound) {
        Rational boundFraction = Rational.fromDouble(bound);
        Rational limit = squared ? boundFraction.multiply(boundFraction) : boundFraction;
        if (q.signum() < 0 || q.compareTo(limit) > 0) {
            throw new IllegalArgumentException("Factor conversion outside its proved range");
        }
        long lo = 0;
        long hi = Double.doubleToRawLongBits(bound);
        while (lo < hi) {
            long mid = (lo + hi + 1) >>> 1;
            Rational candidate = Rational.fromDouble(Double.longBitsToDouble(mid));
            if (squared) candidate = candidate.multiply(candidate);
            if (candidate.compareTo(q) <= 0) lo = mid;
            else hi = mid - 1;
        }
        return Double.longBitsToDouble(lo);
    }

    private static double ceilFloat(Rational q) {
        double floor = floorFloat(q, false, 2.0);
        return Rational.fromDouble(floor).compareTo(q) == 0
                ? floor
                : Double.longBitsToDouble(Double.doubleToRawLongBits(floor) + 1);
    }

    private record Rational(BigInteger numerator, BigInteger denominator) implements Comparable<Rational> {
        private static final Rational ZERO = new Rational(BigInteger.ZERO, BigInteger.ONE);

        private Rational {
            if (denominator.signum() == 0) throw new IllegalArgumentException("Zero rational denominator");
            if (denominator.signum() < 0) {
                numerator = numerator.negate();
                denominator = denominator.negate();
            }
            if (numerator.signum() == 0) {
                denominator = BigInteger.ONE;
            } else {
                BigInteger gcd = numerator.abs().gcd(denominator);
                numerator = numerator.divide(gcd);
                denominator = denominator.divide(gcd);
            }
        }

        static Rational fromDouble(double value) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Finite binary64 operand required");
            long bits = Double.doubleToRawLongBits(value);
            int encoded = (int) ((bits >>> 52) & 2047);
            long mantissa = (bits & 0xfffffffffffffL) | (encoded == 0 ? 0L : 1L << 52);
            BigInteger n = BigInteger.valueOf(mantissa);
            if (bits < 0) n = n.negate();
            int shift = encoded == 0 ? -1074 : encoded - 1075;
            return shift < 0
                    ? new Rational(n, BigInteger.ONE.shiftLeft(-shift))
                    : new Rational(n.shiftLeft(shift), BigInteger.ONE);
        }

        int signum() { return numerator.signum(); }
        Rational add(Rational other) {
            return new Rational(numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),
                    denominator.multiply(other.denominator));
        }
        Rational subtract(Rational other) {
            return new Rational(numerator.multiply(other.denominator).subtract(other.numerator.multiply(denominator)),
                    denominator.multiply(other.denominator));
        }
        Rational multiply(Rational other) {
            return new Rational(numerator.multiply(other.numerator), denominator.multiply(other.denominator));
        }
        Rational divide(Rational other) {
            return new Rational(numerator.multiply(other.denominator), denominator.multiply(other.numerator));
        }
        Rational abs() { return numerator.signum() < 0 ? new Rational(numerator.negate(), denominator) : this; }
        @Override public int compareTo(Rational other) {
            return numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator));
        }
    }
}
