package com.omni.business.assistance.systemmonitor.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 组件信息模型：
 *  - coreInfo    : 核心主要信息（静态，如型号、核心数、厂商）
 *  - runtimeInfo : 运行时信息（动态，如使用率、剩余、读写速率）
 */
public class MonitorItemInfo {
    private String id;                 // 组件唯一ID，如 "cpu"
    private String name;               // 显示名
    private String category;           // HARDWARE / OS / JAVA / RUNTIME
    private String icon;               // 前端图标
    private String description;        // 描述
    private String status = "OK";      // OK / WARN / ERROR
    private long timestamp;
    private Map<String, Object> coreInfo = new LinkedHashMap<>();
    private Map<String, Object> runtimeInfo = new LinkedHashMap<>();

    public MonitorItemInfo(String id, String name, String category) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.timestamp = System.currentTimeMillis();
    }

    public MonitorItemInfo putCore(String key, Object val) { coreInfo.put(key, val); return this; }
    public MonitorItemInfo putRuntime(String key, Object val) { runtimeInfo.put(key, val); return this; }
    public MonitorItemInfo status(String s) { this.status = s; return this; }
    public MonitorItemInfo icon(String i) { this.icon = i; return this; }
    public MonitorItemInfo desc(String d) { this.description = d; return this; }

    // getters / setters 省略，按项目习惯保留
    public String getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getIcon() { return icon; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public long getTimestamp() { return timestamp; }
    public Map<String, Object> getCoreInfo() { return coreInfo; }
    public Map<String, Object> getRuntimeInfo() { return runtimeInfo; }
}