package com.omni.framework.codestats.dependency.model;

import java.util.*;

public class DependencyGraph {
    private final Map<String, Set<String>> adjacencyMap = new HashMap<>();

    public void addDependency(String fromClass, String toClass) {
        // ★ 修复：防止 fromClass 或 toClass 为 null 导致 NPE
        if (fromClass == null || toClass == null) {
            // 可记录警告日志（此处简化处理，直接忽略）
            return;
        }
        if (fromClass.equals(toClass)) return;
        adjacencyMap.computeIfAbsent(fromClass, k -> new HashSet<>()).add(toClass);
    }

    public Set<String> getDependencies(String className) {
        return adjacencyMap.getOrDefault(className, Collections.emptySet());
    }

    public Set<String> getAllClasses() {
        return adjacencyMap.keySet();
    }

    public Map<String, Set<String>> getAdjacencyMap() {
        return Collections.unmodifiableMap(adjacencyMap);
    }

    public boolean isEmpty() { return adjacencyMap.isEmpty(); }

    // ========== 新增：直接依赖（出向） ==========
    public List<String> getDirectDependencies(String className) {
        return new ArrayList<>(getDependencies(className));
    }

    // ========== 新增：反向依赖（入向） ==========
    public List<String> getInverseDependencies(String className) {
        Set<String> inverse = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : adjacencyMap.entrySet()) {
            if (entry.getValue().contains(className)) {
                inverse.add(entry.getKey());
            }
        }
        return new ArrayList<>(inverse);
    }

    // ========== 新增：统计最大出向依赖及对应的类 ==========
    public Map.Entry<Integer, List<String>> getMaxOutgoingDependencies() {
        int max = 0;
        List<String> maxClasses = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : adjacencyMap.entrySet()) {
            int size = entry.getValue().size();
            if (size > max) {
                max = size;
                maxClasses.clear();
                maxClasses.add(entry.getKey());
            } else if (size == max && size > 0) {
                maxClasses.add(entry.getKey());
            }
        }
        return new AbstractMap.SimpleEntry<>(max, maxClasses);
    }

    // ========== 循环依赖检测（Tarjan 算法） ==========
    public List<Set<String>> detectCycles() {
        Map<String, Integer> index = new HashMap<>();
        Map<String, Integer> lowlink = new HashMap<>();
        Stack<String> stack = new Stack<>();
        Set<String> onStack = new HashSet<>();
        List<Set<String>> cycles = new ArrayList<>();
        int[] idx = {0};

        for (String node : adjacencyMap.keySet()) {
            if (!index.containsKey(node)) {
                strongConnect(node, index, lowlink, stack, onStack, cycles, idx);
            }
        }

        cycles.removeIf(cycle -> cycle.size() <= 1);
        return cycles;
    }

    private void strongConnect(String v,
                               Map<String, Integer> index,
                               Map<String, Integer> lowlink,
                               Stack<String> stack,
                               Set<String> onStack,
                               List<Set<String>> cycles,
                               int[] idx) {
        index.put(v, idx[0]);
        lowlink.put(v, idx[0]);
        idx[0]++;
        stack.push(v);
        onStack.add(v);

        for (String w : getDependencies(v)) {
            if (!index.containsKey(w)) {
                strongConnect(w, index, lowlink, stack, onStack, cycles, idx);
                lowlink.put(v, Math.min(lowlink.get(v), lowlink.get(w)));
            } else if (onStack.contains(w)) {
                lowlink.put(v, Math.min(lowlink.get(v), index.get(w)));
            }
        }

        if (lowlink.get(v).equals(index.get(v))) {
            Set<String> component = new LinkedHashSet<>();
            String w;
            do {
                w = stack.pop();
                onStack.remove(w);
                component.add(w);
            } while (!w.equals(v));
            cycles.add(component);
        }
    }
}