package dev.woflo.vixen;
public final class PublicAnalyticRun {
 public static void main(String[] args){
  Guide guide=new Guide.Polynomial(new double[]{.03,.001},150,1,1,50000);
  Curve curve=Vixen.createCoherent().impulse(120,.1,40).influence(new Influence(130,.7,8,guide,Influence.Reference.BASELINE,180,10)).fork(160,"analytic",24.0);
  Operations.Jet jet=curve.jet(150);
  if(jet.value()!=curve.sample(150).value()||jet.first()!=curve.slope(150)||jet.second()!=curve.curvature(150))throw new AssertionError("Public analytic adapters disagree");
  Curve replay=Vixen.restoreCurve(curve.snapshotJson());
  if(!jet.equals(replay.jet(150)))throw new AssertionError("Analytic replay changed");
  Curve checkpoint=Vixen.restoreCurve(curve.tailJson(145));
  if(!jet.equals(checkpoint.jet(150)))throw new AssertionError("Active analytic checkpoint changed");
  Operations.Group group=Vixen.couple(java.util.List.of(curve,Vixen.createCoherent()),.4,180,240);
  if(!Double.isFinite(group.curves().get(0).slope(190)))throw new AssertionError("Coupling slope unavailable");
  Curve deep=Vixen.createCoherent();for(int i=0;i<6000;i++)deep=deep.impulse(120,.000001,20);
  if(!Double.isFinite(deep.slope(125)))throw new AssertionError("Deep analytic graph failed");
  System.out.println("Public native, guided, operator, fork, replay, checkpoint and shared/deep analytic paths passed");
 }
}
