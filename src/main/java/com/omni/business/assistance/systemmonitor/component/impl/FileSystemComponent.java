package com.omni.business.assistance.systemmonitor.component.impl;

import com.omni.business.assistance.systemmonitor.component.AbstractSystemComponent;
import com.omni.business.assistance.systemmonitor.model.MonitorItemInfo;

import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FileSystemComponent extends AbstractSystemComponent {

    @Override public String getId() { return "filesystem"; }
    @Override public String getName() { return "文件系统"; }
    @Override public String getCategory() { return "OS"; }

    @Override
    public MonitorItemInfo getItemInfo() {
        MonitorItemInfo info = new MonitorItemInfo(getId(), getName(), getCategory());
        info.icon("📁");
        info.desc("文件系统类型与挂载点");

        List<Map<String, Object>> stores = new ArrayList<>();
        try {
            for (FileStore store : FileSystems.getDefault().getFileStores()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", store.name());
                m.put("type", store.type());
                m.put("readOnly", store.isReadOnly());
                try {
                    m.put("total", store.getTotalSpace());
                    m.put("usable", store.getUsableSpace());
                    m.put("unallocated", store.getUnallocatedSpace());
                } catch (Exception ignored) {}
                stores.add(m);
            }
        } catch (Exception e) {
            log.warn("文件系统采集失败", e);
        }
        info.putCore("fileSeparator", java.io.File.separator);
        info.putCore("pathSeparator", java.io.File.pathSeparator);
        info.putCore("workDir", System.getProperty("user.dir"));
        info.putCore("tmpDir", System.getProperty("java.io.tmpdir"));
        info.putRuntime("stores", stores);
        return info;
    }
}