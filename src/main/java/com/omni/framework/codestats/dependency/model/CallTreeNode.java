package com.omni.framework.codestats.dependency.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 调用树节点。
 */
public class CallTreeNode {
    private final String methodId;
    private final MethodInfo info;
    private final List<CallTreeNode> children = new ArrayList<>();
    private boolean cycleCut = false;
    private boolean depthCut = false;

    public CallTreeNode(String methodId, MethodInfo info) {
        this.methodId = methodId;
        this.info = info;
    }

    public void addChild(CallTreeNode c) { children.add(c); }
    public String getMethodId() { return methodId; }
    public MethodInfo getInfo() { return info; }
    public List<CallTreeNode> getChildren() { return children; }
    public boolean isCycleCut() { return cycleCut; }
    public void setCycleCut(boolean v) { cycleCut = v; }
    public boolean isDepthCut() { return depthCut; }
    public void setDepthCut(boolean v) { depthCut = v; }
}