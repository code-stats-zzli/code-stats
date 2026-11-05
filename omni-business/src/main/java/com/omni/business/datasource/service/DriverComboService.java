package com.omni.business.datasource.service;

import com.omni.common.datasource.driver.DriverJar;
import com.omni.common.util.DriverClassNameResolver;
import com.omni.common.util.DriverFileNameParser;
import com.omni.business.common.util.DriverResourceScanner;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Service;

import java.util.*;

/**
 * 连接管理下拉数据服务：版本 + 驱动类，并建立版本 → 驱动类的关联映射。
 *
 * <p>返回结构：</p>
 * <pre>
 * {
 *   "success": true,
 *   "dbType": "mysql",
 *   "versions": ["8.0.33","8.4","8.0","8","5.7","5.1.47","5.1","5"],
 *   "driverClasses": ["com.mysql.cj.jdbc.Driver","com.mysql.jdbc.Driver"],
 *   "versionDriverMap": {
 *     "8.0.33": "com.mysql.cj.jdbc.Driver",
 *     "8.4":    "com.mysql.cj.jdbc.Driver",
 *     "8.0":    "com.mysql.cj.jdbc.Driver",
 *     "8":      "com.mysql.cj.jdbc.Driver",
 *     "5.7":    "com.mysql.jdbc.Driver",
 *     "5.1.47": "com.mysql.jdbc.Driver",
 *     "5.1":    "com.mysql.jdbc.Driver",
 *     "5":      "com.mysql.jdbc.Driver"
 *   }
 * }
 * </pre>
 *
 * <p>版本来源（预置主要大版本 + driver_jar 表 + resources/drivers 目录）：</p>
 * <ul>
 *   <li>mysql      → 预置 8, 5</li>
 *   <li>oracle     → 预置 23, 21, 19, 12, 11</li>
 *   <li>sqlserver  → 预置 12, 11, 10, 9, 8</li>
 *   <li>postgresql → 预置 42</li>
 * </ul>
 *
 * <p>驱动类推断规则：</p>
 * <ul>
 *   <li>优先用 driver_jar 表 / resources/drivers 目录里 jar 的解析类名</li>
 *   <li>否则按 dbType + 主版本推断默认驱动类</li>
 * </ul>
 */
// 只需改 getComboboxData 内部的三处推导：优先 overrideService.getOverride

@Service
public class DriverComboService {
    private static final Logger log = LoggerFactory.getLogger(DriverComboService.class);

    @Autowired private DriverJarService driverJarService;
    @Autowired private DriverClassOverrideService overrideService;

    private static final Map<String, List<String>> PRESET_VERSIONS = new HashMap<>();
    static {
        PRESET_VERSIONS.put("mysql",      Arrays.asList("8", "5"));
        PRESET_VERSIONS.put("oracle",     Arrays.asList("23", "21", "19", "12", "11"));
        PRESET_VERSIONS.put("sqlserver",  Arrays.asList("12", "11", "10", "9", "8"));
        PRESET_VERSIONS.put("postgresql", Arrays.asList("42"));
    }

    public Map<String, Object> getComboboxData(String dbType) throws Exception {
        String type = DriverFileNameParser.normalizeDbType(dbType);
        if (!DriverFileNameParser.isAllowedDbType(type)) {
            throw new IllegalArgumentException("不支持的数据库类型: " + dbType);
        }

        Map<String, String> versionDriverMap = new LinkedHashMap<>();

        // ① 预置
        for (String v : PRESET_VERSIONS.getOrDefault(type, Collections.emptyList())) {
            versionDriverMap.put(v, inferDriverClass(type, v));
        }

        // ② driver_jar 表
        try {
            List<DriverJar> jars = driverJarService.listByDbType(type);
            for (DriverJar j : jars) {
                String v = j.getVersion() == null ? null : j.getVersion().trim();
                if (v == null || v.isEmpty()) continue;
                if (versionDriverMap.containsKey(v)) continue;

                // ★ override 优先
                String cls = overrideService.getOverride(type, v);
                if (cls == null || cls.isEmpty()) {
                    cls = DriverClassNameResolver.fromFileName(j.getFileName());
                    if (cls == null && j.getJarData() != null && j.getJarData().length > 0) {
                        cls = DriverClassNameResolver.fromJarDirectory(j.getJarData());
                    }
                    if (cls == null) cls = inferDriverClass(type, v);
                }
                if (cls != null) versionDriverMap.put(v, cls);
            }
        } catch (Exception e) {
            log.warn("查询 driver_jar 失败", e);
        }

        // ③ resources/drivers 目录
        try {
            for (DriverResourceScanner.DriverFile df : DriverResourceScanner.scan()) {
                if (!df.dbType.equalsIgnoreCase(type)) continue;
                String v = df.version == null ? null : df.version.trim();
                if (v == null || v.isEmpty()) continue;
                if (versionDriverMap.containsKey(v)) continue;

                String cls = overrideService.getOverride(type, v);
                if (cls == null || cls.isEmpty()) {
                    cls = DriverClassNameResolver.fromFileName(df.originalFileName);
                    if (cls == null) {
                        try {
                            cls = DriverClassNameResolver.fromJarDirectory(
                                    java.nio.file.Files.readAllBytes(df.jarPath));
                        } catch (Exception ignored) {}
                    }
                    if (cls == null) cls = inferDriverClass(type, v);
                }
                if (cls != null) versionDriverMap.put(v, cls);
            }
        } catch (Exception e) {
            log.warn("扫描资源目录失败", e);
        }

        // ★ 最后：对所有 versions 用 override 强制覆盖（保证 override 最高优先级）
        for (Map.Entry<String, String> e : overrideService.getAll().entrySet()) {
            String[] parts = e.getKey().split(":");
            if (parts.length == 2 && parts[0].equalsIgnoreCase(type)) {
                if (versionDriverMap.containsKey(parts[1])) {
                    versionDriverMap.put(parts[1], e.getValue());
                }
            }
        }

        List<String> versions = new ArrayList<>(versionDriverMap.keySet());
        versions.sort((a, b) -> compareVersion(b, a));

        Set<String> classesSet = new LinkedHashSet<>();
        for (String v : versions) {
            String cls = versionDriverMap.get(v);
            if (cls != null) classesSet.add(cls);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("dbType", type);
        result.put("versions", versions);
        result.put("driverClasses", new ArrayList<>(classesSet));
        result.put("versionDriverMap", versionDriverMap);
        return result;
    }

    private static String inferDriverClass(String dbType, String version) {
        if (dbType == null) return null;
        switch (dbType.toLowerCase()) {
            case "mysql": {
                int major = 0;
                try { major = Integer.parseInt(version.trim().split("\\.")[0]); } catch (Exception ignored) {}
                return major >= 8 ? "com.mysql.cj.jdbc.Driver" : "com.mysql.jdbc.Driver";
            }
            case "oracle": return "oracle.jdbc.OracleDriver";
            case "postgresql":
            case "pg": return "org.postgresql.Driver";
            case "sqlserver": return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            default: return null;
        }
    }

    private static int compareVersion(String v1, String v2) {
        String[] p1 = (v1 == null ? "" : v1).split("\\.");
        String[] p2 = (v2 == null ? "" : v2).split("\\.");
        int n = Math.max(p1.length, p2.length);
        for (int i = 0; i < n; i++) {
            long x = i < p1.length ? parseLongSafe(p1[i]) : 0;
            long y = i < p2.length ? parseLongSafe(p2[i]) : 0;
            if (x != y) return x < y ? -1 : 1;
        }
        return 0;
    }
    private static long parseLongSafe(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0; }
    }
}