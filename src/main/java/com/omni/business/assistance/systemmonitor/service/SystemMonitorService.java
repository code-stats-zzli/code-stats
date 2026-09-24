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

    /** 摘要：所有组件的一行状态（供大屏概览） */
    public Map<String, Object> summary() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> cards = new ArrayList<>();

        for (SystemComponent c : engine.getComponentsMeta().stream()
                .map(m -> (SystemComponent) null).collect(Collectors.toList())) { /* noop */ }

        // 直接遍历所有详细信息
        for (MonitorItemInfo info : engine.getAllItemInfo()) {
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("id", info.getId());
            card.put("name", info.getName());
            card.put("icon", info.getIcon());
            card.put("category", info.getCategory());
            card.put("status", info.getStatus());
            // 抽取一个主要指标作为卡片主值
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

    /** 注册自定义组件（供业务扩展） */
    public void register(SystemComponent c) { engine.register(c); }
    public void unregister(String id) { engine.unregister(id); }
}