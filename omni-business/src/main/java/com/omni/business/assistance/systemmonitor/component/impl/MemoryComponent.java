package com.omni.business.assistance.systemmonitor.component.impl;

import com.omni.business.assistance.systemmonitor.component.AbstractSystemComponent;
import com.omni.business.assistance.systemmonitor.model.MonitorItemInfo;
import com.omni.business.assistance.systemmonitor.support.SystemMonitorLog;

import java.lang.management.ManagementFactory;

public class MemoryComponent extends AbstractSystemComponent {

    @Override public String getId() { return "memory"; }
    @Override public String getName() { return "物理内存"; }
    @Override public String getCategory() { return "HARDWARE"; }

    @Override
    public MonitorItemInfo getItemInfo() {
        MonitorItemInfo info = new MonitorItemInfo(getId(), getName(), getCategory());
        info.icon("💾");
        info.desc("物理内存总量与使用情况");

        java.lang.management.OperatingSystemMXBean osBean =
                ManagementFactory.getOperatingSystemMXBean();

        try {
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean sun =
                        (com.sun.management.OperatingSystemMXBean) osBean;
                long total = sun.getTotalMemorySize();
                long free = sun.getFreeMemorySize();
                long used = total - free;
                double usage = total > 0 ? (double) used / total : 0;

                info.putCore("totalBytes", total);
                info.putCore("totalText", human(total));
                info.putRuntime("usedBytes", used);
                info.putRuntime("usedText", human(used));
                info.putRuntime("freeBytes", free);
                info.putRuntime("freeText", human(free));
                info.putRuntime("usagePercent", Math.round(usage * 10000) / 100.0);
                info.putRuntime("usageText", String.format("%.1f%%", usage * 100));
                if (usage > 0.85) info.status("WARN");
            }
        } catch (Throwable t) {
            SystemMonitorLog.warn(log, "物理内存信息获取失败: {}", t.getMessage());
        }
        return info;
    }

    private String human(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / 1024.0 / 1024);
        return String.format("%.2f GB", bytes / 1024.0 / 1024 / 1024);
    }
}