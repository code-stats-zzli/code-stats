// core/model/CpuMemoryStats.java (新增 - 来自 ps 或 top)
package com.omni.framework.jstats.core.model;

public class CpuMemoryStats {
    private final double cpuUsagePercent;
    private final long memoryRssKb;   // 物理内存占用（KB）

    public CpuMemoryStats(double cpuUsagePercent, long memoryRssKb) {
        this.cpuUsagePercent = cpuUsagePercent;
        this.memoryRssKb = memoryRssKb;
    }
    public double getCpuUsagePercent() { return cpuUsagePercent; }
    public long getMemoryRssKb() { return memoryRssKb; }
}