package dev.woflo.vixen;

import java.util.Map;

/** A random-access Vixen curve, backed by a native timeline or an immutable adapter. */
public abstract class Curve implements Vixen.Restored {
    volatile EvaluationGraph.Node evaluationNode;
    double latestWriteTime(){return availableFrom();}
    public abstract Sample sample(double x);
    public abstract Sample at(double x, double[] latent);
    public abstract Config config();
    public abstract double domainStart();
    public abstract double domainEnd();
    public abstract double availableFrom();
    public abstract Curve fork(double at, String operation, Double handoffWidth);

    public final double[] encloseDeviation(double lo,double hi){return NativeEnclosure.forCurve(this).encloseDeviation(lo,hi);}
    public final Crossing.Result nextCrossing(double threshold,double from,double horizon,Crossing.Options options){return Crossing.nextCrossing(this,threshold,from,horizon,options);}

    public final double[] domain() { return new double[] {domainStart(), domainEnd()}; }
    public final Curve fork(double at, String operation) { return fork(at, operation, null); }

    /** Evenly spaced samples, including both endpoints. */
    public Sample[] sampleRange(double from, double until, int count) {
        if (count < 1) throw new IllegalArgumentException("A range holds at least one sample");
        Sample[] out = new Sample[count];
        for (int i = 0; i < count; i++) {
            double x = count == 1 ? from : i == count - 1 ? until : from + (until - from) * i / (count - 1);
            out[i] = sample(x);
        }
        return out;
    }

    public Curve impulse(double at, double magnitude, double relaxation) {
        return Operations.withImpulse(this, at, magnitude, relaxation, null);
    }
    public Curve impulse(double at, double magnitude, double relaxation, double attack) {
        return Operations.withImpulse(this, at, magnitude, relaxation, attack);
    }
    public Curve impulse(Map<String, ?> options) { return Operations.withImpulse(this, options); }

    public Curve redistribute(double from, double until, double[] coefficients) {
        return Operations.withRedistribution(this, from, until, coefficients, 1, "actualDeviation");
    }
    public Curve redistribute(double from, double until, double[] coefficients, double magnitude, String measure) {
        return Operations.withRedistribution(this, from, until, coefficients, magnitude, measure);
    }
    public Curve influence(Influence influence) { return Vixen.addInfluence(this, influence); }

    @Override public String tailJson() { return tailJson(availableFrom()); }

    public Analytic.Result analyticJet(double x){return analyticJet(x,null);}
    public Analytic.Result analyticJet(double x,double[] coordinates){return CurveEvaluation.jet(this,x,coordinates);}
    public double slope(double x){Analytic.Result j=analyticJet(x);if(j.slope()==null)throw new ArithmeticException(j.rationale());return j.slope();}
    public double curvature(double x){Analytic.Result j=analyticJet(x);if(j.curvature()==null)throw new ArithmeticException(j.rationale());return j.curvature();}
    public Operations.Jet jet(double x){return jet(x,null);}
    public Operations.Jet jet(double x,double[] coordinates){Analytic.Result j=analyticJet(x,coordinates);if(j.slope()==null||j.curvature()==null)throw new ArithmeticException(j.rationale());return new Operations.Jet(j.sample().value(),j.slope(),j.curvature());}
    public double derivative(double x){return slope(x);}
    public double secondDerivative(double x){return curvature(x);}
    /** Normalized-coordinate adapters are not available in this Java surface yet. */
    public Curve normalized() { throw unsupported("normalized"); }
    public Curve normalised() { throw unsupported("normalised"); }
    public Sample sampleNormalized(double x) { throw unsupported("sampleNormalized"); }
    public Sample atNormalized(double x, double[] latent) { throw unsupported("atNormalized"); }
    public Sample normalizedAt(double x) { throw unsupported("normalizedAt"); }

    private static UnsupportedOperationException unsupported(String name) {
        return new UnsupportedOperationException(name + " is unavailable until a Java derivative adapter is integrated");
    }
}