package dev.woflo.vixen;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReplayValidationRun {
    private static final List<String> accepted=new ArrayList<>();
    private static void reject(String name,Object tree){try{Vixen.restore(tree);accepted.add(name);}catch(IllegalArgumentException expected){}}
    private static Map<String,Object> graph(List<?> nodes,Object root){return new LinkedHashMap<>(Map.of("algorithm","vixen-graph/1","nodes",nodes,"root",root));}
    @SuppressWarnings("unchecked") public static void main(String[] args){
        Map<String,Object> base=(Map<String,Object>)VixenJson.parse(Vixen.createCoherent().snapshotJson());
        if(!Vixen.restoreCurve(Vixen.stringifyTree(graph(List.of(base),0))).sample(150).equals(Vixen.createCoherent().sample(150)))throw new AssertionError("valid graph refused");
        for(Object root:new Object[]{-1,1,.5,true,"0"})reject("root "+root,graph(List.of(base),root));
        Map<String,Object> extra=graph(List.of(base),0);extra.put("extra",true);reject("unknown graph field",extra);
        reject("empty graph",graph(List.of(),0));
        reject("unknown frame",graph(List.of(Map.of("algorithm","unknown")),0));
        Object recipe=VixenJson.parse("{\"type\":\"impulse\",\"at\":120,\"magnitude\":0.1,\"attack\":1,\"relaxation\":10}");
        Object validFrame=Map.of("algorithm",Operations.ALGORITHM,"operation","impulse","curve",0,"recipe",recipe);
        Curve validCurve=Vixen.restoreCurve(Vixen.stringifyTree(graph(List.of(base,validFrame),1)));
        if(validCurve.sample(125).equals(Vixen.createCoherent().sample(125)))throw new AssertionError("valid operation frame was ignored");
        for(Object ref:new Object[]{-1,1,.5,true,"0",base}){
            Object frame=Map.of("algorithm",Operations.ALGORITHM,"operation","impulse","curve",ref,"recipe",recipe);
            reject("reference "+ref,graph(List.of(base,frame),1));
        }
        Map<String,Object> cycle=new LinkedHashMap<>();cycle.put("self",cycle);reject("object cycle",cycle);
        Map<String,Object> tail=(Map<String,Object>)VixenJson.parse(Vixen.create().tailJson(220));
        for(Object count:new Object[]{-.5,.5,-1,1e30}){Map<String,Object> bad=new LinkedHashMap<>(tail);bad.put("folded",count);reject("legacy folded "+count,bad);}
        Map<String,Object> witness=new LinkedHashMap<>(tail);witness.put("witness","not-a-sha256");reject("legacy witness",witness);
        for(long count:new long[]{9007199254740993L,Long.MAX_VALUE}){Map<String,Object> valid=new LinkedHashMap<>(tail);valid.put("folded",count);if(VixenJson.tail(Vixen.stringifyTree(valid)).folded()!=count)throw new AssertionError("valid legacy long count lost precision");}
        if(!accepted.isEmpty())throw new AssertionError("Malformed replay accepted: "+accepted);
        System.out.println("Malformed graph references, cycles and checkpoint metadata fail closed");
    }
}
