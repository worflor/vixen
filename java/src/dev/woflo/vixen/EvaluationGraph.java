package dev.woflo.vixen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;

/** An immutable, generic DAG evaluation kernel. */
final class EvaluationGraph {
    static final int MODE_SCALAR = 0;
    static final int MODE_ANALYTIC_JET = 1;
    static final int MODE_NORMALIZED = 2;
    static final long DEFAULT_NODE_LIMIT = 1_000_000L;

    @FunctionalInterface
    interface Evaluator {
        Object evaluate(Node self, Object[] evaluatedParents, double x,
                        double[] coordinates, int mode);
    }

    private enum Kind { OPAQUE, UNARY, MULTI }

    private record JumpBlock(Node ancestor, double minimumStart,
                             double maximumEnd, boolean allAlwaysIdentity) { }

    static final class Node {
        private final Kind kind;
        private final Object ownerPayload;
        private final List<Node> parents;
        private final Evaluator evaluator;
        private final double effectStart;
        private final double effectEnd;
        private final boolean alwaysIdentity;
        private final long depth;
        private final JumpBlock[] jumpBlocks;

        private Node(Kind kind, Object ownerPayload, List<Node> parents,
                     double effectStart, double effectEnd,
                     boolean alwaysIdentity, Evaluator evaluator) {
            this.kind = Objects.requireNonNull(kind, "kind");
            this.ownerPayload = ownerPayload;
            this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
            this.effectStart = effectStart;
            this.effectEnd = effectEnd;
            this.alwaysIdentity = alwaysIdentity;

            ArrayList<Node> copiedParents = new ArrayList<>(
                    Objects.requireNonNull(parents, "parents").size());
            long maximumParentDepth = 0L;
            for (Node parent : parents) {
                Node checkedParent = Objects.requireNonNull(parent, "parent");
                copiedParents.add(checkedParent);
                maximumParentDepth = Math.max(maximumParentDepth, checkedParent.depth);
            }
            this.parents = List.copyOf(copiedParents);
            this.depth = maximumParentDepth + 1L;
            this.jumpBlocks = kind == Kind.UNARY
                    ? buildJumpBlocks(this, this.parents.get(0))
                    : new JumpBlock[0];
        }

        Object ownerPayload() {
            return ownerPayload;
        }

        List<Node> parents() {
            return parents;
        }

        private static JumpBlock[] buildJumpBlocks(Node node, Node parent) {
            ArrayList<JumpBlock> blocks = new ArrayList<>();
            blocks.add(new JumpBlock(parent, node.effectStart, node.effectEnd,
                    node.alwaysIdentity));
            while (true) {
                int level = blocks.size() - 1;
                JumpBlock firstHalf = blocks.get(level);
                Node midpoint = firstHalf.ancestor();
                if (midpoint.kind != Kind.UNARY
                        || midpoint.jumpBlocks.length <= level) {
                    break;
                }
                JumpBlock secondHalf = midpoint.jumpBlocks[level];
                blocks.add(new JumpBlock(secondHalf.ancestor(),
                        Math.min(firstHalf.minimumStart(), secondHalf.minimumStart()),
                        Math.max(firstHalf.maximumEnd(), secondHalf.maximumEnd()),
                        firstHalf.allAlwaysIdentity()
                                && secondHalf.allAlwaysIdentity()));
            }
            return blocks.toArray(new JumpBlock[0]);
        }
    }

    static final class ResourceLimitException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ResourceLimitException(String message) {
            super(message);
        }
    }

    private static final class EvaluationFrame {
        private final Node node;
        private final Object[] parentValues;
        private int nextParent;

        private EvaluationFrame(Node node) {
            this.node = node;
            this.parentValues = new Object[node.parents.size()];
        }
    }

    private static final class TopologyFrame {
        private final Node node;
        private int nextParent;

        private TopologyFrame(Node node) {
            this.node = node;
        }
    }

    private final long evaluatedNodeLimit;
    private final long topologyNodeLimit;

    EvaluationGraph() {
        this(DEFAULT_NODE_LIMIT, DEFAULT_NODE_LIMIT);
    }

    EvaluationGraph(long evaluatedNodeLimit, long topologyNodeLimit) {
        if (evaluatedNodeLimit <= 0L) {
            throw new IllegalArgumentException("evaluated-node limit must be positive");
        }
        if (topologyNodeLimit <= 0L) {
            throw new IllegalArgumentException("topology-node limit must be positive");
        }
        this.evaluatedNodeLimit = evaluatedNodeLimit;
        this.topologyNodeLimit = topologyNodeLimit;
    }

    static Node opaque(Object ownerPayload, Evaluator evaluator) {
        return new Node(Kind.OPAQUE, ownerPayload, List.of(), 0.0, 0.0,
                false, evaluator);
    }

    static Node unary(Object ownerPayload, Node parent,
                      double effectStart, double effectEnd,
                      boolean alwaysIdentity, Evaluator evaluator) {
        validateBounds(effectStart, effectEnd);
        return new Node(Kind.UNARY, ownerPayload, List.of(
                Objects.requireNonNull(parent, "parent")), effectStart, effectEnd,
                alwaysIdentity, evaluator);
    }

    static Node multi(Object ownerPayload, List<Node> parents, Evaluator evaluator) {
        return new Node(Kind.MULTI, ownerPayload,
                Objects.requireNonNull(parents, "parents"), 0.0, 0.0,
                false, evaluator);
    }

    Object evaluate(Node root, double x, double[] coordinates, int mode) {
        Objects.requireNonNull(root, "root");
        validateQuery(x, mode);

        // Snapshot once, then give each callback its own copy so callback code cannot
        // alter the caller's array or affect the coordinates seen by another callback.
        double[] coordinateSnapshot = coordinates == null ? null : coordinates.clone();
        IdentityHashMap<Node, Node> resolutionCache = new IdentityHashMap<>();
        IdentityHashMap<Node, Object> memo = new IdentityHashMap<>();
        IdentityHashMap<Node, Boolean> active = new IdentityHashMap<>();
        ArrayDeque<EvaluationFrame> stack = new ArrayDeque<>();

        Node evaluationRoot = resolveTransparent(root, x, mode, resolutionCache);
        active.put(evaluationRoot, Boolean.TRUE);
        stack.push(new EvaluationFrame(evaluationRoot));
        long evaluated = 0L;

        while (!stack.isEmpty()) {
            EvaluationFrame frame = stack.peek();
            if (frame.nextParent < frame.node.parents.size()) {
                Node originalParent = frame.node.parents.get(frame.nextParent);
                Node parent = resolveTransparent(originalParent, x, mode,
                        resolutionCache);
                if (memo.containsKey(parent)) {
                    frame.parentValues[frame.nextParent] = memo.get(parent);
                    frame.nextParent++;
                } else {
                    if (active.containsKey(parent)) {
                        throw new IllegalStateException(
                                "cycle detected during evaluation");
                    }
                    active.put(parent, Boolean.TRUE);
                    stack.push(new EvaluationFrame(parent));
                }
                continue;
            }

            if (evaluated >= evaluatedNodeLimit) {
                throw new ResourceLimitException(
                        "evaluated-node limit (" + evaluatedNodeLimit + ") exceeded");
            }
            evaluated++;
            double[] callbackCoordinates = coordinateSnapshot == null
                    ? null : coordinateSnapshot.clone();
            Object result = frame.node.evaluator.evaluate(frame.node,
                    frame.parentValues, x, callbackCoordinates, mode);
            memo.put(frame.node, result);
            active.remove(frame.node);
            stack.pop();
        }

        return memo.get(evaluationRoot);
    }

    /** Returns a deterministic DFS postorder: parents before children. */
    List<Node> topologicalOrder(Node root) {
        Objects.requireNonNull(root, "root");
        IdentityHashMap<Node, Byte> states = new IdentityHashMap<>();
        ArrayDeque<TopologyFrame> stack = new ArrayDeque<>();
        ArrayList<Node> order = new ArrayList<>();
        long discovered = 1L;
        if (discovered > topologyNodeLimit) {
            throw topologyLimitExceeded();
        }
        states.put(root, (byte) 1);
        stack.push(new TopologyFrame(root));

        while (!stack.isEmpty()) {
            TopologyFrame frame = stack.peek();
            if (frame.nextParent < frame.node.parents.size()) {
                Node parent = frame.node.parents.get(frame.nextParent++);
                Byte state = states.get(parent);
                if (state == null) {
                    discovered++;
                    if (discovered > topologyNodeLimit) {
                        throw topologyLimitExceeded();
                    }
                    states.put(parent, (byte) 1);
                    stack.push(new TopologyFrame(parent));
                } else if (state.byteValue() == 1) {
                    throw new IllegalStateException(
                            "cycle detected during topological traversal");
                }
            } else {
                stack.pop();
                states.put(frame.node, (byte) 2);
                order.add(frame.node);
            }
        }
        return List.copyOf(order);
    }

    private ResourceLimitException topologyLimitExceeded() {
        return new ResourceLimitException(
                "topology node limit (" + topologyNodeLimit + ") exceeded");
    }

    private static Node resolveTransparent(Node start, double x, int mode,
                                           IdentityHashMap<Node, Node> cache) {
        if (cache.containsKey(start)) {
            return cache.get(start);
        }
        Node current = start;
        while (current.kind == Kind.UNARY) {
            boolean jumped = false;
            for (int level = current.jumpBlocks.length - 1; level >= 0; level--) {
                JumpBlock block = current.jumpBlocks[level];
                if (blockIsInactive(block, x, mode)) {
                    current = block.ancestor();
                    jumped = true;
                    break;
                }
            }
            if (!jumped) {
                break;
            }
        }
        cache.put(start, current);
        return current;
    }

    private static boolean blockIsInactive(JumpBlock block, double x, int mode) {
        if (block.allAlwaysIdentity()) {
            return true;
        }
        if (mode == MODE_ANALYTIC_JET) {
            return x < block.minimumStart() || x > block.maximumEnd();
        }
        return x <= block.minimumStart() || x >= block.maximumEnd();
    }

    private static void validateBounds(double start, double end) {
        if (Double.isNaN(start) || Double.isNaN(end)) {
            throw new IllegalArgumentException("effect bounds must not be NaN");
        }
        if (start > end) {
            throw new IllegalArgumentException("effect start must be <= effect end");
        }
    }

    private static void validateQuery(double x, int mode) {
        if (!Double.isFinite(x)) {
            throw new IllegalArgumentException("x must be finite");
        }
        if (mode != MODE_SCALAR && mode != MODE_ANALYTIC_JET
                && mode != MODE_NORMALIZED) {
            throw new IllegalArgumentException("mode must be 0, 1, or 2");
        }
    }
}
