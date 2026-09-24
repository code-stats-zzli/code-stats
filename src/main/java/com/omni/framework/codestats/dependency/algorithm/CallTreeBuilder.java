package com.omni.framework.codestats.dependency.algorithm;



import com.omni.framework.codestats.dependency.model.CallTreeNode;
import com.omni.framework.codestats.dependency.model.MethodCallGraph;
import com.omni.framework.codestats.dependency.model.MethodInfo;

import java.util.HashSet;
import java.util.Set;

/**
 * 从方法调用图构建调用树。
 */
public class CallTreeBuilder {
    private final MethodCallGraph graph;
    private final int maxDepth;

    public CallTreeBuilder(MethodCallGraph graph, int maxDepth) {
        this.graph = graph;
        this.maxDepth = maxDepth;
    }

    public CallTreeNode build(String entryMethodId) {
        MethodInfo info = graph.getMethod(entryMethodId);
        if (info == null) return null;
        return buildInternal(entryMethodId, 0, new HashSet<>());
    }

    private CallTreeNode buildInternal(String methodId, int depth, Set<String> stack) {
        MethodInfo info = graph.getMethod(methodId);
        if (info == null) {
            info = new MethodInfo(methodId, "?", "?", java.util.Collections.emptyList(),
                    "?", -1, false, false);
        }
        CallTreeNode node = new CallTreeNode(methodId, info);

        if (depth >= maxDepth) { node.setDepthCut(true); return node; }
        if (stack.contains(methodId)) { node.setCycleCut(true); return node; }

        stack.add(methodId);
        for (String callee : graph.getCallees(methodId)) {
            CallTreeNode c = buildInternal(callee, depth + 1, stack);
            if (c != null) node.addChild(c);
        }
        stack.remove(methodId);
        return node;
    }
}