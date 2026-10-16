package dev.woflo.vixen;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CurveEvaluation {
    private CurveEvaluation(){}
    static final String GRAPH_ALGORITHM="vixen-graph/1";
    private static final EvaluationGraph GRAPH=new EvaluationGraph();
    interface Shape {
        Map<String,Object> replayFrame(Object[] parents);
        Vixen.Restored rebuild(Object[] parents);
        default boolean canFork(){return true;}
        default EvaluationGraph.Node tailAlias(double from){return null;}
    }
    private record NativeInfluence(Influence influence,EvaluationGraph.Node parent) implements Shape {
        public EvaluationGraph.Node tailAlias(double from){return influence.end()<=from?parent:null;}
        public Map<String,Object> replayFrame(Object[] parents){return VixenJson.influenceSnapshotFrame(influence,parents[0]);}
        public Vixen.Restored rebuild(Object[] parents){return Vixen.replayInfluence((Curve)parents[0],influence);}
    }
    static EvaluationGraph.Node node(Curve curve){
        EvaluationGraph.Node found=curve.evaluationNode;if(found!=null)return found;
        synchronized(curve){
            if(curve.evaluationNode==null){
                if(curve instanceof Vixen.TimelineCurve nativeCurve&&nativeCurve.timeline().influences()>0){
                    Timeline t=nativeCurve.timeline();EvaluationGraph.Node current=node(new Vixen.TimelineCurve(t.withoutInfluences()));Config config=curve.config();
                    for(Influence influence:t.influenceList())current=EvaluationGraph.unary(new NativeInfluence(influence,current),current,influence.at(),influence.end(),influence.amount()==0,(self,parents,x,h,mode)->{if(mode==EvaluationGraph.MODE_ANALYTIC_JET){Analytic.Result p=(Analytic.Result)parents[0];return CompositionAnalytic.influence(p,influence,x,config,influence.apply(p.sample(),x,config));}requireScalar(mode);return influence.apply((Sample)parents[0],x,config);});
                    curve.evaluationNode=current;
                }else curve.evaluationNode=EvaluationGraph.opaque(curve,(self,parents,x,h,mode)->{if(mode==EvaluationGraph.MODE_ANALYTIC_JET&&curve instanceof Vixen.TimelineCurve t)return t.timeline().analyticJet(x,h);requireScalar(mode);return h==null?curve.sample(x):curve.at(x,h);});
            }
            return curve.evaluationNode;
        }
    }
    static void requireScalar(int mode){
        if(mode!=EvaluationGraph.MODE_SCALAR)throw new UnsupportedOperationException("This Java graph adapter has no evaluator for the requested representation");
    }
    static Sample sample(Curve curve,double x,double[] coordinates){
        if(!Double.isFinite(x)||x<curve.domainStart()||x>curve.domainEnd()||x<curve.availableFrom())throw new IllegalArgumentException("x outside available curve domain");
        return (Sample)GRAPH.evaluate(node(curve),x,coordinates,EvaluationGraph.MODE_SCALAR);
    }
    static Analytic.Result jet(Curve curve,double x,double[] coordinates){
        if(!Double.isFinite(x)||x<curve.domainStart()||x>curve.domainEnd()||x<curve.availableFrom())throw new IllegalArgumentException("x outside analytic curve domain");
        if(coordinates!=null)coordinates=Config.vector(coordinates,1_000_000);
        return (Analytic.Result)GRAPH.evaluate(node(curve),x,coordinates,EvaluationGraph.MODE_ANALYTIC_JET);
    }
    static Object analytic(EvaluationGraph.Node node,double x,double[] coordinates){return GRAPH.evaluate(node,x,coordinates,EvaluationGraph.MODE_ANALYTIC_JET);}
    static Object evaluate(EvaluationGraph.Node node,double x,double[] coordinates){return GRAPH.evaluate(node,x,coordinates,EvaluationGraph.MODE_SCALAR);}
    static Curve fork(Curve curve,double at,String operation,Double handoffWidth){
        if(!Double.isFinite(at)||at<curve.domainStart()||at<curve.availableFrom()||at<curve.latestWriteTime()||at>=curve.domainEnd())throw new IllegalArgumentException("Fork precedes available history or lies outside curve domain");
        if(operation==null||operation.isEmpty())throw new IllegalArgumentException("Fork operation must be nonempty");Seed.of(operation);
        double width=handoffWidth==null?Math.min(16,curve.domainEnd()-at):handoffWidth;
        if(!Double.isFinite(width)||width<=0||width>50000||at+width<=at||at+width>curve.domainEnd())throw new IllegalArgumentException("Fork handoff exceeds effective curve domain");
        EvaluationGraph.Node root=node(curve);IdentityHashMap<EvaluationGraph.Node,Vixen.Restored> values=new IdentityHashMap<>();
        List<EvaluationGraph.Node> order=GRAPH.topologicalOrder(root);
        for(EvaluationGraph.Node n:order)if(n.ownerPayload() instanceof Shape shape&&!shape.canFork())throw new UnsupportedOperationException("Fork input lineages before coupling; projection branch semantics are not defined");
        for(EvaluationGraph.Node n:order){
            Object owner=n.ownerPayload();Object[] parents=n.parents().stream().map(values::get).toArray();Vixen.Restored rebuilt;
            if(owner instanceof Shape shape)rebuilt=shape.rebuild(parents);
            else if(owner instanceof Curve opaque)rebuilt=opaque.fork(at,operation,width);
            else throw new UnsupportedOperationException("Graph source does not support independent branching");
            values.put(n,rebuilt);
        }
        return Vixen.requireCurve(values.get(root));
    }
    static String snapshot(Curve curve,Double from){
        if(from!=null&&(!Double.isFinite(from)||from<curve.domainStart()||from>curve.domainEnd()||from<curve.availableFrom()))throw new IllegalArgumentException("Checkpoint outside available curve domain");
        return snapshot(node(curve),from);
    }
    @SuppressWarnings("unchecked")
    static String snapshot(EvaluationGraph.Node root,Double from){
        List<EvaluationGraph.Node> order=GRAPH.topologicalOrder(root);IdentityHashMap<EvaluationGraph.Node,Integer> costs=new IdentityHashMap<>();
        for(EvaluationGraph.Node n:order){int size=1;for(EvaluationGraph.Node p:n.parents())size=Math.min(65,size+costs.get(p));costs.put(n,size);}
        boolean flat=order.size()>32||costs.get(root)>64;IdentityHashMap<EvaluationGraph.Node,Object> refs=new IdentityHashMap<>();ArrayList<Map<String,Object>> frames=new ArrayList<>();
        for(EvaluationGraph.Node n:order){
            Object owner=n.ownerPayload();EvaluationGraph.Node alias=from!=null&&owner instanceof Shape shape?shape.tailAlias(from):null;
            if(alias!=null){if(!refs.containsKey(alias))throw new IllegalStateException("Tail aliases must refer to ancestors");refs.put(n,refs.get(alias));continue;}
            Object[] parents=n.parents().stream().map(refs::get).toArray();Map<String,Object> frame;
            if(owner instanceof Shape shape)frame=shape.replayFrame(parents);
            else if(owner instanceof Vixen.Restored opaque)frame=(Map<String,Object>)VixenJson.parse(from==null?opaque.snapshotJson():opaque.tailJson(from));
            else throw new IllegalStateException("Graph node has no replay frame");
            if(GRAPH_ALGORITHM.equals(frame.get("algorithm")))throw new IllegalArgumentException("Nested composition graphs require a registered shape");
            if(flat){refs.put(n,frames.size());frames.add(frame);}else refs.put(n,frame);
        }
        return Vixen.stringifyTree(flat?compact(frames,((Number)refs.get(root)).intValue()):refs.get(root));
    }
    private static Map<String,Object> compact(List<Map<String,Object>> frames,int root){
        boolean[] reachable=new boolean[frames.size()];ArrayList<Integer> queue=new ArrayList<>();queue.add(root);
        for(int i=0;i<queue.size();i++){int index=queue.get(i);if(reachable[index])continue;reachable[index]=true;for(int parent:references(frames.get(index)))queue.add(parent);}
        ArrayList<Map<String,Object>> nodes=new ArrayList<>();int[] remap=new int[frames.size()];
        for(int i=0;i<frames.size();i++)if(reachable[i]){remap[i]=nodes.size();Map<String,Object> frame=new LinkedHashMap<>(frames.get(i));remapReferences(frame,remap);nodes.add(frame);}
        Map<String,Object> graph=new LinkedHashMap<>();graph.put("algorithm",GRAPH_ALGORITHM);graph.put("nodes",nodes);graph.put("root",remap[root]);return graph;
    }
    private static List<Integer> references(Map<String,Object> frame){
        ArrayList<Integer> refs=new ArrayList<>();Object algorithm=frame.get("algorithm");
        if(Influence.ALGORITHM.equals(algorithm)||Influence.ALGORITHM_WINDOWED.equals(algorithm)||Influence.ALGORITHM_ADDITIVE.equals(algorithm))refs.add(((Number)frame.get("curve")).intValue());
        else if(Operations.ALGORITHM.equals(algorithm)){
            if(frame.containsKey("curve"))refs.add(((Number)frame.get("curve")).intValue());
            if(frame.containsKey("group"))refs.add(((Number)frame.get("group")).intValue());
            if(frame.get("curves") instanceof List<?> curves)for(Object c:curves)refs.add(((Number)c).intValue());
        }
        return refs;
    }
    private static void remapReferences(Map<String,Object> frame,int[] remap){
        if(references(frame).isEmpty())return;
        for(String key:new String[]{"curve","group"})if(frame.get(key) instanceof Number n)frame.put(key,remap[n.intValue()]);
        if(frame.get("curves") instanceof List<?> curves)frame.put("curves",curves.stream().map(v->remap[((Number)v).intValue()]).toList());
    }
    @SuppressWarnings("unchecked")
    static Vixen.Restored restoreGraph(Map<String,Object> graph){
        if(!graph.keySet().equals(java.util.Set.of("algorithm","nodes","root"))||!(graph.get("nodes") instanceof List<?> nodes)||nodes.isEmpty()||nodes.size()>1_000_000)throw new IllegalArgumentException("Invalid composition graph");
        if(!(graph.get("root") instanceof Number root)||root.doubleValue()!=nodes.size()-1)throw new IllegalArgumentException("Invalid composition graph root");
        ArrayList<Vixen.Restored> values=new ArrayList<>();
        java.util.function.Function<Object,Vixen.Restored> read=ref->{if(!(ref instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()!=Math.rint(n.doubleValue())||n.doubleValue()<0||n.doubleValue()>=values.size())throw new IllegalArgumentException("Graph references must identify an earlier node");return values.get(n.intValue());};
        for(Object value:nodes){
            if(!(value instanceof Map<?,?> raw))throw new IllegalArgumentException("Graph frame must be an object");Map<String,Object> frame=(Map<String,Object>)raw;Object algorithm=frame.get("algorithm");Vixen.Restored restored;
            if(GRAPH_ALGORITHM.equals(algorithm))throw new IllegalArgumentException("Nested composition graphs are not allowed");
            if(Influence.ALGORITHM.equals(algorithm)||Influence.ALGORITHM_WINDOWED.equals(algorithm)||Influence.ALGORITHM_ADDITIVE.equals(algorithm))restored=Vixen.replayInfluence(Vixen.requireCurve(read.apply(frame.get("curve"))),VixenJson.influenceFrame(frame,!Influence.ALGORITHM.equals(algorithm)));
            else if(Operations.ALGORITHM.equals(algorithm))restored=Operations.restoreSnapshot(frame,read);
            else if(ExtendedOps.accepts(algorithm))restored=ExtendedOps.restore(frame,read);
            else restored=Vixen.restoreTree(frame);
            values.add(restored);
        }
        return values.get(values.size()-1);
    }
}
