package com.omni.business.datasource.service;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 驱动类映射覆盖服务。
 *
 * <p>存储位置：{@code {user.dir}/resources/drivers/driver-class-overrides.properties}</p>
 * <p>格式：{@code dbType:version=driverClass}，例：{@code mysql:8.0.33=com.mysql.cj.jdbc.Driver}</p>
 *
 * <p>优先级：本服务 override > jar 文件内推导 > 按 dbType 规则推断</p>
 */
@Service
public class DriverClassOverrideService {
    private static final Logger log = LoggerFactory.getLogger(DriverClassOverrideService.class);

    private static final String FILE_NAME = "driver-class-overrides.properties";
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private volatile boolean loaded = false;

    // ==================== 存储路径 ====================

    private Path getStorePath() {
        return Paths.get(System.getProperty("user.dir"), "resources", "drivers", FILE_NAME)
                .toAbsolutePath().normalize();
    }

    private static String makeKey(String dbType, String version) {
        if (dbType == null || version == null) return null;
        return dbType.trim().toLowerCase() + ":" + version.trim().toLowerCase();
    }

    // ==================== 加载/写入 ====================

    private void ensureLoaded() {
        if (loaded) return;
        synchronized (this) {
            if (loaded) return;
            Path p = getStorePath();
            if (Files.exists(p)) {
                try (InputStream is = Files.newInputStream(p)) {
                    Properties props = new Properties();
                    props.load(new InputStreamReader(is, StandardCharsets.UTF_8));
                    for (String name : props.stringPropertyNames()) {
                        cache.put(name.toLowerCase(), props.getProperty(name).trim());
                    }
                    log.info("加载驱动类映射覆盖: {} 条", cache.size());
                } catch (Exception e) {
                    log.warn("加载驱动类覆盖文件失败: {}", p, e);
                }
            }
            loaded = true;
        }
    }

    private void persist() {
        Path p = getStorePath();
        try {
            Files.createDirectories(p.getParent());
            Properties props = new Properties();
            props.putAll(cache);
            try (OutputStream os = Files.newOutputStream(p)) {
                props.store(new OutputStreamWriter(os, StandardCharsets.UTF_8),
                        "Driver class overrides (dbType:version=driverClass)");
            }
            log.info("驱动类映射覆盖已保存: {} 条, 文件: {}", cache.size(), p);
        } catch (Exception e) {
            log.error("保存驱动类覆盖文件失败: {}", p, e);
        }
    }

    // ==================== 对外 API ====================

    /** 获取 override，没有返回 null */
    public String getOverride(String dbType, String version) {
        ensureLoaded();
        String k = makeKey(dbType, version);
        return k == null ? null : cache.get(k);
    }

    /** 设置 override；传空则删除 */
    public void setOverride(String dbType, String version, String driverClass) {
        ensureLoaded();
        String k = makeKey(dbType, version);
        if (k == null) return;
        if (driverClass == null || driverClass.trim().isEmpty()) {
            cache.remove(k);
        } else {
            cache.put(k, driverClass.trim());
        }
        persist();
    }

    /** 移除 override */
    public void removeOverride(String dbType, String version) {
        ensureLoaded();
        String k = makeKey(dbType, version);
        if (k != null) {
            cache.remove(k);
            persist();
        }
    }

    /** 全量（只读） */
    public Map<String, String> getAll() {
        ensureLoaded();
        return Collections.unmodifiableMap(new LinkedHashMap<>(cache));
    }

    /** 版本号变了 → 迁移 key（oldVersion → newVersion） */
    public void renameKey(String dbType, String oldVersion, String newVersion) {
        ensureLoaded();
        String oldKey = makeKey(dbType, oldVersion);
        String newKey = makeKey(dbType, newVersion);
        if (oldKey == null || newKey == null || oldKey.equals(newKey)) return;
        String v = cache.remove(oldKey);
        if (v != null) {
            cache.put(newKey, v);
            persist();
        }
    }
}