package dev.woflo.vixen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class ComposedParityRun {
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object x){return (Map<String,Object>)x;}
    public static void main(String[] args)throws Exception{
        Map<String,Object> data=object(VixenJson.parse(Files.readString(Path.of("tests/fixtures/v5/composed-graphs.json"))));
        int count=0;
        for(Object raw:(List<?>)data.get("cases")){
            Map<String,Object> c=object(raw);Curve curve=Vixen.restoreCurve(Vixen.stringifyTree(c.get("snapshot")));
            List<?> xs=(List<?>)c.get("coordinates"),samples=(List<?>)c.get("samples");
            for(int i=0;i<xs.size();i++){compare(curve.sample(((Number)xs.get(i)).doubleValue()),object(samples.get(i)));count++;}
            for(Object item:(List<?>)c.get("tails")){
                Map<String,Object> t=object(item);double from=((Number)t.get("from")).doubleValue();Curve tail=Vixen.restoreCurve(Vixen.stringifyTree(t.get("snapshot")));
                for(int i=0;i<xs.size();i++)if(((Number)xs.get(i)).doubleValue()>=from){compare(tail.sample(((Number)xs.get(i)).doubleValue()),object(samples.get(i)));count++;}
            }
        }
        System.out.println("Java mixed/shared graph interoperability: "+count+" complete snapshot and checkpoint samples passed");
    }
    private static void compare(Sample sample,Map<String,Object> expected){
        Map<String,Object> actual=sample.asMap();if(!actual.keySet().equals(expected.keySet()))throw new AssertionError("sample field set");
        for(String key:actual.keySet()){
            Object a=actual.get(key),b=expected.get(key);
            if(a instanceof Number x&&b instanceof Number y){if(!Double.isFinite(x.doubleValue())||Math.abs(x.doubleValue()-y.doubleValue())>1e-10*Math.max(1,Math.abs(y.doubleValue())))throw new AssertionError(key+": "+a+" != "+b);}
            else if(!java.util.Objects.equals(a,b))throw new AssertionError(key+": "+a+" != "+b);
        }
    }
}
