package com.omni.business.assistance.systemmonitor.component.impl;

import com.omni.business.assistance.systemmonitor.component.AbstractSystemComponent;
import com.omni.business.assistance.systemmonitor.model.MonitorItemInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DiskComponent extends AbstractSystemComponent {

    @Override public String getId() { return "disk"; }
    @Override public String getName() { return "磁盘"; }
    @Override public String getCategory() { return "HARDWARE"; }

    @Override
    public MonitorItemInfo getItemInfo() {
        MonitorItemInfo info = new MonitorItemInfo(getId(), getName(), getCategory());
        info.icon("💿");
        info.desc("各磁盘分区容量与使用率");

        List<Map<String, Object>> partitions = new ArrayList<>();
        File[] roots = File.listRoots();
        if (roots != null) {
            for (File root : roots) {
                long total = root.getTotalSpace();
                long free = root.getFreeSpace();
                long usable = root.getUsableSpace();
                if (total <= 0) continue;
                long used = total - free;
                double pct = (double) used / total * 100;

                Map<String, Object> p = new LinkedHashMap<>();
                p.put("path", root.getAbsolutePath());
                p.put("total", total);
                p.put("totalText", human(total));
                p.put("used", used);
                p.put("usedText", human(used));
                p.put("free", free);
                p.put("freeText", human(free));
                p.put("usable", usable);
                p.put("usableText", human(usable));
                p.put("usagePercent", Math.round(pct * 100) / 100.0);
                partitions.add(p);

                if (pct > 90) info.status("WARN");
            }
        }
        info.putCore("partitionCount", partitions.size());
        info.putRuntime("partitions", partitions);
        return info;
    }

    private String human(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / 1024.0 / 1024);
        return String.format("%.2f GB", bytes / 1024.0 / 1024 / 1024);
    }
}