package com.omni.business.assistance.systemmonitor.component.impl;

import com.omni.business.assistance.systemmonitor.component.AbstractSystemComponent;
import com.omni.business.assistance.systemmonitor.model.MonitorItemInfo;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

public class CpuComponent extends AbstractSystemComponent {

    @Override public String getId() { return "cpu"; }
    @Override public String getName() { return "CPU"; }
    @Override public String getCategory() { return "HARDWARE"; }

    @Override
    public MonitorItemInfo getItemInfo() {
        MonitorItemInfo info = new MonitorItemInfo(getId(), getName(), getCategory());
        info.icon("🧠");
        info.desc("中央处理器实时负载");

        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

        // 核心主要信息
        info.putCore("cores", osBean.getAvailableProcessors());
        info.putCore("arch", osBean.getArch());
        info.putCore("osName", osBean.getName());
        info.putCore("osVersion", osBean.getVersion());
        info.putCore("systemLoadAverage", osBean.getSystemLoadAverage());

        // 运行时信息：使用反射兼容不同 JDK
        try {
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean sun =
                        (com.sun.management.OperatingSystemMXBean) osBean;
                double cpuLoad = sun.getCpuLoad();
                double processLoad = sun.getProcessCpuLoad();
                info.putRuntime("cpuUsagePercent", Math.round(cpuLoad * 10000) / 100.0);
                info.putRuntime("processCpuUsagePercent", Math.round(processLoad * 10000) / 100.0);
                info.putRuntime("usedPercentText",
                        String.format("%.1f%%", cpuLoad * 100));
                if (cpuLoad > 0.9) info.status("WARN");
            } else {
                double la = osBean.getSystemLoadAverage();
                if (la >= 0) {
                    double usage = la / osBean.getAvailableProcessors();
                    info.putRuntime("cpuUsagePercent", Math.round(usage * 10000) / 100.0);
                }
            }
        } catch (Throwable t) {
            log.warn("CPU 运行时信息获取失败: {}", t.getMessage());
        }
        return info;
    }
}