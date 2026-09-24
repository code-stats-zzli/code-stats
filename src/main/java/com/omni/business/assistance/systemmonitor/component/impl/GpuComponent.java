package com.omni.business.assistance.systemmonitor.component.impl;

import com.omni.business.assistance.systemmonitor.component.AbstractSystemComponent;
import com.omni.business.assistance.systemmonitor.model.MonitorItemInfo;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
                // Windows: wmic 查询
                lines = exec("wmic", "path", "win32_VideoController", "get",
                        "Name,AdapterRAM,DriverVersion,VideoProcessor", "/format:list");
            } else {
                // Linux/macOS: lspci 或 system_profiler
                lines = exec("sh", "-c", "lspci 2>/dev/null | grep -i vga || system_profiler SPDisplaysDataType 2>/dev/null");
            }
        } catch (Exception e) {
            log.warn("显卡信息获取失败: {}", e.getMessage());
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
                        info.putCore("vramText", String.format("%.2f GB", bytes / 1024.0 / 1024 / 1024));
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