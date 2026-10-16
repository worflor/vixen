package dev.woflo.vixen;

import java.util.Arrays;
import java.util.List;

/**
 * An authored scalar guide an influence pulls toward (vixen-guide.mjs, {@code vixen-guide/1}): a polynomial of {@code (x - center) / scale},
 * or a sampled quintic Hermite through knots that share value, slope and curvature. Callback capture is not ported: Cartomancy builds every
 * guide from its own numbers. Evaluation outside the domain is refused, never extrapolated.
 */
public sealed interface Guide permits Guide.Polynomial, Guide.Sampled {
    String FORMAT = "vixen-guide/1";

    /** The domain's two endpoints. */
    double lo();

    double hi();

    /** The guide's value at {@code x}, which must lie in the domain. */
    double evaluate(double x);

    /** The guide's value and coordinate derivatives at one point. Unavailable orders are null independently. */
    Jet jet(double x);

    /** A knot's jet: value, first and second derivative. */
    record Knot(double x, double y, double d1, double d2) {
    }

    /**
     * An analytic scalar jet. A null derivative has an order-specific reason; a finite slope remains available when curvature overflows.
     */
    record Jet(double value, Double first, Double second, String firstUnavailableReason, String secondUnavailableReason) {
        public Jet {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Guide jet value must be finite");
            }
            if ((first == null) != (firstUnavailableReason != null)
                    || (second == null) != (secondUnavailableReason != null)) {
                throw new IllegalArgumentException("Unavailable derivatives require order-specific reasons");
            }
            if (first != null && !Double.isFinite(first) || second != null && !Double.isFinite(second)) {
                throw new IllegalArgumentException("Available derivatives must be finite");
            }
        }

        public boolean slopeAvailable() {
            return first != null;
        }

        public boolean curvatureAvailable() {
            return second != null;
        }
    }

    /** {@code polynomialGuide}: ascending coefficients of powers of {@code (x - center) / scale}. */
    final class Polynomial implements Guide {
        private final double[] coefficients;
        private final double center;
        private final double scale;
        private final double lo;
        private final double hi;

        public Polynomial(double[] coefficients, double center, double scale, double lo, double hi) {
            if (coefficients == null || coefficients.length < 1 || coefficients.length > 65) {
                throw new IllegalArgumentException("Polynomial requires 1..65 coefficients");
            }
            for (double c : coefficients) {
                number(c, -1e250, 1e250);
            }
            number(center, -1e6, 1e6);
            number(scale, 1e-6, 1e6);
            number(lo, -1e6, 1e6);
            number(hi, -1e6, 1e6);
            if (lo >= hi) {
                throw new IllegalArgumentException("Guide domain must increase");
            }
            double qMax = Math.max(Math.abs((lo - center) / scale), Math.abs((hi - center) / scale));
            double bound = 0;
            for (int i = coefficients.length - 1; i >= 0; i--) {
                bound = bound * qMax + Math.abs(coefficients[i]);
            }
            number(bound, 0, 1e250);
            this.coefficients = coefficients.clone();
            this.center = center;
            this.scale = scale;
            this.lo = lo;
            this.hi = hi;
        }

        public double[] coefficients() {
            return coefficients.clone();
        }

        public double center() {
            return center;
        }

        public double scale() {
            return scale;
        }

        @Override
        public double lo() {
            return lo;
        }

        @Override
        public double hi() {
            return hi;
        }

        @Override
        public double evaluate(double x) {
            number(x, lo, hi);
            double z = (x - center) / scale;
            double y = 0;
            for (int i = coefficients.length - 1; i >= 0; i--) {
                y = y * z + coefficients[i];
            }
            return number(y, -1e250, 1e250);
        }

        @Override
        public Jet jet(double x) {
            double value = evaluate(x);
            double z = (x - center) / scale;
            double p = coefficients[coefficients.length - 1];
            double d = 0;
            double dd = 0;
            for (int i = coefficients.length - 2; i >= 0; i--) {
                dd = dd * z + 2 * d;
                d = d * z + p;
                p = p * z + coefficients[i];
            }
            return makeJet(value, d / scale, dd / scale / scale);
        }
    }

    /** {@code sampledGuide}: ordered knots with a quintic Hermite segment between each pair. */
    final class Sampled implements Guide {
        private final Knot[] knots;
        private final double[][] segments;

        public Sampled(List<Knot> knotList) {
            if (knotList == null || knotList.size() < 2 || knotList.size() > 2049) {
                throw new IllegalArgumentException("Sampled guide requires 2..2049 jets");
            }
            Knot[] k = knotList.toArray(new Knot[0]);
            double previous = Double.NEGATIVE_INFINITY;
            for (Knot n : k) {
                number(n.x, -1e6, 1e6);
                if (n.x <= previous) {
                    throw new IllegalArgumentException("Knots must strictly increase");
                }
                previous = n.x;
                number(n.y, -1e250, 1e250);
                number(n.d1, -1e250, 1e250);
                number(n.d2, -1e250, 1e250);
            }
            double[][] segs = new double[k.length - 1][];
            for (int i = 0; i < k.length - 1; i++) {
                Knot a = k[i];
                Knot b = k[i + 1];
                double h = b.x - a.x;
                double A = a.y;
                double B = h * a.d1;
                double C = h * h * a.d2 / 2;
                double U = b.y - (A + B + C);
                double V = h * b.d1 - (B + 2 * C);
                double W = h * h * b.d2 - 2 * C;
                double[] c = {A, B, C, 10 * U - 4 * V + W / 2, -15 * U + 7 * V - W, 6 * U - 3 * V + W / 2};
                double sum = 0;
                for (double v : c) {
                    number(v, -1e280, 1e280);
                    sum = sum + Math.abs(v);
                }
                number(sum, 0, 1e250);
                segs[i] = c;
            }
            this.knots = k;
            this.segments = segs;
        }

        public List<Knot> knots() {
            return List.of(knots);
        }

        @Override
        public double lo() {
            return knots[0].x;
        }

        @Override
        public double hi() {
            return knots[knots.length - 1].x;
        }

        @Override
        public double evaluate(double x) {
            number(x, lo(), hi());
            int index = segmentIndex(x);
            if (x == knots[index].x) {
                return knots[index].y;
            }
            double t = (x - knots[index].x) / (knots[index + 1].x - knots[index].x);
            double[] c = segments[index];
            double y = 0;
            for (int j = 5; j >= 0; j--) {
                y = y * t + c[j];
            }
            return number(y, -1e250, 1e250);
        }

        @Override
        public Jet jet(double x) {
            number(x, lo(), hi());
            int index = segmentIndex(x);
            Knot knot = knots[index];
            if (x == knot.x) {
                return makeJet(knot.y, knot.d1, knot.d2);
            }
            double h = knots[index + 1].x - knot.x;
            double t = (x - knot.x) / h;
            double[] c = segments[index];
            double p = c[5];
            double d = 0;
            double dd = 0;
            for (int i = 4; i >= 0; i--) {
                dd = dd * t + 2 * d;
                d = d * t + p;
                p = p * t + c[i];
            }
            // Keep the primal value on the same scalar path as evaluate().
            return makeJet(evaluate(x), d / h, dd / h / h);
        }

        private int segmentIndex(double x) {
            int low = 0;
            int high = knots.length - 1;
            while (low < high) {
                int middle = (low + high + 1) / 2;
                if (knots[middle].x <= x) {
                    low = middle;
                } else {
                    high = middle - 1;
                }
            }
            return low;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Sampled s && Arrays.equals(knots, s.knots);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(knots);
        }
    }

    private static Jet makeJet(double value, double first, double second) {
        Double slope = Double.isFinite(first) ? first : null;
        Double curvature = Double.isFinite(second) ? second : null;
        return new Jet(value, slope, curvature,
                slope == null ? "The guide's first derivative exceeds the finite binary64 range." : null,
                curvature == null ? "The guide's second derivative exceeds the finite binary64 range." : null);
    }

    /** Vixen's {@code guideNumber}: a finite number inside bounds. */
    static double number(double v, double lo, double hi) {
        if (Double.isNaN(v) || Double.isInfinite(v) || v < lo || v > hi) {
            throw new IllegalArgumentException("Guide number outside finite bounds");
        }
        return v;
    }
}
