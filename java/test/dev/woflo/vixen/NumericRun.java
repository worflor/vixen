package dev.woflo.vixen;
import java.util.List;
import java.util.Map;

public final class NumericRun {
 public static void main(String[] args){
  if(Double.doubleToRawLongBits(Floating.mean(new double[]{Double.MAX_VALUE,-Double.MAX_VALUE,2*Double.MIN_VALUE}))!=Double.doubleToRawLongBits(Double.MIN_VALUE))throw new AssertionError("exact cancellation mean");
  if(Floating.sumProducts(new double[][]{{1e308,2},{-1e308}})!=1e308)throw new AssertionError("finite product sum");
  Guide large=new Guide.Polynomial(new double[]{1e100},0,1,1,50000);
  for(Influence.Mode mode:Influence.Mode.values())for(Influence.Reference ref:List.of(Influence.Reference.BASELINE,Influence.Reference.BACKBONE)){
   Curve c=Vixen.create().influence(new Influence(100,1,16,large,ref,Double.POSITIVE_INFINITY,0,mode));
   boolean rejected=false;try{c.sample(50000);}catch(IllegalArgumentException e){rejected=true;}
   if(!rejected)throw new AssertionError("raw overflow was not rejected");
  }
  Curve p=Vixen.create();Guide g=new Guide.Polynomial(new double[]{1e250},0,1,1,50000);
  Sample s=p.sample(50000),v=p.influence(new Influence(100,1e-250,16,g,Influence.Reference.BASELINE,Double.POSITIVE_INFINITY,0)).sample(50000);
  if(!Double.isFinite(v.value())||StrictMath.abs((v.unconstrainedValue()-s.unconstrainedValue())/s.base()-1)>1e-12)throw new AssertionError("weighted target overflow");
  Curve extreme=Vixen.create().impulse(1,Double.MAX_VALUE,.0002,.0002);
  Sample before=extreme.sample(1.0001);
  for(Sample sample:Vixen.couple(List.of(extreme,extreme,extreme),1,1,2).sample(1.0001))if(!sample.equals(before))throw new AssertionError("equal finite coupling");
  Operations.Jet zero=Operations.perturbationJet(Map.of("type","impulse","at",0.0,"magnitude",0.0,"attack",1e-200,"relaxation",1.0),5e-201);
  if(zero.value()!=0||zero.first()!=0||zero.second()!=0)throw new AssertionError("zero impulse NaN");
  System.out.println("Java guarded extremes and exact finite arithmetic passed");
 }
}
