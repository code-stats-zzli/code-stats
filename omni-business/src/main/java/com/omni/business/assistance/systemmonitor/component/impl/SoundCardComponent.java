package com.omni.business.assistance.systemmonitor.component.impl;

import com.omni.business.assistance.systemmonitor.component.AbstractSystemComponent;
import com.omni.business.assistance.systemmonitor.model.MonitorItemInfo;
import com.omni.business.assistance.systemmonitor.support.SystemMonitorLog;
import com.omni.framework.jstats.infrastructure.CommandExecutor;

import java.util.ArrayList;
import java.util.List;

/** ★ L1-07：exec 用 CommandExecutor */
public class SoundCardComponent extends AbstractSystemComponent {

    @Override public String getId() { return "soundcard"; }
    @Override public String getName() { return "声卡"; }
    @Override public String getCategory() { return "HARDWARE"; }

    @Override
    public MonitorItemInfo getItemInfo() {
        MonitorItemInfo info = new MonitorItemInfo(getId(), getName(), getCategory());
        info.icon("🔊");
        info.desc("音频设备信息");
        String os = System.getProperty("os.name").toLowerCase();
        List<String> lines = new ArrayList<>();
        try {
            if (os.contains("win")) {
                String out = new CommandExecutor(5).execute("wmic", "path", "win32_SoundDevice", "get", "Name,Status", "/format:list");
                for (String l : out.split("\n")) lines.add(l);
            } else {
                String out = new CommandExecutor(5).execute("sh", "-c",
                        "cat /proc/asound/cards 2>/dev/null || aplay -l 2>/dev/null || system_profiler SPAudioDataType 2>/dev/null");
                for (String l : out.split("\n")) lines.add(l);
            }
        } catch (Exception e) {
            SystemMonitorLog.warn(log, "声卡信息获取失败: {}", e.getMessage());
        }
        List<String> names = new ArrayList<>();
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            int idx = t.indexOf('=');
            if (idx > 0 && "Name".equalsIgnoreCase(t.substring(0, idx).trim())) names.add(t.substring(idx + 1).trim());
            else if (!t.contains("=")) names.add(t);
        }
        info.putCore("deviceCount", names.size());
        info.putRuntime("devices", names);
        return info;
    }
}