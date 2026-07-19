package com.omni.business.assistance.jstats.model;

import com.omni.framework.jstats.core.model.*;
import java.util.List;

public class JstatsMonitorData {
    private GcStatistics gc;
    private ClassLoadingStats classLoading;
    private List<ThreadInfo> threadInfos;
    private HeapHistogram heapHistogram;
    private List<JvmOption> jvmOptions;
    private CpuMemoryStats cpuMemory;

    // getters and setters
    public GcStatistics getGc() { return gc; }
    public void setGc(GcStatistics gc) { this.gc = gc; }
    public ClassLoadingStats getClassLoading() { return classLoading; }
    public void setClassLoading(ClassLoadingStats classLoading) { this.classLoading = classLoading; }
    public List<ThreadInfo> getThreadInfos() { return threadInfos; }
    public void setThreadInfos(List<ThreadInfo> threadInfos) { this.threadInfos = threadInfos; }
    public HeapHistogram getHeapHistogram() { return heapHistogram; }
    public void setHeapHistogram(HeapHistogram heapHistogram) { this.heapHistogram = heapHistogram; }
    public List<JvmOption> getJvmOptions() { return jvmOptions; }
    public void setJvmOptions(List<JvmOption> jvmOptions) { this.jvmOptions = jvmOptions; }
    public CpuMemoryStats getCpuMemory() { return cpuMemory; }
    public void setCpuMemory(CpuMemoryStats cpuMemory) { this.cpuMemory = cpuMemory; }
}