package dev.woflo.vixen;

import java.util.ArrayList;
import java.util.List;

/** Standalone executable checks for EvaluationGraph. */
public final class EvaluationGraphRun {
    private EvaluationGraphRun() { }

    public static void main(String[] args) {
        testActiveUnaryChain();
        testSettledAndIdentityUnaryChain();
        testOlderPermanentWindow();
        testSharedGroupProjectionDag();
        testTopologicalOrder();
        testModeSpecificEdges();
        testLimits();
        testImmutabilityFreshQueriesAndNullMemo();
        System.out.println("EvaluationGraphRun: all checks passed");
    }

    private static void testActiveUnaryChain() {
        int[] sourceCalls = {0};
        int[] stageCalls = {0};
        EvaluationGraph.Node node = EvaluationGraph.opaque("source",
                (self, parents, x, coordinates, mode) -> {
                    sourceCalls[0]++;
                    return 0;
                });
        for (int i = 0; i < 6000; i++) {
            node = EvaluationGraph.unary(null, node, -1.0, 1.0, false,
                    (self, parents, x, coordinates, mode) -> {
                        stageCalls[0]++;
                        return ((Integer) parents[0]) + 1;
                    });
        }
        Object result = new EvaluationGraph().evaluate(node, 0.0, null,
                EvaluationGraph.MODE_SCALAR);
        check(Integer.valueOf(6000).equals(result), "active chain result");
        check(sourceCalls[0] == 1, "active chain source callback count");
        check(stageCalls[0] == 6000, "active chain stage callback count");
    }

    private static void testSettledAndIdentityUnaryChain() {
        int[] sourceCalls = {0};
        int[] stageCalls = {0};
        EvaluationGraph.Node node = EvaluationGraph.opaque("source",
                (self, parents, x, coordinates, mode) -> {
                    sourceCalls[0]++;
                    return 42;
                });
        for (int i = 0; i < 6000; i++) {
            boolean identity = (i & 1) == 0;
            node = EvaluationGraph.unary(null, node,
                    identity ? 0.0 : 1.0, identity ? 1.0 : 2.0,
                    identity, (self, parents, x, coordinates, mode) -> {
                        stageCalls[0]++;
                        return -1;
                    });
        }
        Object result = new EvaluationGraph().evaluate(node, 0.0, null,
                EvaluationGraph.MODE_SCALAR);
        check(Integer.valueOf(42).equals(result), "settled chain result");
        check(sourceCalls[0] == 1, "settled chain source evaluated once");
        check(stageCalls[0] == 0, "settled and identity callbacks skipped");
    }

    private static void testOlderPermanentWindow() {
        int[] sourceCalls = {0};
        int[] permanentCalls = {0};
        int[] closedCalls = {0};
        EvaluationGraph.Node node = EvaluationGraph.opaque("source",
                (self, parents, x, coordinates, mode) -> {
                    sourceCalls[0]++;
                    return 10;
                });
        node = EvaluationGraph.unary("permanent", node,
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false,
                (self, parents, x, coordinates, mode) -> {
                    permanentCalls[0]++;
                    return ((Integer) parents[0]) + 1;
                });
        for (int i = 0; i < 3000; i++) {
            node = EvaluationGraph.unary(null, node, 1.0, 2.0, false,
                    (self, parents, x, coordinates, mode) -> {
                        closedCalls[0]++;
                        return -1;
                    });
        }
        Object result = new EvaluationGraph().evaluate(node, 0.0, null,
                EvaluationGraph.MODE_SCALAR);
        check(Integer.valueOf(11).equals(result), "permanent window result");
        check(sourceCalls[0] == 1, "permanent window source count");
        check(permanentCalls[0] == 1, "older permanent stage remains live");
        check(closedCalls[0] == 0, "newer closed stages are skipped");
    }

    private static void testSharedGroupProjectionDag() {
        int[] groupCalls = {0};
        int[] projectionCalls = {0};
        EvaluationGraph.Node group = EvaluationGraph.opaque("group-0",
                (self, parents, x, coordinates, mode) -> {
                    groupCalls[0]++;
                    return new Object[]{0};
                });
        for (int i = 1; i <= 600; i++) {
            int generation = i;
            EvaluationGraph.Node previous = group;
            EvaluationGraph.Node left = EvaluationGraph.unary("left", previous,
                    Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false,
                    (self, parents, x, coordinates, mode) -> {
                        projectionCalls[0]++;
                        return parents[0];
                    });
            EvaluationGraph.Node right = EvaluationGraph.unary("right", previous,
                    Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false,
                    (self, parents, x, coordinates, mode) -> {
                        projectionCalls[0]++;
                        return parents[0];
                    });
            group = EvaluationGraph.multi("group-" + generation,
                    List.of(left, right), (self, parents, x, coordinates, mode) -> {
                        groupCalls[0]++;
                        return new Object[]{generation};
                    });
        }
        Object[] result = (Object[]) new EvaluationGraph().evaluate(group, 0.5,
                null, EvaluationGraph.MODE_SCALAR);
        check(Integer.valueOf(600).equals(result[0]), "group DAG result");
        check(groupCalls[0] == 601, "each group callback evaluated once");
        check(projectionCalls[0] == 1200, "each projection callback evaluated once");
    }

    private static void testTopologicalOrder() {
        EvaluationGraph.Node a = EvaluationGraph.opaque("a",
                (self, parents, x, coordinates, mode) -> "a");
        EvaluationGraph.Node shared = EvaluationGraph.unary("shared", a,
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false,
                (self, parents, x, coordinates, mode) -> parents[0]);
        EvaluationGraph.Node b = EvaluationGraph.opaque("b",
                (self, parents, x, coordinates, mode) -> "b");
        EvaluationGraph.Node root = EvaluationGraph.multi("root",
                List.of(shared, b, shared),
                (self, parents, x, coordinates, mode) -> parents);
        List<EvaluationGraph.Node> order = new EvaluationGraph().topologicalOrder(root);
        check(order.size() == 4, "topology contains unique identities");
        check(order.get(0) == a && order.get(1) == shared
                        && order.get(2) == b && order.get(3) == root,
                "topology is deterministic parent-first DFS order");
        expectUnsupported(() -> order.add(a), "topology result must be immutable");
    }

    private static void testModeSpecificEdges() {
        int[] sourceCalls = {0};
        int[] edgeCalls = {0};
        int[] observedMode = {-1};
        EvaluationGraph.Node source = EvaluationGraph.opaque("source",
                (self, parents, x, coordinates, mode) -> {
                    sourceCalls[0]++;
                    return 3;
                });
        EvaluationGraph.Node edge = EvaluationGraph.unary("edge", source,
                0.0, 1.0, false, (self, parents, x, coordinates, mode) -> {
                    edgeCalls[0]++;
                    observedMode[0] = mode;
                    return ((Integer) parents[0]) + 5;
                });
        EvaluationGraph graph = new EvaluationGraph();
        Object scalar = graph.evaluate(edge, 0.0, null,
                EvaluationGraph.MODE_SCALAR);
        check(Integer.valueOf(3).equals(scalar), "scalar edge is skipped");
        check(edgeCalls[0] == 0 && sourceCalls[0] == 1,
                "scalar exact edge callback counts");
        Object analytic = graph.evaluate(edge, 0.0, null,
                EvaluationGraph.MODE_ANALYTIC_JET);
        check(Integer.valueOf(8).equals(analytic), "analytic exact edge is evaluated");
        check(edgeCalls[0] == 1 && sourceCalls[0] == 2,
                "analytic exact edge callback counts");
        check(observedMode[0] == EvaluationGraph.MODE_ANALYTIC_JET,
                "mode forwarded unchanged");
        Object normalized = graph.evaluate(edge, 1.0, null,
                EvaluationGraph.MODE_NORMALIZED);
        check(Integer.valueOf(3).equals(normalized), "normalized edge is skipped");
        check(edgeCalls[0] == 1 && sourceCalls[0] == 3,
                "normalized exact edge callback counts");
    }

    private static void testLimits() {
        int[] sourceCalls = {0};
        int[] rootCalls = {0};
        EvaluationGraph.Node source = EvaluationGraph.opaque("source",
                (self, parents, x, coordinates, mode) -> {
                    sourceCalls[0]++;
                    return 1;
                });
        EvaluationGraph.Node root = EvaluationGraph.unary("root", source,
                -1.0, 1.0, false, (self, parents, x, coordinates, mode) -> {
                    rootCalls[0]++;
                    return parents[0];
                });
        EvaluationGraph limited = new EvaluationGraph(1, 10);
        expectResourceLimit(() -> limited.evaluate(root, 0.0, null,
                EvaluationGraph.MODE_SCALAR), "evaluated-node limit");
        check(sourceCalls[0] == 1 && rootCalls[0] == 0,
                "evaluation limit refuses the next callback");
        check(new EvaluationGraph(10,2).topologicalOrder(root).size()==2,
                "topology limit permits exactly the declared number of nodes");
        expectResourceLimit(() -> new EvaluationGraph(10, 1).topologicalOrder(root),
                "topology node limit");
        expectIllegalArgument(() -> new EvaluationGraph(0, 1),
                "evaluated-node limit must be positive");
    }

    private static void testImmutabilityFreshQueriesAndNullMemo() {
        int[] sourceCalls = {0};
        int[] extraCalls = {0};
        int[] rootCalls = {0};
        EvaluationGraph.Node source = EvaluationGraph.opaque("source",
                (self, parents, x, coordinates, mode) -> {
                    sourceCalls[0]++;
                    if (coordinates != null) {
                        coordinates[0] = 99.0;
                    }
                    return sourceCalls[0];
                });
        EvaluationGraph.Node extra = EvaluationGraph.opaque("extra",
                (self, parents, x, coordinates, mode) -> {
                    extraCalls[0]++;
                    return 100;
                });
        ArrayList<EvaluationGraph.Node> inputParents = new ArrayList<>();
        inputParents.add(source);
        EvaluationGraph.Node root = EvaluationGraph.multi("root", inputParents,
                (self, parents, x, coordinates, mode) -> {
                    rootCalls[0]++;
                    check(parents.length == 1, "parent ordering and count");
                    return parents[0];
                });
        inputParents.add(extra);
        check(root.parents().size() == 1 && root.parents().get(0) == source,
                "factory copies the parent list");
        expectUnsupported(() -> root.parents().add(extra),
                "node parent list must be immutable");

        EvaluationGraph graph = new EvaluationGraph();
        double[] coordinates = {4.0};
        Object first = graph.evaluate(root, 0.0, coordinates,
                EvaluationGraph.MODE_SCALAR);
        Object second = graph.evaluate(root, 0.0, coordinates,
                EvaluationGraph.MODE_SCALAR);
        check(Integer.valueOf(1).equals(first) && Integer.valueOf(2).equals(second),
                "each query has a fresh evaluation memo");
        check(sourceCalls[0] == 2 && rootCalls[0] == 2 && extraCalls[0] == 0,
                "fresh query callback counts");
        check(coordinates[0] == 4.0, "callback cannot mutate caller coordinates");

        int[] nullSourceCalls = {0};
        EvaluationGraph.Node nullSource = EvaluationGraph.opaque("null-source",
                (self, parents, x, coords, mode) -> {
                    nullSourceCalls[0]++;
                    return null;
                });
        EvaluationGraph.Node duplicateParent = EvaluationGraph.multi("duplicate",
                List.of(nullSource, nullSource),
                (self, parents, x, coords, mode) -> {
                    check(parents.length == 2 && parents[0] == null
                                    && parents[1] == null,
                            "duplicate parent ordering and null results");
                    return "ok";
                });
        Object duplicateResult = graph.evaluate(duplicateParent, 0.0, null,
                EvaluationGraph.MODE_SCALAR);
        check("ok".equals(duplicateResult), "duplicate-parent result");
        check(nullSourceCalls[0] == 1,
                "null result is memoized and duplicate parent executes once");
    }

    private static void expectResourceLimit(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message + " was not enforced");
        } catch (EvaluationGraph.ResourceLimitException expected) {
            check(expected.getMessage().contains("limit"),
                    "resource exception has a clear message");
        }
    }

    private static void expectUnsupported(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    private static void expectIllegalArgument(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
