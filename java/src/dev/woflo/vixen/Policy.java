package dev.woflo.vixen;

/** The output policies: the floor and ceiling maps with their soft boundary (vixen.mjs {@code floorMap}, {@code ceilMap}, {@code limits}, {@code apply}). */
final class Policy {
    /** A leaf's limits at one coordinate; {@code floorValue} and {@code ceilingValue} are null when absent. */
    record Limits(Double floorValue, Double ceilingValue, double boundaryWidth) {
    }

    private Policy() {
    }

    static double floorMap(double y, double lo, double e) {
        if (y <= lo) {
            return lo;
        }
        if (e == 0 || y >= lo + e) {
            return y;
        }
        double t = (y - lo) / e;
        return lo + e * Opening.boundaryRamp(t);
    }

    static double ceilMap(double y, double hi, double e) {
        if (y >= hi) {
            return hi;
        }
        if (e == 0 || y <= hi - e) {
            return y;
        }
        double t = (hi - y) / e;
        return hi - e * Opening.boundaryRamp(t);
    }

    static Limits limits(Config c, double base, double guide, double gate) {
        Double floorValue = c.floor() == Config.Floor.NONE ? null
                : c.floor() == Config.Floor.ZERO ? Double.valueOf(0) : c.floor() == Config.Floor.BASELINE ? Double.valueOf(base) : Double.valueOf(guide);
        Double ceilingValue = c.ceilingMargin() == null ? null
                : Double.valueOf((c.floor() == Config.Floor.BASELINE ? base : guide) + c.ceilingMargin() * base);
        double a = c.strength() * gate * c.boundarySoftness();
        Double gap = floorValue != null && ceilingValue != null ? Double.valueOf((ceilingValue - floorValue) / base) : null;
        double boundaryWidth;
        if (gap != null) {
            boundaryWidth = gap <= 0 || a == 0 ? 0 : base * (a * gap / (gap + 2 * a));
        } else {
            boundaryWidth = floorValue == null && ceilingValue == null ? 0 : base * a;
        }
        return new Limits(floorValue, ceilingValue, boundaryWidth);
    }

    static double apply(double y, Limits lim) {
        if (lim.floorValue() != null && lim.ceilingValue() != null && lim.ceilingValue() <= lim.floorValue()) {
            return lim.floorValue();
        }
        double lo = lim.floorValue() == null ? y : floorMap(y, lim.floorValue(), lim.boundaryWidth());
        return lim.ceilingValue() == null ? lo : ceilMap(lo, lim.ceilingValue(), lim.boundaryWidth());
    }
}
