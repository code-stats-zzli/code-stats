package com.omni.business.assistance.systemmonitor.component.impl;

import com.omni.business.assistance.systemmonitor.component.AbstractSystemComponent;
import com.omni.business.assistance.systemmonitor.model.MonitorItemInfo;

import java.lang.management.*;
import java.util.*;

/**
 * Java 运行时信息：
 *  - JVM 版本、厂商、启动参数、运行时长
 *  - 堆 / 非堆内存使用
 *  - GC 统计
 *  - 类加载数量
 *  - JIT 编译信息
 */
public class JavaRuntimeComponent extends AbstractSystemComponent {

    @Override public String getId() { return "java"; }
    @Override public String getName() { return "Java 运行时"; }
    @Override public String getCategory() { return "JAVA"; }

    @Override
    public MonitorItemInfo getItemInfo() {
        MonitorItemInfo info = new MonitorItemInfo(getId(), getName(), getCategory());
        info.icon("☕");
        info.desc("JVM 核心信息与运行时指标");

        RuntimeMXBean rt = ManagementFactory.getRuntimeMXBean();

        // ============ 核心主要信息 ============
        info.putCore("javaVersion", System.getProperty("java.version"));
        info.putCore("javaVendor", System.getProperty("java.vendor"));
        info.putCore("javaHome", System.getProperty("java.home"));
        info.putCore("jvmName", rt.getVmName());
        info.putCore("jvmVersion", rt.getVmVersion());
        info.putCore("jvmVendor", rt.getVmVendor());
        info.putCore("pid", rt.getName().split("@")[0]);
        info.putCore("startTime", new Date(rt.getStartTime()));
        info.putCore("uptimeText", formatDuration(rt.getUptime()));
        info.putCore("inputArguments", rt.getInputArguments());
        info.putCore("classPath", rt.getClassPath());
        info.putCore("libraryPath", rt.getLibraryPath());
        info.putCore("osName", System.getProperty("os.name"));
        info.putCore("osArch", System.getProperty("os.arch"));

        // ============ 运行时信息 ============

        // 堆内存
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = mem.getHeapMemoryUsage();
        MemoryUsage nonHeap = mem.getNonHeapMemoryUsage();

        Map<String, Object> heapMap = new LinkedHashMap<>();
        heapMap.put("init", human(heap.getInit()));
        heapMap.put("used", human(heap.getUsed()));
        heapMap.put("committed", human(heap.getCommitted()));
        heapMap.put("max", human(heap.getMax()));
        heapMap.put("usagePercent", heap.getMax() > 0
                ? Math.round((double) heap.getUsed() / heap.getMax() * 10000) / 100.0 : 0);
        info.putRuntime("heap", heapMap);

        Map<String, Object> nonHeapMap = new LinkedHashMap<>();
        nonHeapMap.put("used", human(nonHeap.getUsed()));
        nonHeapMap.put("committed", human(nonHeap.getCommitted()));
        info.putRuntime("nonHeap", nonHeapMap);

        // 内存池
        List<Map<String, Object>> pools = new ArrayList<>();
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", pool.getName());
            m.put("type", pool.getType().name());
            m.put("used", human(pool.getUsage().getUsed()));
            m.put("max", human(pool.getUsage().getMax()));
            pools.add(m);
        }
        info.putRuntime("memoryPools", pools);

        // GC
        List<Map<String, Object>> gcList = new ArrayList<>();
        long totalGcCount = 0, totalGcTime = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", gc.getName());
            m.put("count", gc.getCollectionCount());
            m.put("timeMs", gc.getCollectionTime());
            gcList.add(m);
            totalGcCount += gc.getCollectionCount();
            totalGcTime += gc.getCollectionTime();
        }
        info.putRuntime("gc", gcList);
        info.putRuntime("totalGcCount", totalGcCount);
        info.putRuntime("totalGcTimeMs", totalGcTime);

        // 类加载
        ClassLoadingMXBean cl = ManagementFactory.getClassLoadingMXBean();
        info.putRuntime("loadedClassCount", cl.getLoadedClassCount());
        info.putRuntime("totalLoadedClassCount", cl.getTotalLoadedClassCount());
        info.putRuntime("unloadedClassCount", cl.getUnloadedClassCount());

        // JIT
        CompilationMXBean comp = ManagementFactory.getCompilationMXBean();
        if (comp != null) {
            info.putRuntime("jitName", comp.getName());
            info.putRuntime("jitTotalTimeMs", comp.getTotalCompilationTime());
        }

        // 判断堆是否吃紧
        if (heap.getMax() > 0) {
            double pct = (double) heap.getUsed() / heap.getMax();
            if (pct > 0.85) info.status("WARN");
        }

        return info;
    }

    private String human(long bytes) {
        if (bytes < 0) return "N/A";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / 1024.0 / 1024);
        return String.format("%.2f GB", bytes / 1024.0 / 1024 / 1024);
    }

    private String formatDuration(long ms) {
        long s = ms / 1000;
        long d = s / 86400; s %= 86400;
        long h = s / 3600;  s %= 3600;
        long m = s / 60;    s %= 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("天 ");
        if (h > 0 || sb.length() > 0) sb.append(h).append("时 ");
        sb.append(m).append("分 ").append(s).append("秒");
        return sb.toString();
    }
}