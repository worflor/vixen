package dev.woflo.vixen;

import java.util.List;

public final class DeepEvaluationRun {
    public static void main(String[] args){
        Curve base=Vixen.createCoherent().impulse(120,.1,2000,1),c=base;
        Guide g=new Guide.Polynomial(new double[]{.001},0,1,1,50000);
        Influence influence=Influence.additive(120,.01,10,180,10,g);
        for(int i=0;i<6000;i++)c=c.influence(influence);
        if(!c.sample(119).equals(base.sample(119)))throw new AssertionError("deep inactive prefix changed");
        if(!Double.isFinite(c.sample(150).value()))throw new AssertionError("deep active sample");
        if(!c.sample(220).equals(base.sample(220)))throw new AssertionError("deep released suffix changed");
        Curve replay=Vixen.restoreCurve(c.snapshotJson());
        Curve fork=c.fork(160,"deep/future",20.0);if(!fork.sample(150).equals(c.sample(150)))throw new AssertionError("deep fork changed prefix");
        if(!Vixen.restoreCurve(fork.snapshotJson()).sample(180).equals(fork.sample(180)))throw new AssertionError("deep fork replay");
        if(!replay.sample(150).equals(c.sample(150)))throw new AssertionError("deep composition replay");
        Curve a=Vixen.createCoherent(),b=Vixen.createCoherent();
        for(int i=0;i<600;i++){Operations.Group group=Operations.couple(List.of(a,b),.4,120,180);a=group.curves().get(0);b=group.curves().get(1);}
        if(!Double.isFinite(a.sample(150).value())||!Double.isFinite(b.sample(150).value()))throw new AssertionError("shared coupling evaluation");
        Curve sharedReplay=Vixen.restoreCurve(a.snapshotJson());if(!sharedReplay.sample(150).equals(a.sample(150)))throw new AssertionError("shared coupling replay");
        Curve projection=a;try{projection.fork(160,"ambiguous",20.0);throw new AssertionError("projection fork must refuse ambiguity");}catch(UnsupportedOperationException expected){}
        try{projection.impulse(160,.01,5,1).fork(170,"wrapped-ambiguity",5.0);throw new AssertionError("wrapping must not bypass projection fork refusal");}catch(UnsupportedOperationException expected){}
        for(double from:new double[]{150,220}){
            Curve tail=Vixen.restoreCurve(a.tailJson(from));if(!tail.sample(from).equals(a.sample(from)))throw new AssertionError("shared coupling checkpoint");
            Curve causalTail=Vixen.restoreCurve(c.tailJson(from));if(!causalTail.sample(from).equals(c.sample(from)))throw new AssertionError("deep operator checkpoint");
        }
        Curve nativeOnly=Vixen.createCoherent();for(int i=0;i<6000;i++)nativeOnly=nativeOnly.influence(influence);
        if(!Vixen.restoreCurve(nativeOnly.snapshotJson()).sample(150).equals(nativeOnly.sample(150)))throw new AssertionError("deep native influence replay");
        String retiredNative=nativeOnly.tailJson(220);if(retiredNative.length()>10000)throw new AssertionError("retired native windows were retained in checkpoint");
        if(!Vixen.restoreCurve(retiredNative).sample(220).equals(nativeOnly.sample(220)))throw new AssertionError("native window checkpoint");
        System.out.println("Java: 6000 mixed stages,6000 native influences,600 shared generations pass sampling/replay/checkpoints; unary forks pass and coupled-projection forks are explicitly refused");

    }
}
