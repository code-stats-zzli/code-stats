package com.omni.business.assistance.systemmonitor.component.impl;

import com.omni.business.assistance.systemmonitor.component.AbstractSystemComponent;
import com.omni.business.assistance.systemmonitor.model.MonitorItemInfo;

import java.util.*;
import java.util.stream.Collectors;

public class ProcessComponent extends AbstractSystemComponent {

    @Override public String getId() { return "process"; }
    @Override public String getName() { return "进程"; }
    @Override public String getCategory() { return "RUNTIME"; }
    @Override public long getRefreshInterval() { return 2000L; }

    @Override
    public MonitorItemInfo getItemInfo() {
        MonitorItemInfo info = new MonitorItemInfo(getId(), getName(), getCategory());
        info.icon("⚙️");
        info.desc("系统进程列表与资源占用");

        long currentPid = ProcessHandle.current().pid();
        List<Map<String, Object>> processes = ProcessHandle.allProcesses()
                .limit(200)
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("pid", p.pid());
                    m.put("command", p.info().command().orElse(""));
                    m.put("user", p.info().user().orElse(""));
                    m.put("cpuDuration", p.info().totalCpuDuration().map(d -> d.toMillis()).orElse(0L));
                    m.put("startInstant", p.info().startInstant().map(String::valueOf).orElse(""));
                    m.put("alive", p.isAlive());
                    m.put("current", p.pid() == currentPid);
                    return m;
                })
                .sorted((a, b) -> Long.compare(
                        ((Number) b.get("cpuDuration")).longValue(),
                        ((Number) a.get("cpuDuration")).longValue()))
                .limit(50)
                .collect(Collectors.toList());

        long total = ProcessHandle.allProcesses().count();
        info.putRuntime("totalProcesses", total);
        info.putRuntime("topProcesses", processes);
        info.putRuntime("currentPid", currentPid);
        return info;
    }
}