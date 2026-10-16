package dev.woflo.vixen;

import java.util.Map;

public final class CoherentRun {
    public static void main(String[] args) {
        Config config=new Config(Seed.of(1L),100,0,Config.Floor.BASELINE,null,.1,0,1,16,.8,10,new double[16],new double[16]);
        Curve parent=Vixen.createCoherent(config).fork(120,"branch",80.0);
        for(double x:new double[]{120,125,140,150,160,180,200}){
            Sample p=parent.sample(x);Policy.Limits limits=new Policy.Limits(p.floorValue(),p.ceilingValue(),p.boundaryWidth());
            if(p.value()!=Policy.apply(p.unconstrainedValue(),limits)||p.guideValue()!=Policy.apply(p.unconstrainedGuideValue(),limits))throw new AssertionError("incoherent native constraint state");
        }
        Curve replay=Vixen.restoreCurve(parent.snapshotJson());
        if(!parent.snapshotJson().contains(Timeline.COHERENT_ALGORITHM))throw new AssertionError("native math version lost");
        for(double x:new double[]{1,100,120,150,200,220})if(!parent.sample(x).equals(replay.sample(x)))throw new AssertionError("coherent snapshot replay");
        for(double from:new double[]{150,220}){
            String text=parent.tailJson(from);if(!text.contains(Tail.COHERENT_ALGORITHM))throw new AssertionError("coherent tail version lost");
            Curve tail=Vixen.restoreCurve(text);
            Timeline low=VixenJson.tail(text).restore(),copy=Timeline.restore(low.snapshot());
            if(copy.from()!=from)throw new AssertionError("low-level snapshot lost checkpoint domain");
            Timeline textCopy=VixenJson.restore(VixenJson.snapshotText(low.snapshot()));
            if(textCopy.from()!=from||!textCopy.sample(from+10).equals(low.sample(from+10)))throw new AssertionError("low-level checkpoint text lost retained state");
            for(double x:new double[]{from,from+10,300})if(!low.sample(x).equals(copy.sample(x)))throw new AssertionError("low-level snapshot lost checkpoint seed");
            for(double x:new double[]{from,from+10,300})if(!parent.sample(x).equals(tail.sample(x)))throw new AssertionError("coherent tail replay");
            if(!tail.snapshotJson().contains(Tail.COHERENT_ALGORITHM))throw new AssertionError("checkpoint snapshot lost retained seed");
        }
        Guide guide=new Guide.Polynomial(new double[]{.3},150,1,1,50000);
        Curve affected=parent.influence(Influence.additive(150,.4,10,180,10,guide));
        for(double x:new double[]{150,190}){
            Sample a=affected.sample(x-1e-7),b=affected.sample(x+1e-7);
            if(Math.abs(b.value()-a.value())/Math.max(1,Math.abs(parent.sample(x).value()))>=1e-6)throw new AssertionError("coherent influence seam");
        }
        System.out.println("Java coherent native state, snapshot, active/folded checkpoints and influence seams passed");
    }
}
