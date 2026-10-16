package dev.woflo.vixen;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Random;

public final class EvaluationGraphOracleRun {
    private record Model(int kind,int[] parents,double start,double end,double bias,boolean identity){}
    public static void main(String[] args){
        Random random=new Random(0x67A9E1L);ArrayList<EvaluationGraph.Node> nodes=new ArrayList<>();ArrayList<Model> models=new ArrayList<>();
        for(int i=0;i<1500;i++){
            int kind=i<3?0:random.nextInt(3),n=kind==0?0:kind==1?1:2+random.nextInt(3);int[] parents=new int[n];ArrayList<EvaluationGraph.Node> p=new ArrayList<>();
            for(int j=0;j<n;j++){parents[j]=random.nextInt(i);p.add(nodes.get(parents[j]));}
            Model m=new Model(kind,parents,random.nextBoolean()?0:1,2,random.nextInt(7)-3,random.nextInt(9)==0);models.add(m);
            EvaluationGraph.Evaluator evaluator=(self,values,x,h,mode)->{Model a=(Model)self.ownerPayload();if(a.kind==0)return a.bias;if(a.kind==1)return (Double)values[0]+a.bias;double sum=a.bias;for(Object value:values)sum+=(Double)value;return sum;};
            nodes.add(kind==0?EvaluationGraph.opaque(m,evaluator):kind==1?EvaluationGraph.unary(m,p.get(0),m.start,m.end,m.identity,evaluator):EvaluationGraph.multi(m,p,evaluator));
        }
        EvaluationGraph graph=new EvaluationGraph();int comparisons=0;
        for(int mode=0;mode<3;mode++)for(double x:new double[]{-1,0,.5,1,2,3}){
            double[] expected=new double[models.size()];
            for(int i=0;i<models.size();i++){Model m=models.get(i);boolean skip=m.identity||(mode==1?x<m.start||x>m.end:x<=m.start||x>=m.end);if(m.kind==0)expected[i]=m.bias;else if(m.kind==1)expected[i]=skip?expected[m.parents[0]]:expected[m.parents[0]]+m.bias;else{double sum=m.bias;for(int p:m.parents)sum+=expected[p];expected[i]=sum;}}
            for(int index=0;index<nodes.size();index+=17){double actual=(Double)graph.evaluate(nodes.get(index),x,null,mode);if(Double.doubleToLongBits(actual)!=Double.doubleToLongBits(expected[index]))throw new AssertionError("independent DAG mismatch");comparisons++;}
        }
        List<EvaluationGraph.Node> order=graph.topologicalOrder(nodes.get(nodes.size()-1));IdentityHashMap<EvaluationGraph.Node,Integer> seen=new IdentityHashMap<>();
        for(EvaluationGraph.Node n:order){for(EvaluationGraph.Node p:n.parents())if(!seen.containsKey(p))throw new AssertionError("non-parent-first traversal");if(seen.put(n,seen.size())!=null)throw new AssertionError("duplicate node");}
        int[] liveCalls={0},closedCalls={0};EvaluationGraph.Node n=EvaluationGraph.opaque(null,(self,p,x,h,m)->0);
        n=EvaluationGraph.unary(null,n,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY,false,(self,p,x,h,m)->{liveCalls[0]++;return (Integer)p[0]+1;});
        for(int i=0;i<6000;i++)n=EvaluationGraph.unary(null,n,1,2,false,(self,p,x,h,m)->{closedCalls[0]++;return -100;});
        if(!graph.evaluate(n,3,null,0).equals(1)||liveCalls[0]!=1||closedCalls[0]!=0)throw new AssertionError("settled prefix erased older live state");
        System.out.println("Independent graph model: "+comparisons+" comparisons; shared topology and settled-prefix liveness passed");
    }
}
