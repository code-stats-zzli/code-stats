package com.omni.business.assistance.systemmonitor.component.impl;

import com.omni.business.assistance.systemmonitor.component.AbstractSystemComponent;
import com.omni.business.assistance.systemmonitor.model.MonitorItemInfo;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
                lines = exec("wmic", "path", "win32_SoundDevice", "get", "Name,Status", "/format:list");
            } else {
                lines = exec("sh", "-c", "cat /proc/asound/cards 2>/dev/null || " +
                        "aplay -l 2>/dev/null || system_profiler SPAudioDataType 2>/dev/null");
            }
        } catch (Exception e) {
            log.warn("声卡信息获取失败: {}", e.getMessage());
        }

        List<String> names = new ArrayList<>();
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            int idx = t.indexOf('=');
            if (idx > 0 && "Name".equalsIgnoreCase(t.substring(0, idx).trim())) {
                names.add(t.substring(idx + 1).trim());
            } else if (!t.contains("=")) {
                names.add(t);
            }
        }
        info.putCore("deviceCount", names.size());
        info.putRuntime("devices", names);
        return info;
    }

    private List<String> exec(String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        List<String> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) out.add(line);
        }
        p.waitFor(5, TimeUnit.SECONDS);
        return out;
    }
}