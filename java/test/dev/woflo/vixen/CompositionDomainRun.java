package dev.woflo.vixen;

import java.util.List;

public final class CompositionDomainRun {
    public static void main(String[] args){
        Guide guide=new Guide.Polynomial(new double[]{.1},0,1,1,200);
        Influence influence=new Influence(130,.5,10,guide,Influence.Reference.BASELINE,Double.POSITIVE_INFINITY,0);
        Curve c=Vixen.createCoherent().impulse(120,.1,30,1).influence(influence);
        if(c.domainEnd()!=200)throw new AssertionError("composed permanent guide did not retain its effective domain");
        Curve bounded=c.fork(195,"bounded/default",null);if(!Double.isFinite(bounded.sample(200).value()))throw new AssertionError("default fork did not fit effective domain");
        reject(()->c.fork(195,"bounded/explicit",10.0));
        Curve late=c.impulse(170,.01,10,1);reject(()->late.fork(169,"past",2.0));
        Curve checkpoint=Vixen.restoreCurve(late.tailJson(175));reject(()->checkpoint.fork(174,"forgotten",2.0));
        Curve coupled=Vixen.couple(List.of(late,late),.4,180,190).curves().get(0);reject(()->coupled.fork(179,"before-coupling",2.0));
        try{coupled.fork(190,"projection",5.0);throw new AssertionError("coupled projection branch contract");}catch(UnsupportedOperationException expected){}
        System.out.println("Java composition domains, default handoff bounds and immutable chronology passed");
    }
    private static void reject(Runnable fn){try{fn.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("invalid chronology/domain accepted");}
}
