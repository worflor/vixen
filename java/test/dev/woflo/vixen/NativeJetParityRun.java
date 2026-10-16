package dev.woflo.vixen;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
public final class NativeJetParityRun {
 private static void close(Object e,Double a,String label){if(e==null){if(a!=null)throw new AssertionError(label+" availability differs");return;}double n=((Number)e).doubleValue();if(a==null||!Double.isFinite(a)||Math.abs(n-a)>2e-10*Math.max(1,Math.abs(n)))throw new AssertionError(label+": expected "+n+" got "+a);}
 @SuppressWarnings("unchecked")public static void main(String[] args)throws Exception{
  Map<String,Object> fixture=(Map<String,Object>)VixenJson.parse(Files.readString(Path.of("tests/fixtures/v5/native-jets.json")));
  List<Object> cases=(List<Object>)fixture.get("cases");int index=0;
  for(Object o:cases){Map<String,Object> c=(Map<String,Object>)o;Config config=VixenJson.config(c.get("config"));Leaf leaf=Leaf.create(config);double x=((Number)c.get("x")).doubleValue();Analytic.Result actual;
   if(c.get("coordinates") instanceof List<?> h){double[] v=new double[h.size()];for(int i=0;i<v.length;i++)v[i]=((Number)h.get(i)).doubleValue();actual=Analytic.leaf(leaf,x,v);}else actual=Analytic.leaf(leaf,x);
   Map<String,Object> expected=(Map<String,Object>)c.get("expected"),fields=(Map<String,Object>)expected.get("fields");
   close(expected.get("slope"),actual.slope(),index+" slope");close(expected.get("curvature"),actual.curvature(),index+" curvature");
   for(var field:fields.entrySet()){Map<String,Object> e=(Map<String,Object>)field.getValue();Analytic.FieldJet a=actual.fields().get(field.getKey());if(a==null)throw new AssertionError("Missing field "+field.getKey());close(e.get("value"),a.value(),index+" "+field.getKey()+" value");close(e.get("d1"),a.d1(),index+" "+field.getKey()+" d1");close(e.get("d2"),a.d2(),index+" "+field.getKey()+" d2");}
   index++;
  }
  System.out.println("Java native analytic jets: "+cases.size()+" frozen JS cases with moving/explicit 16D, constraints and opening contacts passed");
 }
}
