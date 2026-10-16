package dev.woflo.vixen;

import java.util.Arrays;

/** Standalone containment and refusal probes for native interval enclosures. */
public final class NativeEnclosureRun {
    private NativeEnclosureRun() {}
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static Config config(String seed) {
        double[] latent=new double[16], velocity=new double[16];
        Arrays.fill(velocity,.03);
        return new Config(Seed.of(seed),100,.35,Config.Floor.BASELINE,1.5,.25,.4,1,16,.8,10,latent,velocity);
    }
    private static void contains(Curve curve,double a,double b) {
        double[] box=NativeEnclosure.forCurve(curve).encloseDeviation(a,b);
        for(int i=0;i<=32;i++) {
            double x=a+(b-a)*i/32;
            double value=curve.sample(x).deviation();
            check(box[0]<=value&&value<=box[1],"sample outside interval at "+x+": "+Arrays.toString(box));
        }
    }
    public static void main(String[] args) {
        for(boolean coherent:new boolean[] {false,true}) {
            Curve root=coherent?Vixen.createCoherent(config("native-"+coherent)):Vixen.create(config("native-"+coherent));
            Curve forked=root.fork(120,"first",45.0).fork(130,"overlap",30.0);
            for(double[] interval:new double[][] {{90,110},{119,121},{125,150},{145,166},{190,191}})
                contains(forked,interval[0],interval[1]);
            double[] h=new double[16];Arrays.fill(h,.2);
            double[] explicit=NativeEnclosure.forCurve(forked,h).encloseDeviation(125,150);
            for(int i=0;i<=24;i++) {
                double x=125+25*i/24, value=forked.at(x,h).deviation();
                check(explicit[0]<=value&&value<=explicit[1],"explicit coordinate enclosure");
            }
        }
        Curve root=Vixen.createCoherent(config("checkpoint"));
        Curve open=root.fork(120,"open",60.0);
        Curve checkpoint=Vixen.restoreCurve(open.tailJson(140));
        check(checkpoint.availableFrom()==140,"checkpoint source preserved");
        contains(checkpoint,140,170);
        try { NativeEnclosure.forCurve(checkpoint).encloseDeviation(139,150); throw new AssertionError("checkpoint prefix accepted"); }
        catch(IllegalArgumentException expected) { }

        Curve transformed=Vixen.create(config("transform")).impulse(120,.5,80,10);
        check(Crossing.nextCrossing(transformed,.2,110,150,Crossing.Options.DEFAULT).status()==Crossing.Status.UNRESOLVED,
                "transformed curve refused without ancestor bounds");
        try { NativeEnclosure.forCurve(transformed); throw new AssertionError("operation oracle accepted"); }
        catch(UnsupportedOperationException expected) { }

        Guide guide=new Guide.Polynomial(new double[] {0},0,1,1,50000);
        Curve influenced=Vixen.create(config("influence")).influence(Influence.of(120,.5,10,guide));
        try { NativeEnclosure.forCurve(influenced); throw new AssertionError("native influence oracle accepted"); }
        catch(UnsupportedOperationException expected) { }

        Curve constant=Vixen.create(new Config(Seed.of("flat"),100,0,Config.Floor.NONE,null,.1,0,0,16,.8,0,new double[16],new double[16]));
        check(Crossing.nextCrossing(constant,.2,100,1000,Crossing.Options.DEFAULT).status()==Crossing.Status.ABSENT,
                "native enclosure proves no crossing");
        System.out.println("NativeEnclosureRun passed");
    }
}
