package com.omni.business.assistance.systemmonitor.component.impl;

import com.omni.business.assistance.systemmonitor.component.AbstractSystemComponent;
import com.omni.business.assistance.systemmonitor.model.MonitorItemInfo;
import com.omni.business.assistance.systemmonitor.support.SystemMonitorLog;
import com.omni.common.util.ParseUtils;
import com.omni.framework.jstats.infrastructure.CommandExecutor;

import java.util.ArrayList;
import java.util.List;

/** ★ L1-07：exec 用 CommandExecutor */
public class GpuComponent extends AbstractSystemComponent {

    @Override public String getId() { return "gpu"; }
    @Override public String getName() { return "显卡"; }
    @Override public String getCategory() { return "HARDWARE"; }

    @Override
    public MonitorItemInfo getItemInfo() {
        MonitorItemInfo info = new MonitorItemInfo(getId(), getName(), getCategory());
        info.icon("🎮");
        info.desc("GPU 型号与显存信息");
        String os = System.getProperty("os.name").toLowerCase();
        List<String> lines = new ArrayList<>();
        try {
            if (os.contains("win")) {
                String out = new CommandExecutor(5).execute("wmic", "path", "win32_VideoController", "get",
                        "Name,AdapterRAM,DriverVersion,VideoProcessor", "/format:list");
                for (String l : out.split("\n")) lines.add(l);
            } else {
                String out = new CommandExecutor(5).execute("sh", "-c",
                        "lspci 2>/dev/null | grep -i vga || system_profiler SPDisplaysDataType 2>/dev/null");
                for (String l : out.split("\n")) lines.add(l);
            }
        } catch (Exception e) {
            SystemMonitorLog.warn(log, "显卡信息获取失败: {}", e.getMessage());
            return info;
        }
        List<String> names = new ArrayList<>();
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            int idx = t.indexOf('=');
            if (idx > 0) {
                String key = t.substring(0, idx).trim();
                String val = t.substring(idx + 1).trim();
                if ("Name".equalsIgnoreCase(key)) names.add(val);
                if ("AdapterRAM".equalsIgnoreCase(key)) {
                    try {
                        long bytes = Long.parseLong(val);
                        info.putCore("vramBytes", bytes);
                        info.putCore("vramText", ParseUtils.formatSize(bytes));
                    } catch (NumberFormatException ignored) {}
                }
                if ("DriverVersion".equalsIgnoreCase(key)) info.putCore("driverVersion", val);
            } else if (t.toLowerCase().contains("vga") || t.toLowerCase().contains("display")) {
                names.add(t);
            }
        }
        info.putCore("gpuCount", names.size());
        info.putRuntime("gpuNames", names);
        return info;
    }
}