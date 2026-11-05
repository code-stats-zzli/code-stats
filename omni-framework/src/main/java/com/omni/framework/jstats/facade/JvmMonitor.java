package com.omni.framework.jstats.facade;

import com.omni.framework.jstats.JStatsException;
import com.omni.framework.jstats.core.model.*;
import com.omni.framework.jstats.core.parser.*;
import com.omni.framework.jstats.infrastructure.CommandExecutor;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.management.ThreadInfo;
import java.util.ArrayList;
import java.util.List;

public class JvmMonitor {
    private static final Logger log = LoggerFactory.getLogger(JvmMonitor.class);
    private final CommandExecutor executor;

    public JvmMonitor() { this(10); }
    public JvmMonitor(long defaultTimeoutSeconds) {
        this.executor = new CommandExecutor(defaultTimeoutSeconds);
    }

    // ---------- 进程 ----------
    public List<JavaProcess> listProcesses() {
        try {
            String output = executor.execute("jps", "-l");
            return new JpsParser().parse(output);
        } catch (Exception e) {
            log.error("获取进程列表失败", e);
            return new ArrayList<>();
        }
    }

    // ---------- GC ----------
    public GcStatistics getGcStatistics(String pid) {
        try {
            String output = executor.execute("jstat", "-gc", pid);
            return new JstatGcParser().parse(output);
        } catch (Exception e) {
            log.error("获取 GC 统计失败, pid={}", pid, e);
            return null;
        }
    }

    // ---------- 类加载 ----------
    public ClassLoadingStats getClassLoadingStats(String pid) {
        try {
            String output = executor.execute("jstat", "-class", pid);
            return new JstatClassParser().parse(output);
        } catch (Exception e) {
            log.error("获取类加载统计失败, pid={}", pid, e);
            return null;
        }
    }

    // ---------- 线程（外部命令）----------
    public List<com.omni.framework.jstats.core.model.ThreadInfo> getThreadDump(String pid) {
        try {
            // ★ 3秒超时，防止卡死
            String output = executor.execute(3, "jstack", "-l", pid);
            return new JstackParser().parse(output);
        } catch (Exception e) {
            log.warn("jstack 超时或失败，将返回空线程信息", e);
            return new ArrayList<>();
        }
    }

    public List<com.omni.framework.jstats.core.model.ThreadInfo> getThreadDumpByJMX() {
        java.lang.management.ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        long[] ids = threadMXBean.getAllThreadIds();
        java.lang.management.ThreadInfo[] infos = threadMXBean.getThreadInfo(ids, 20);
        List<com.omni.framework.jstats.core.model.ThreadInfo> list = new ArrayList<>();
        for (java.lang.management.ThreadInfo info : infos) {
            if (info == null) continue;
            List<String> stack = new ArrayList<>();
            for (StackTraceElement ste : info.getStackTrace()) {
                stack.add(ste.toString());
            }
            list.add(new com.omni.framework.jstats.core.model.ThreadInfo(info.getThreadName(), info.getThreadId(),
                    info.getThreadState().name(), stack));
        }
        return list;
    }
    // ---------- 堆直方图 ----------
    public HeapHistogram getHeapHistogram(String pid) {
        try {
            String output = executor.execute("jmap", "-histo", pid);
            return new JmapHistoParser().parse(output);
        } catch (Exception e) {
            log.error("获取堆直方图失败, pid={}", pid, e);
            return null;
        }
    }

    // ---------- JVM 参数 ----------
    public List<JvmOption> getJvmOptions(String pid) {
        try {
            String output = executor.execute("jinfo", pid);
            return new JinfoParser().parse(output);
        } catch (Exception e) {
            log.error("获取 JVM 参数失败, pid={}", pid, e);
            return new ArrayList<>();
        }
    }

    // ---------- CPU/内存 ----------
    public CpuMemoryStats getCpuMemoryStats(String pid) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) return null;
            String output = executor.execute("ps", "-p", pid, "-o", "pid,%cpu,%mem,rss");
            return new PsParser().parse(output);
        } catch (Exception e) {
            log.error("获取 CPU/内存失败, pid={}", pid, e);
            return null;
        }
    }

    // ---------- 综合采集 ----------
    public MonitorSummary getFullMonitorData(String pid) {
        MonitorSummary summary = new MonitorSummary();
        summary.setPid(pid);

        try { summary.setGcStatistics(getGcStatistics(pid)); } catch (Exception e) { log.warn("GC采集失败", e); }
        try { summary.setClassLoadingStats(getClassLoadingStats(pid)); } catch (Exception e) { log.warn("类加载采集失败", e); }
        try {
            String currentPid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
            if (currentPid.equals(pid)) {
                summary.setThreadInfos(getThreadDumpByJMX());
            } else {
                summary.setThreadInfos(getThreadDump(pid));
            }
        } catch (Exception e) {
            log.warn("线程采集失败", e);
            summary.setThreadInfos(new ArrayList<>());
        }
        try { summary.setHeapHistogram(getHeapHistogram(pid)); } catch (Exception e) { log.warn("堆直方图采集失败", e); }
        try { summary.setJvmOptions(getJvmOptions(pid)); } catch (Exception e) { log.warn("JVM参数采集失败", e); }
        try { summary.setCpuMemoryStats(getCpuMemoryStats(pid)); } catch (Exception e) { log.warn("CPU/内存采集失败", e); }
        return summary;
    }

    // ---------- 聚合数据容器 ----------
    public static class MonitorSummary {
        private String pid;
        private GcStatistics gcStatistics;
        private ClassLoadingStats classLoadingStats;
        private List<com.omni.framework.jstats.core.model.ThreadInfo> threadInfos;
        private HeapHistogram heapHistogram;
        private List<JvmOption> jvmOptions;
        private CpuMemoryStats cpuMemoryStats;

        public String getPid() { return pid; }
        public void setPid(String pid) { this.pid = pid; }
        public GcStatistics getGcStatistics() { return gcStatistics; }
        public void setGcStatistics(GcStatistics gcStatistics) { this.gcStatistics = gcStatistics; }
        public ClassLoadingStats getClassLoadingStats() { return classLoadingStats; }
        public void setClassLoadingStats(ClassLoadingStats classLoadingStats) { this.classLoadingStats = classLoadingStats; }
        public List<com.omni.framework.jstats.core.model.ThreadInfo> getThreadInfos() { return threadInfos; }
        public void setThreadInfos(List<com.omni.framework.jstats.core.model.ThreadInfo> threadInfos) { this.threadInfos = threadInfos; }
        public HeapHistogram getHeapHistogram() { return heapHistogram; }
        public void setHeapHistogram(HeapHistogram heapHistogram) { this.heapHistogram = heapHistogram; }
        public List<JvmOption> getJvmOptions() { return jvmOptions; }
        public void setJvmOptions(List<JvmOption> jvmOptions) { this.jvmOptions = jvmOptions; }
        public CpuMemoryStats getCpuMemoryStats() { return cpuMemoryStats; }
        public void setCpuMemoryStats(CpuMemoryStats cpuMemoryStats) { this.cpuMemoryStats = cpuMemoryStats; }
    }
}