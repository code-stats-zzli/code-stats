package com.omni.framework.jstats.core.model;

public class ClassLoadingStats {
    private final long loadedCount;
    private final long unloadedCount;
    private final long totalLoadedCount;

    public ClassLoadingStats(long loadedCount, long unloadedCount, long totalLoadedCount) {
        this.loadedCount = loadedCount;
        this.unloadedCount = unloadedCount;
        this.totalLoadedCount = totalLoadedCount;
    }
    public long getLoadedCount() { return loadedCount; }
    public long getUnloadedCount() { return unloadedCount; }
    public long getTotalLoadedCount() { return totalLoadedCount; }
}