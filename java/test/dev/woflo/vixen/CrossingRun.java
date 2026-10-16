package dev.woflo.vixen;

/** Standalone assertions for crossing search contracts; run with assertions enabled. */
public final class CrossingRun {
    private CrossingRun() {}
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        Crossing.Evaluator polynomial = x -> (x-.25)*(x-.75);
        Crossing.EnclosureProvider lipschitz = Crossing.makeLipschitzEnclosure(polynomial, 1, 0);
        Crossing.Options search = new Crossing.Options("value", true, 1e-5, 10000, 10000, 64);
        Crossing.Result hidden = Crossing.nextCrossing(polynomial, lipschitz, 0, 0, 1, search);
        check(hidden.status()==Crossing.Status.FOUND && hidden.interval().lo() <= .25 && hidden.interval().hi() >= .25, "hidden interior crossing: "+hidden);

        Crossing.Evaluator constant = x -> 2;
        Crossing.Result absent = Crossing.nextCrossing(constant, Crossing.makeLipschitzEnclosure(constant,0,0), 0, 0, 1, search);
        check(absent.status()==Crossing.Status.ABSENT && absent.certifiedAbsent()!=null, "enclosed absence");

        Crossing.Evaluator oscillating = x -> Math.sin(2*Math.PI*x);
        Crossing.EnclosureProvider broad = new Crossing.EnclosureProvider() {
            public Object source() { return oscillating; }
            public double[] enclose(double lo,double hi,String measure) { return new double[] {-1,1}; }
        };
        Crossing.Result budget = Crossing.nextCrossing(oscillating,broad,.2,0,1,
                new Crossing.Options("value",true,1e-8,1,100,64));
        check(budget.status()==Crossing.Status.UNRESOLVED && "budget".equals(budget.reason()), "budget unresolved");

        Crossing.EnclosureProvider invalid = new Crossing.EnclosureProvider() {
            public Object source() { return constant; }
            public double[] enclose(double lo,double hi,String measure) { return new double[] {Double.NaN,1}; }
        };
        check("invalid-enclosure".equals(Crossing.nextCrossing(constant,invalid,0,0,1,search).reason()), "invalid oracle refused");
        Crossing.EnclosureProvider validBroad = new Crossing.EnclosureProvider() {
            public Object source() { return constant; }
            public double[] enclose(double lo,double hi,String measure) { return new double[] {-3,3}; }
        };
        Crossing.Evaluator bad = x -> Double.NaN;
        Crossing.EnclosureProvider badBound = new Crossing.EnclosureProvider() {
            public Object source() { return bad; }
            public double[] enclose(double lo,double hi,String measure) { return new double[] {-1,1}; }
        };
        check("invalid-evaluator".equals(Crossing.nextCrossing(bad,badBound,0,0,1,search).reason()), "invalid evaluator refused");
        check("source-mismatch".equals(Crossing.nextCrossing(constant,validBroad,0,0,1,search).reason()) == false,
                "curve/evaluator entry points remain distinct");
        System.out.println("CrossingRun passed");
    }
}
