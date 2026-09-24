package com.omni.framework.codestats.dependency.model;

import java.util.*;

/**
 * 方法级调用图。
 */
public class MethodCallGraph {
    private final Map<String, MethodInfo> methods = new HashMap<>();
    private final Map<String, Set<String>> outEdges = new HashMap<>();
    private final Map<String, Set<String>> inEdges = new HashMap<>();
    private final List<MethodCallEdge> edges = new ArrayList<>();

    public void addMethod(MethodInfo m) {
        if (m == null) return;
        methods.put(m.getId(), m);
        outEdges.computeIfAbsent(m.getId(), k -> new LinkedHashSet<>());
        inEdges.computeIfAbsent(m.getId(), k -> new LinkedHashSet<>());
    }

    public void addCall(MethodCallEdge e) {
        if (e == null || !methods.containsKey(e.getCallerId())) return;
        edges.add(e);
        outEdges.computeIfAbsent(e.getCallerId(), k -> new LinkedHashSet<>()).add(e.getCalleeId());
        inEdges.computeIfAbsent(e.getCalleeId(), k -> new LinkedHashSet<>()).add(e.getCallerId());
    }

    public MethodInfo getMethod(String id) { return methods.get(id); }
    public Collection<MethodInfo> getAllMethods() { return methods.values(); }
    public Set<String> getCallees(String id) { return outEdges.getOrDefault(id, Collections.emptySet()); }
    public Set<String> getCallers(String id) { return inEdges.getOrDefault(id, Collections.emptySet()); }
    public List<MethodCallEdge> getEdges() { return edges; }
    public int size() { return methods.size(); }
    public int edgeCount() { return edges.size(); }

    /** 找"入口方法"（无入向调用，且是 public 或构造器） */
    public List<MethodInfo> findEntryPoints() {
        List<MethodInfo> entries = new ArrayList<>();
        for (MethodInfo m : methods.values()) {
            if (getCallers(m.getId()).isEmpty()) entries.add(m);
        }
        return entries;
    }

    /** 按方法名查找（用于回退匹配） */
    public List<MethodInfo> findByMethodName(String name) {
        List<MethodInfo> list = new ArrayList<>();
        for (MethodInfo m : methods.values()) {
            if (m.getMethodName().equals(name)) list.add(m);
        }
        return list;
    }
}