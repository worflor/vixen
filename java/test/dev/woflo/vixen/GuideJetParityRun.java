package dev.woflo.vixen;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
public final class GuideJetParityRun {
 private static void close(Object expected,Double actual){
  if(expected==null){if(actual!=null)throw new AssertionError("Unexpected available derivative");return;}
  double e=((Number)expected).doubleValue();
  if(actual==null||!Double.isFinite(actual)||Math.abs(e-actual)>2e-12*Math.max(1,Math.abs(e)))throw new AssertionError("Expected "+e+" got "+actual);
 }
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
  Map<String,Object> fixture=(Map<String,Object>)VixenJson.parse(Files.readString(Path.of("tests/fixtures/v5/guide-jets.json")));
  List<Object> cases=(List<Object>)fixture.get("cases");
  for(Object raw:cases){
   Map<String,Object> c=(Map<String,Object>)raw,expected=(Map<String,Object>)c.get("expected");
   Guide guide=VixenJson.guide(c.get("guide"));double x=((Number)c.get("x")).doubleValue();Guide.Jet jet=guide.jet(x);
   close(expected.get("value"),jet.value());close(expected.get("d1"),jet.first());close(expected.get("d2"),jet.second());
   if(Double.doubleToRawLongBits(jet.value())!=Double.doubleToRawLongBits(guide.evaluate(x)))throw new AssertionError("Changed primal value");
  }
  System.out.println("Java guide jets: "+cases.size()+" frozen JavaScript polynomial/quintic cases passed");
 }
}
