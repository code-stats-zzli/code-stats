package com.omni.business.assistance.jstats.service;

import com.omni.business.assistance.jstats.model.JstatsMonitorData;
import com.omni.framework.jstats.facade.JvmMonitor;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Service;

import java.lang.management.ManagementFactory;

@Service
public class JstatsMonitorService {
    private static final Logger log = LoggerFactory.getLogger(JstatsMonitorService.class);
    private final JvmMonitor jvmMonitor = new JvmMonitor(30); // 超时30秒

    public JstatsMonitorData collectAllData() {
        String pid = getCurrentPid();
        JstatsMonitorData data = new JstatsMonitorData();
        try {
            data.setGc(jvmMonitor.getGcStatistics(pid));
            data.setClassLoading(jvmMonitor.getClassLoadingStats(pid));
            data.setThreadInfos(jvmMonitor.getThreadDumpByJMX());
            //todo
            //data.setHeapHistogram(jvmMonitor.getHeapHistogram(pid));
            //data.setJvmOptions(jvmMonitor.getJvmOptions(pid));
            //data.setCpuMemory(jvmMonitor.getCpuMemoryStats(pid));
        } catch (Exception e) {
            log.error("采集JVM监控数据失败", e);
            // 返回空数据或重新抛出，这里返回空对象，前端会显示默认值
        }
        return data;
    }

    private String getCurrentPid() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        return name.split("@")[0];
    }
}