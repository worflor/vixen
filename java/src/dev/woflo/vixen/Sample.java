package dev.woflo.vixen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One evaluation of a curve: every field vixen.mjs returns from {@code sample}, under its own name. Immutable.
 * {@code floorValue} and {@code ceilingValue} are null when the leaf has none.
 */
public record Sample(double x, double value, double base, double score, double guideScore, double rawDeviation, double guideDeviation,
                     double guideValue, double stabilizationWeight, double deviation, double multiplier, double activity,
                     double positiveMultiplier, double positiveValue, double gate, double unconstrainedValue, double unconstrainedDeviation,
                     double unconstrainedGuideValue, double unconstrainedGuideDeviation, Double floorValue, Double ceilingValue,
                     double boundaryWidth, boolean constraintApplied) {

    /** The field names in vixen.mjs's order, for fixtures and the Fate Log's tooling. */
    public static final List<String> FIELDS = List.of("x", "value", "base", "score", "guideScore", "rawDeviation",
            "guideDeviation", "guideValue", "stabilizationWeight", "deviation", "multiplier", "activity", "positiveMultiplier", "positiveValue",
            "gate", "unconstrainedValue", "unconstrainedDeviation", "unconstrainedGuideValue", "unconstrainedGuideDeviation", "floorValue",
            "ceilingValue", "boundaryWidth", "constraintApplied");

    /** Every field as an object, keyed by its Vixen name. */
    public Map<String, Object> asMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("x", x);
        m.put("value", value);
        m.put("base", base);
        m.put("score", score);
        m.put("guideScore", guideScore);
        m.put("rawDeviation", rawDeviation);
        m.put("guideDeviation", guideDeviation);
        m.put("guideValue", guideValue);
        m.put("stabilizationWeight", stabilizationWeight);
        m.put("deviation", deviation);
        m.put("multiplier", multiplier);
        m.put("activity", activity);
        m.put("positiveMultiplier", positiveMultiplier);
        m.put("positiveValue", positiveValue);
        m.put("gate", gate);
        m.put("unconstrainedValue", unconstrainedValue);
        m.put("unconstrainedDeviation", unconstrainedDeviation);
        m.put("unconstrainedGuideValue", unconstrainedGuideValue);
        m.put("unconstrainedGuideDeviation", unconstrainedGuideDeviation);
        m.put("floorValue", floorValue);
        m.put("ceilingValue", ceilingValue);
        m.put("boundaryWidth", boundaryWidth);
        m.put("constraintApplied", constraintApplied);
        return m;
    }

    /**
     * {@code mixSamples}: the handoff's blend of a parent's sample {@code a} and a future's {@code b} at weight {@code w}. The actual
     * constrained values and their references are blended and every adapter derived afresh.
     */
    static Sample mix(Sample a, Sample b, double w) { return mix(a,b,w,false); }

    static Sample mix(Sample a, Sample b, double w, boolean coherent) {
        double x = a.x + (b.x - a.x) * w;
        double value = a.value + (b.value - a.value) * w;
        double base = a.base + (b.base - a.base) * w;
        double score = a.score + (b.score - a.score) * w;
        double guideScore = a.guideScore + (b.guideScore - a.guideScore) * w;
        double rawDeviation = a.rawDeviation + (b.rawDeviation - a.rawDeviation) * w;
        double guideValue = a.guideValue + (b.guideValue - a.guideValue) * w;
        double stabilizationWeight = a.stabilizationWeight + (b.stabilizationWeight - a.stabilizationWeight) * w;
        double gate = a.gate + (b.gate - a.gate) * w;
        double unconstrainedValue = a.unconstrainedValue + (b.unconstrainedValue - a.unconstrainedValue) * w;
        double unconstrainedGuideValue = a.unconstrainedGuideValue + (b.unconstrainedGuideValue - a.unconstrainedGuideValue) * w;
        double boundaryWidth = a.boundaryWidth + (b.boundaryWidth - a.boundaryWidth) * w;
        Double floorValue = a.floorValue;
        Double ceilingValue = a.ceilingValue;
        if (floorValue != null) {
            floorValue = a.floorValue + (b.floorValue - a.floorValue) * w;
        }
        if (ceilingValue != null) {
            ceilingValue = a.ceilingValue + (b.ceilingValue - a.ceilingValue) * w;
        }
        if (coherent) {
            Policy.Limits limits=new Policy.Limits(floorValue,ceilingValue,boundaryWidth);
            value=Policy.apply(unconstrainedValue,limits);
            guideValue=Policy.apply(unconstrainedGuideValue,limits);
        } else {
            if (floorValue != null) value=Math.max(value,floorValue);
            if (ceilingValue != null) value=Math.min(value,ceilingValue);
        }
        double deviation = (value - base) / base;
        double multiplier = 1 + deviation;
        double activity = .5 + .5 * StrictMath.tanh(deviation);
        double positiveMultiplier = StrictMath.exp(.55 * StrictMath.tanh(deviation));
        double positiveValue = base * positiveMultiplier;
        double guideDeviation = (guideValue - base) / base;
        double unconstrainedDeviation = (unconstrainedValue - base) / base;
        double unconstrainedGuideDeviation = (unconstrainedGuideValue - base) / base;
        boolean constraintApplied = value != unconstrainedValue;
        return new Sample(x, value, base, score, guideScore, rawDeviation, guideDeviation, guideValue, stabilizationWeight, deviation, multiplier,
                activity, positiveMultiplier, positiveValue, gate, unconstrainedValue, unconstrainedDeviation, unconstrainedGuideValue,
                unconstrainedGuideDeviation, floorValue, ceilingValue, boundaryWidth, constraintApplied);
    }
}
