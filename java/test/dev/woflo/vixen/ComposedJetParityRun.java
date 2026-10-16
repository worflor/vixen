package dev.woflo.vixen;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
public final class ComposedJetParityRun {
 private static void close(Object expected,Double actual,String label){
  if(expected==null){if(actual!=null)throw new AssertionError(label+" should be unavailable");return;}
  double value=((Number)expected).doubleValue();
  if(actual==null||!Double.isFinite(actual)||Math.abs(actual-value)>2e-9*Math.max(1,Math.abs(value)))throw new AssertionError(label+": "+actual+" != "+value);
 }
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
  Map<String,Object> fixture=(Map<String,Object>)VixenJson.parse(Files.readString(Path.of("tests/fixtures/v5/composed-jets.json")));
  List<Object> cases=(List<Object>)fixture.get("cases");
  for(Object item:cases){
   Map<String,Object> row=(Map<String,Object>)item,expected=(Map<String,Object>)row.get("expected");
   Curve curve=Vixen.restoreCurve(Vixen.stringifyTree(row.get("snapshot")));double x=((Number)row.get("x")).doubleValue();
   double[] h=row.get("coordinates")==null?null:((List<Number>)row.get("coordinates")).stream().mapToDouble(Number::doubleValue).toArray();
   Analytic.Result actual=curve.analyticJet(x,h);
   close(expected.get("slope"),actual.slope(),row.get("id")+" slope @"+x);
   close(expected.get("curvature"),actual.curvature(),row.get("id")+" curvature @"+x);
   Map<String,Object> fields=(Map<String,Object>)expected.get("fields");
   for(var entry:fields.entrySet()){
    Map<String,Object> want=(Map<String,Object>)entry.getValue();Analytic.FieldJet got=actual.fields().get(entry.getKey());
    if(got==null)throw new AssertionError("Missing field "+entry.getKey());
    String label=row.get("id")+" @"+x+" "+entry.getKey();close(want.get("value"),got.value(),label+" value");close(want.get("d1"),got.d1(),label+" d1");close(want.get("d2"),got.d2(),label+" d2");
   }
  }
  System.out.println("Java composed jets agree with "+cases.size()+" frozen JavaScript graph/fixed-coordinate cases");
 }
}
