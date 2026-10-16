package dev.woflo.vixen;

import java.util.List;

/** Executable regression checks for analytic guide jets. */
public final class GuideJetRun {
    private GuideJetRun() {
    }

    public static void main(String[] args) {
        polynomialJetUsesCoordinateScale();
        sampledJetMatchesQuinticHermite();
        knotJetPreservesAuthoredEndpointDerivatives();
    }

    private static void polynomialJetUsesCoordinateScale() {
        Guide guide = new Guide.Polynomial(new double[] {3, -2, 4}, 0, 2, -2, 2);
        Guide.Jet jet = guide.jet(1);
        close(jet.value(), 3, 0);
        close(jet.first(), 1, 0);
        close(jet.second(), 2, 0);
        if (!jet.slopeAvailable() || !jet.curvatureAvailable()) {
            throw new AssertionError("Finite polynomial derivatives must be available");
        }
    }

    private static void sampledJetMatchesQuinticHermite() {
        Guide guide = new Guide.Sampled(List.of(
                new Guide.Knot(0, 1, 0, 0),
                new Guide.Knot(2, 5, 0, 0)));
        Guide.Jet jet = guide.jet(1);
        close(jet.value(), 3, 0);
        close(jet.first(), 3.75, 1e-14);
        close(jet.second(), 0, 1e-14);
        if (Double.doubleToRawLongBits(jet.value())
                != Double.doubleToRawLongBits(guide.evaluate(1))) {
            throw new AssertionError("Jet must preserve the scalar guide value exactly");
        }
    }

    private static void knotJetPreservesAuthoredEndpointDerivatives() {
        Guide guide = new Guide.Sampled(List.of(
                new Guide.Knot(0, 2, 3, 4),
                new Guide.Knot(2, 7, -5, 6)));
        Guide.Jet left = guide.jet(0);
        Guide.Jet right = guide.jet(2);
        close(left.value(), 2, 0);
        close(left.first(), 3, 0);
        close(left.second(), 4, 0);
        close(right.value(), 7, 0);
        close(right.first(), -5, 0);
        close(right.second(), 6, 0);
    }

    private static void close(Double actual, double expected, double tolerance) {
        if (actual == null || Math.abs(actual - expected) > tolerance) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }
}
