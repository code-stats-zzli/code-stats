package com.omni.framework.codestats.dependency.model;

import java.util.*;

public class DependencyResult {
    private final Map<String, ClassInfo> classInfoMap;
    private final DependencyGraph graph;
    private final long analysisTimeMs;
    private final int totalClasses;
    private final double averageOutgoingDependencies;
    private final int maxOutgoingDependencies;
    private final List<String> maxOutgoingClasses;
    private List<Set<String>> cycleGroups;   // Day 4 填充

    public DependencyResult(Map<String, ClassInfo> classInfoMap, DependencyGraph graph, long analysisTimeMs) {
        this.classInfoMap = Collections.unmodifiableMap(classInfoMap);
        this.graph = graph;
        this.analysisTimeMs = analysisTimeMs;
        this.totalClasses = classInfoMap.size();

        long sum = 0;
        for (String cls : graph.getAllClasses()) {
            sum += graph.getDependencies(cls).size();
        }
        this.averageOutgoingDependencies = totalClasses == 0 ? 0.0 : (double) sum / totalClasses;

        Map.Entry<Integer, List<String>> maxEntry = graph.getMaxOutgoingDependencies();
        this.maxOutgoingDependencies = maxEntry.getKey();
        this.maxOutgoingClasses = maxEntry.getValue();

        this.cycleGroups = Collections.emptyList(); // 默认空
    }

    // Getters
    public Map<String, ClassInfo> getClassInfoMap() { return classInfoMap; }
    public DependencyGraph getGraph() { return graph; }
    public long getAnalysisTimeMs() { return analysisTimeMs; }
    public int getTotalClasses() { return totalClasses; }
    public double getAverageOutgoingDependencies() { return averageOutgoingDependencies; }
    public int getMaxOutgoingDependencies() { return maxOutgoingDependencies; }
    public List<String> getMaxOutgoingClasses() { return maxOutgoingClasses; }
    public List<Set<String>> getCycleGroups() { return cycleGroups; }
    public void setCycleGroups(List<Set<String>> cycleGroups) { this.cycleGroups = cycleGroups; }

    @Override
    public String toString() {
        return String.format("DependencyResult{classes=%d, avgDeps=%.2f, maxDeps=%d, time=%dms, cycles=%d}",
                totalClasses, averageOutgoingDependencies, maxOutgoingDependencies, analysisTimeMs, cycleGroups.size());
    }
}