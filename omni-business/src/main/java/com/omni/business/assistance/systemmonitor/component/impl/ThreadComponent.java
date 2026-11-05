package com.omni.business.assistance.systemmonitor.component.impl;

import com.omni.business.assistance.systemmonitor.component.AbstractSystemComponent;
import com.omni.business.assistance.systemmonitor.model.MonitorItemInfo;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.*;

public class ThreadComponent extends AbstractSystemComponent {

    @Override public String getId() { return "thread"; }
    @Override public String getName() { return "线程"; }
    @Override public String getCategory() { return "RUNTIME"; }

    @Override
    public MonitorItemInfo getItemInfo() {
        MonitorItemInfo info = new MonitorItemInfo(getId(), getName(), getCategory());
        info.icon("🧵");
        info.desc("JVM 线程状态分布");

        ThreadMXBean tb = ManagementFactory.getThreadMXBean();
        int threadCount = tb.getThreadCount();
        int peakCount = tb.getPeakThreadCount();
        int daemonCount = tb.getDaemonThreadCount();
        long totalStarted = tb.getTotalStartedThreadCount();

        info.putCore("peakThreadCount", peakCount);
        info.putCore("totalStartedThreadCount", totalStarted);
        info.putCore("daemonThreadCount", daemonCount);
        info.putRuntime("threadCount", threadCount);

        // 状态统计
        ThreadInfo[] infos = tb.getThreadInfo(tb.getAllThreadIds(), 0);
        Map<String, Integer> stateCount = new LinkedHashMap<>();
        for (ThreadInfo ti : infos) {
            if (ti == null) continue;
            String s = ti.getThreadState().name();
            stateCount.merge(s, 1, Integer::sum);
        }
        info.putRuntime("stateDistribution", stateCount);

        // 死锁检测
        long[] deadlocked = tb.findDeadlockedThreads();
        info.putRuntime("deadlockedCount", deadlocked == null ? 0 : deadlocked.length);
        if (deadlocked != null && deadlocked.length > 0) info.status("ERROR");

        return info;
    }
}