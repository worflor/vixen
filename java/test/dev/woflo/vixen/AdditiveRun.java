package dev.woflo.vixen;

public final class AdditiveRun {
    public static void main(String[] args) {
        Curve parent=Vixen.create();
        Guide guide=new Guide.Polynomial(new double[]{.2},0,1,1,50000);
        Influence influence=Influence.additive(120,.6,10,170,10,guide);
        Curve curve=parent.influence(influence);
        Curve replay=Vixen.restoreCurve(curve.snapshotJson());
        for (double x:new double[]{120,125,130,150,175,180,300}) {
            Sample a=parent.sample(x),b=curve.sample(x);
            double w=influence.weight(x);
            double expected=a.unconstrainedValue()+a.base()*(w*.2);
            if(Math.abs(b.unconstrainedValue()-expected)>1e-12*Math.max(1,Math.abs(expected)))throw new AssertionError("additive mismatch");
            if(!b.equals(replay.sample(x)))throw new AssertionError("additive replay mismatch");
            if(w==0&&!b.equals(a))throw new AssertionError("nontransparent release");
        }
        Curve tail=Vixen.restoreCurve(curve.tailJson(150));
        if(!tail.sample(175).equals(curve.sample(175)))throw new AssertionError("tail lost mode");
        System.out.println("Java additive samples, release, snapshot and active tail passed");
    }
}
