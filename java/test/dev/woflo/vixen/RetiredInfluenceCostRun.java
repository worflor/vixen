package dev.woflo.vixen;

public final class RetiredInfluenceCostRun {
    public static void main(String[] args){
        Guide guide=new Guide.Polynomial(new double[]{.03},0,1,1,50000);
        Timeline live=Timeline.createCoherent(Config.defaults()).influence(new Influence(110,.2,5,guide,Influence.Reference.BASELINE,Double.POSITIVE_INFINITY,0));
        Timeline deep=live;
        int windows=6000;
        for(int i=0;i<windows;i++)deep=deep.influence(Influence.additive(120,.1,5,180,10,guide));
        long before=deep.influenceReads();Sample actual=deep.sample(220);long reads=deep.influenceReads()-before;
        if(!actual.equals(live.sample(220)))throw new AssertionError("retired suffix changed the older live layer");
        int levels=Integer.SIZE-Integer.numberOfLeadingZeros(windows+1),bound=16+4*levels*levels;
        if(reads>bound)throw new AssertionError("retired suffix still scanned: "+reads+" reads, bound "+bound);
        Timeline plain=Timeline.createCoherent(Config.defaults()),retired=plain;
        for(int i=0;i<windows;i++)retired=retired.influence(Influence.additive(120,.1,5,180,10,guide));
        for(double x:new double[]{100,220}){long prior=retired.influenceReads();if(!retired.sample(x).equals(plain.sample(x)))throw new AssertionError("transparent prefix changed sample");if(retired.influenceReads()-prior!=1)throw new AssertionError("fully transparent history was not constant cost");}
        java.util.Random random=new java.util.Random(0x1a7e);java.util.List<Influence> recipes=new java.util.ArrayList<>();
        Timeline origin=Timeline.createCoherent(new Config(Seed.of(1L),100,0,Config.Floor.BASELINE,.5,.1,.2,1,16,.8,10,new double[16],new double[16])),mixed=origin;
        for(int i=0;i<500;i++){
            double at=120+i*.01;Influence r=new Influence(at,.1,1,guide,Influence.Reference.BASELINE,at+5+random.nextInt(100),1+random.nextInt(20),i%2==0?Influence.Mode.ADD:Influence.Mode.BLEND);
            recipes.add(r);mixed=mixed.influence(r);
        }
        for(double x=119;x<250;x+=.77){Sample expected=origin.sample(x);for(Influence r:recipes)expected=r.apply(expected,x,origin.rootConfig());if(!mixed.sample(x).equals(expected))throw new AssertionError("active effect order changed at "+x);}
        Influence later=new Influence(150,.2,5,guide,Influence.Reference.BASELINE,Double.POSITIVE_INFINITY,0),earlier=new Influence(120,.7,5,guide,Influence.Reference.BASELINE,Double.POSITIVE_INFINITY,0);
        Timeline replayed=Timeline.restore(new Timeline.Snapshot(Config.defaults(),java.util.List.of(),java.util.List.of(later,earlier),true));
        Sample ordered=earlier.apply(later.apply(plain.sample(130),130,plain.rootConfig()),130,plain.rootConfig());
        if(!replayed.sample(130).equals(ordered))throw new AssertionError("replay incorrectly assumed influence start times were sorted");
        try{replayed.fork(140,"too-early",5.0);throw new AssertionError("replay lost the maximum authored coordinate");}catch(IllegalArgumentException expected){}
        System.out.println("Older live influence under "+windows+" retired windows: "+reads+" traversal reads; random constrained effect order preserved");
    }
}
