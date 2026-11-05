package com.omni.business.datasource.service;

import com.omni.common.datasource.driver.DriverJar;
import com.omni.common.util.DriverClassNameResolver;
import com.omni.common.util.DriverFileNameParser;
import com.omni.business.common.util.DriverResourceScanner;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 驱动条目合并视图（driver_jar 表 + resources/drivers 目录 → 统一列表）。
 */
@Service
public class DriverEntryService {
    private static final Logger log = LoggerFactory.getLogger(DriverEntryService.class);

    @Autowired
    private DriverJarService driverJarService;

    @Autowired
    private DriverClassOverrideService overrideService;

    // ==================== 列表 ====================

    public List<Map<String, Object>> listEntries() throws Exception {
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();

        // ① driver_jar 表
        try {
            List<DriverJar> jars = driverJarService.listAll();
            for (DriverJar j : jars) {
                String type = DriverFileNameParser.normalizeDbType(j.getDbType());
                if (type == null) continue;
                String version = j.getVersion();
                if (version == null || version.trim().isEmpty()) continue;
                String key = type + ":" + version.toLowerCase();

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("key", key);
                item.put("dbType", type);
                item.put("version", version);
                item.put("fileName", j.getFileName());
                item.put("source", "db");
                item.put("id", j.getId());
                item.put("size", j.getJarData() != null ? j.getJarData().length : 0);
                item.put("createTime", j.getCreateTime() != null ? j.getCreateTime().getTime() : 0);
                // 驱动类：override 优先，其次文件名推导，再其次按规则
                item.put("driverClass", resolveDriverClass(type, version, j.getFileName(),
                        j.getJarData(), true));
                merged.put(key, item);
            }
        } catch (Exception e) {
            log.warn("读取 driver_jar 表失败", e);
        }

        // ② resources/drivers 目录
        try {
            for (DriverResourceScanner.DriverFile df : DriverResourceScanner.scan()) {
                String type = df.dbType;
                String version = df.version;
                if (type == null || version == null || version.trim().isEmpty()) continue;
                String key = type + ":" + version.toLowerCase();

                if (merged.containsKey(key)) {
                    // 两边都有 → source=both，补 jarPath
                    Map<String, Object> item = merged.get(key);
                    item.put("source", "both");
                    item.put("jarPath", df.jarPath.toString());
                    // 驱动类：override > jar 推导 > 规则；优先保留已有的
                    if (item.get("driverClass") == null) {
                        item.put("driverClass", resolveDriverClass(type, version, df.originalFileName,
                                readBytes(df.jarPath), false));
                    }
                } else {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("key", key);
                    item.put("dbType", type);
                    item.put("version", version);
                    item.put("fileName", df.originalFileName);
                    item.put("source", "dir");
                    item.put("jarPath", df.jarPath.toString());
                    try { item.put("size", Files.size(df.jarPath)); } catch (Exception ignored) {}
                    item.put("driverClass", resolveDriverClass(type, version, df.originalFileName,
                            readBytes(df.jarPath), false));
                    merged.put(key, item);
                }
            }
        } catch (Exception e) {
            log.warn("扫描资源目录失败", e);
        }

        List<Map<String, Object>> list = new ArrayList<>(merged.values());
        list.sort((a, b) -> {
            String t1 = (String) a.get("dbType"), t2 = (String) b.get("dbType");
            if (!t1.equals(t2)) return t1.compareTo(t2);
            return compareVersion((String) b.get("version"), (String) a.get("version"));
        });
        return list;
    }

    private byte[] readBytes(Path p) {
        try { return Files.readAllBytes(p); } catch (Exception e) { return null; }
    }

    private String resolveDriverClass(String dbType, String version, String fileName,
                                      byte[] jarData, boolean preferField) {
        // ① override
        String override = overrideService.getOverride(dbType, version);
        if (override != null && !override.isEmpty()) return override;

        // ② 文件名
        String cls = DriverClassNameResolver.fromFileName(fileName);
        if (cls != null) return cls;

        // ③ jar 目录
        if (jarData != null && jarData.length > 0) {
            cls = DriverClassNameResolver.fromJarDirectory(jarData);
            if (cls != null) return cls;
        }

        // ④ 按规则
        return inferDefaultDriverClass(dbType, version);
    }

    private String inferDefaultDriverClass(String dbType, String version) {
        if (dbType == null) return null;
        switch (dbType.toLowerCase()) {
            case "mysql": {
                int major = parseInt(version);
                return major >= 8 ? "com.mysql.cj.jdbc.Driver" : "com.mysql.jdbc.Driver";
            }
            case "oracle": return "oracle.jdbc.OracleDriver";
            case "postgresql":
            case "pg": return "org.postgresql.Driver";
            case "sqlserver": return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            default: return null;
        }
    }

    // ==================== 更新 ====================

    public Map<String, Object> updateEntry(String key, String dbType, String oldVersion,
                                           String newVersion, String driverClass) throws Exception {
        if (dbType == null || dbType.trim().isEmpty())
            throw new IllegalArgumentException("dbType 不能为空");
        if (newVersion == null || newVersion.trim().isEmpty())
            throw new IllegalArgumentException("新版本不能为空");
        if (newVersion.contains("_"))
            throw new IllegalArgumentException("版本不能包含下划线 _");

        String type = DriverFileNameParser.normalizeDbType(dbType);
        String oldV = oldVersion == null ? "" : oldVersion.trim();
        String newV = newVersion.trim();

        // ① 更新 driver_jar 表（如果有匹配记录）
        try {
            List<DriverJar> jars = driverJarService.listByDbType(type);
            for (DriverJar j : jars) {
                if (oldV.equalsIgnoreCase(j.getVersion())) {
                    DriverJar update = new DriverJar();
                    update.setId(j.getId());
                    update.setName(j.getName());
                    update.setDbType(type);
                    update.setVersion(newV);
                    driverJarService.update(update);
                    log.info("更新 driver_jar 记录: id={}, {} → {}", j.getId(), oldV, newV);
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("更新 driver_jar 表失败", e);
        }

        // ② 更新 driver class override
        if (driverClass != null) {
            overrideService.setOverride(type, newV, driverClass.trim());
            if (!oldV.equalsIgnoreCase(newV)) {
                overrideService.renameKey(type, oldV, newV);
                overrideService.setOverride(type, newV, driverClass.trim());
            }
        } else {
            overrideService.removeOverride(type, oldV);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("key", type + ":" + newV.toLowerCase());
        return out;
    }

    // ==================== 删除 ====================

    public void deleteEntry(String key, String dbType, String version) throws Exception {
        String type = DriverFileNameParser.normalizeDbType(dbType);
        String v = version == null ? "" : version.trim();

        // ① 删 driver_jar 表
        try {
            List<DriverJar> jars = driverJarService.listByDbType(type);
            for (DriverJar j : jars) {
                if (v.equalsIgnoreCase(j.getVersion())) {
                    driverJarService.delete(j.getId());
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("删除 driver_jar 失败", e);
        }

        // ② 删 override
        overrideService.removeOverride(type, v);

        // ③ 删目录文件
        try {
            for (DriverResourceScanner.DriverFile df : DriverResourceScanner.scan()) {
                if (df.dbType.equalsIgnoreCase(type) && df.version.equalsIgnoreCase(v)) {
                    Files.deleteIfExists(df.jarPath);
                    log.info("删除驱动目录文件: {}", df.jarPath);
                }
            }
        } catch (Exception e) {
            log.warn("删除目录文件失败", e);
        }
    }

    // ==================== 版本比较 ====================

    private static int compareVersion(String a, String b) {
        String[] pa = a == null ? new String[0] : a.split("\\.");
        String[] pb = b == null ? new String[0] : b.split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            long x = i < pa.length ? parseLong(pa[i]) : 0;
            long y = i < pb.length ? parseLong(pb[i]) : 0;
            if (x != y) return Long.compare(x, y);
        }
        return 0;
    }
    private static long parseLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0; }
    }
    private static int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }
}