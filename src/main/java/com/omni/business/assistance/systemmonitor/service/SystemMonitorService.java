package com.omni.business.assistance.systemmonitor.service;

import com.omni.business.assistance.systemmonitor.component.SystemComponent;
import com.omni.business.assistance.systemmonitor.engine.SystemMonitorEngine;
import com.omni.business.assistance.systemmonitor.model.MonitorItemInfo;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SystemMonitorService {

    @Autowired
    private SystemMonitorEngine engine;

    /**
     * 摘要：所有组件的一行状态（供大屏概览）。
     * ★ 修复：删除原代码中无意义的空 for 循环（遍历空列表 + 空操作），保留真正的遍历逻辑。
     */
    public Map<String, Object> summary() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> cards = new ArrayList<>();

        for (MonitorItemInfo info : engine.getAllItemInfo()) {
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("id", info.getId());
            card.put("name", info.getName());
            card.put("icon", info.getIcon());
            card.put("category", info.getCategory());
            card.put("status", info.getStatus());
            card.put("primary", pickPrimary(info));
            cards.add(card);
        }
        result.put("cards", cards);
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    private Object pickPrimary(MonitorItemInfo info) {
        Map<String, Object> rt = info.getRuntimeInfo();
        for (String key : Arrays.asList("usageText", "usagePercent", "loadedClassCount", "threadCount")) {
            if (rt.containsKey(key)) return rt.get(key);
        }
        return "-";
    }

    public MonitorItemInfo getItemInfo(String id) { return engine.getItemInfo(id); }
    public List<MonitorItemInfo> getAllItemInfo() { return engine.getAllItemInfo(); }
    public List<MonitorItemInfo> getByCategory(String cat) { return engine.getItemInfoByCategory(cat); }

    public void register(SystemComponent c) { engine.register(c); }
    public void unregister(String id) { engine.unregister(id); }
}