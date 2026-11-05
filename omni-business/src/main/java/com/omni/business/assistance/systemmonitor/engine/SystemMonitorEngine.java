package com.omni.business.assistance.systemmonitor.engine;

import com.omni.business.assistance.systemmonitor.component.SystemComponent;
import com.omni.business.assistance.systemmonitor.component.impl.*;
import com.omni.business.assistance.systemmonitor.model.MonitorItemInfo;
import com.omni.business.assistance.systemmonitor.support.SystemMonitorLog;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 系统实时监控引擎：
 *  - 持有所有注册的 SystemComponent
 *  - 对外提供统一的信息获取入口
 *  - 支持运行时动态注册/注销组件
 *
 * <p>日志说明：所有本类日志均通过 {@link SystemMonitorLog} 门面输出，
 * 默认关闭（systemmonitor.log.enabled=false），避免前端 2 秒轮询刷屏。</p>
 */
@Service
public class SystemMonitorEngine {
    private static final Logger log = LoggerFactory.getLogger(SystemMonitorEngine.class);

    /** 组件表：id -> 组件实例 */
    private static final Map<String, SystemComponent> components = new ConcurrentHashMap<>();

    /** 组件默认刷新间隔缓存 */
    private static final Map<String, Long> refreshIntervals = new ConcurrentHashMap<>();

    /** 全局信息缓存（可选，避免高频采集） */
    private static final Map<String, MonitorItemInfo> cache = new ConcurrentHashMap<>();
    private static long cacheTtlMs = 500L;

    // ==================== 注册 / 注销 ====================
    static {
        List<SystemComponent> defaultComponents = new ArrayList<>(20);
        defaultComponents.add(new CpuComponent());
        defaultComponents.add(new MemoryComponent());
        defaultComponents.add(new DiskComponent());
        defaultComponents.add(new FileSystemComponent());
        defaultComponents.add(new GpuComponent());
        defaultComponents.add(new SoundCardComponent());
        defaultComponents.add(new ProcessComponent());
        defaultComponents.add(new ThreadComponent());
        registerAll(defaultComponents);
    }

    /**
     * 注册组件
     */
    public static void register(SystemComponent component) {
        if (component == null || component.getId() == null) return;
        components.put(component.getId(), component);
        refreshIntervals.put(component.getId(), component.getRefreshInterval());
        SystemMonitorLog.info(log, "注册系统监控组件: {} [{}]",
                component.getId(), component.getName());
    }

    /**
     * 批量注册
     */
    public static void registerAll(Collection<? extends SystemComponent> list) {
        if (list != null) list.forEach(SystemMonitorEngine::register);
    }

    /**
     * 注销
     */
    public static void unregister(String id) {
        components.remove(id);
        refreshIntervals.remove(id);
        cache.remove(id);
        SystemMonitorLog.info(log, "注销系统监控组件: {}", id);
    }

    // ==================== 获取信息 ====================

    /** 获取所有组件的基础元数据（不采集运行时数据） */
    public List<Map<String, Object>> getComponentsMeta() {
        return components.values().stream()
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", c.getId());
                    m.put("name", c.getName());
                    m.put("category", c.getCategory());
                    m.put("available", c.isAvailable());
                    m.put("refreshInterval", c.getRefreshInterval());
                    return m;
                })
                .collect(Collectors.toList());
    }

    /** 获取指定组件的详细信息 */
    public MonitorItemInfo getItemInfo(String id) {
        SystemComponent c = components.get(id);
        if (c == null) return null;

        // 缓存优先
        long now = System.currentTimeMillis();
        MonitorItemInfo cached = cache.get(id);
        if (cached != null && now - cached.getTimestamp() < cacheTtlMs) {
            return cached;
        }

        try {
            MonitorItemInfo info = c.getItemInfo();
            cache.put(id, info);
            return info;
        } catch (Exception e) {
            // 默认关闭时这里不打印；开启后 level>=error 才打印
            SystemMonitorLog.error(log, "获取组件 [" + id + "] 信息失败", e);
            MonitorItemInfo err = new MonitorItemInfo(id, c.getName(), c.getCategory());
            err.status("ERROR");
            err.desc("采集失败: " + e.getMessage());
            return err;
        }
    }

    /** 获取所有组件的详细信息（一次采集全部） */
    public List<MonitorItemInfo> getAllItemInfo() {
        List<MonitorItemInfo> result = new ArrayList<>();
        for (SystemComponent c : components.values()) {
            result.add(getItemInfo(c.getId()));
        }
        return result;
    }

    /** 按分类过滤 */
    public List<MonitorItemInfo> getItemInfoByCategory(String category) {
        return components.values().stream()
                .filter(c -> category.equalsIgnoreCase(c.getCategory()))
                .map(c -> getItemInfo(c.getId()))
                .collect(Collectors.toList());
    }

    /** 是否有指定组件 */
    public boolean contains(String id) { return components.containsKey(id); }

    /** 组件数量 */
    public int size() { return components.size(); }

    /** 设置缓存 TTL */
    public void setCacheTtlMs(long ttl) { this.cacheTtlMs = ttl; }

    /** 清空缓存（下一次请求强制刷新） */
    public void clearCache() { cache.clear(); }
}