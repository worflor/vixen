package dev.woflo.vixen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class CoherentParityRun {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Map<String,Object> data=(Map<String,Object>)VixenJson.parse(Files.readString(Path.of("tests/fixtures/v5/coherent-timelines.json")));
        List<Object> cases=(List<Object>)data.get("cases");int samples=0,tails=0;
        for(Object value:cases){
            Map<String,Object> item=(Map<String,Object>)value;
            Curve curve=Vixen.requireCurve(Vixen.restore(item.get("snapshot")));
            for(Object row:(List<Object>)item.get("samples")){
                Map<String,Object> expected=(Map<String,Object>)row;double x=((Number)expected.get("x")).doubleValue();
                Map<String,Object> actual=curve.sample(x).asMap();
                if(!actual.keySet().equals(expected.keySet()))throw new AssertionError("sample fields differ");
                for(String key:expected.keySet()){
                    Object a=actual.get(key),b=expected.get(key);
                    if(b instanceof Number n){double want=n.doubleValue(),got=((Number)a).doubleValue();
                        if(!Double.isFinite(got)||Math.abs(got-want)>1e-10*Math.max(1,Math.abs(want)))throw new AssertionError(key+" at "+x+": "+got+" != "+want);
                    }else if(!java.util.Objects.equals(a,b))throw new AssertionError("field mismatch: "+key);
                }
                samples++;
            }
            for(Object row:(List<Object>)item.get("tails")){
                Map<String,Object> tail=(Map<String,Object>)row;double from=((Number)tail.get("from")).doubleValue();
                Object actual=VixenJson.parse(curve.tailJson(from));
                if(!actual.equals(tail))throw new AssertionError("coherent tail metadata/witness differs from JavaScript: "+from+"\n"+actual+"\n"+tail);
                Curve restored=Vixen.requireCurve(Vixen.restore(tail));
                if(!restored.sample(from+10).equals(curve.sample(from+10)))throw new AssertionError("foreign coherent tail replay");
                tails++;
            }
        }
        if(cases.size()!=8||samples!=88||tails!=16)throw new AssertionError("coherent corpus was not fully exercised");
        System.out.println("Java coherent interoperability: "+cases.size()+" cases, "+samples+" samples, "+tails+" exact tail/witness comparisons");
    }
}
