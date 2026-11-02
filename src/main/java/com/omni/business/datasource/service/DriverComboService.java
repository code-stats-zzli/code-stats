package com.omni.business.datasource.service;

import com.omni.common.datasource.driver.DriverJar;
import com.omni.common.util.DriverClassNameResolver;
import com.omni.common.util.DriverFileNameParser;
import com.omni.common.util.DriverResourceScanner;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Service;

import java.util.*;

/**
 * 连接管理下拉数据服务（版本 + 驱动类）。
 *
 * <p>版本来源（去重 + 按版本号降序）：</p>
 *   <ol>
 *     <li><b>预置常见大/小版本</b>（每个类型至少 2~8 个，如 mysql 8.4 / 8.0 / 8 / 5.7 / 5.6 / 5.5 / 5.1 / 5）</li>
 *     <li><b>driver_jar 表</b>里的版本</li>
 *     <li><b>resources/drivers 目录</b>（递归扫描）里的版本</li>
 *   </ol>
 *
 * <p>驱动类来源（去重）：</p>
 *   <ol>
 *     <li>预置常见驱动类</li>
 *     <li>driver_jar 表中各 jar 文件名/内部包名推导</li>
 *     <li>resources/drivers 目录中各 jar 文件名/内部包名推导</li>
 *   </ol>
 */
@Service
public class DriverComboService {
    private static final Logger log = LoggerFactory.getLogger(DriverComboService.class);

    @Autowired
    private DriverJarService driverJarService;

    // ==================== 预置数据 ====================

    /** 预置常见版本（每个类型都提供多种） */
    private static final Map<String, List<String>> PRESET_VERSIONS = new HashMap<>();

    /** 预置常见驱动类 */
    private static final Map<String, List<String>> PRESET_DRIVER_CLASSES = new HashMap<>();

    static {
        // ==================== MySQL ====================
        // 8.x 与 5.x 至少各有一个；细分常见小版本
        PRESET_VERSIONS.put("mysql", Arrays.asList(
                "8.4", "8.0", "8",
                "5.7", "5.6", "5.5", "5.1", "5"
        ));
        PRESET_DRIVER_CLASSES.put("mysql", Arrays.asList(
                "com.mysql.cj.jdbc.Driver",   // 8.x
                "com.mysql.jdbc.Driver"       // 5.x
        ));

        // ==================== Oracle ====================
        PRESET_VERSIONS.put("oracle", Arrays.asList(
                "23", "21", "19", "12", "11"
        ));
        PRESET_DRIVER_CLASSES.put("oracle",
                Collections.singletonList("oracle.jdbc.OracleDriver"));

        // ==================== SQL Server ====================
        // 12=2014, 11=2012, 10=2008, 9=2005, 8=2000
        PRESET_VERSIONS.put("sqlserver", Arrays.asList(
                "12", "11", "10", "9", "8"
        ));
        PRESET_DRIVER_CLASSES.put("sqlserver",
                Collections.singletonList("com.microsoft.sqlserver.jdbc.SQLServerDriver"));

        // ==================== PostgreSQL ====================
        // 42.x 对应 PG JDBC 驱动主线
        PRESET_VERSIONS.put("postgresql", Arrays.asList(
                "42.7", "42.6", "42.5", "42.4", "42.3", "42.2", "42.1", "42"
        ));
        PRESET_DRIVER_CLASSES.put("postgresql",
                Collections.singletonList("org.postgresql.Driver"));
    }

    // ==================== 对外接口 ====================

    /**
     * 获取下拉数据（版本列表 + 驱动类列表）。
     * 返回结构：
     * <pre>
     * {
     *   "success": true,
     *   "dbType": "mysql",
     *   "versions": ["8.4","8.0","8.0.33","8","5.7","5.6","5.5","5.1","5.1.47","5"],  // 含目录/表来源
     *   "driverClasses": ["com.mysql.cj.jdbc.Driver","com.mysql.jdbc.Driver"]
     * }
     * </pre>
     */
    public Map<String, Object> getComboboxData(String dbType) throws Exception {
        String type = DriverFileNameParser.normalizeDbType(dbType);
        if (!DriverFileNameParser.isAllowedDbType(type)) {
            throw new IllegalArgumentException("不支持的数据库类型: " + dbType
                    + "（仅支持 " + String.join("/", DriverFileNameParser.ALLOWED_DB_TYPES) + "）");
        }

        // ==================== 版本 ====================
        Set<String> versionsSet = new LinkedHashSet<>();

        // ① 预置常见版本
        versionsSet.addAll(PRESET_VERSIONS.getOrDefault(type, Collections.emptyList()));

        // ② driver_jar 表里的版本
        try {
            List<DriverJar> jars = driverJarService.listByDbType(type);
            for (DriverJar j : jars) {
                if (j.getVersion() != null && !j.getVersion().trim().isEmpty()) {
                    versionsSet.add(j.getVersion().trim());
                }
            }
        } catch (Exception e) {
            log.warn("查询 driver_jar 表失败: dbType={}", type, e);
        }

        // ③ resources/drivers 目录里的版本（递归扫描）
        try {
            for (DriverResourceScanner.DriverFile df : DriverResourceScanner.scan()) {
                if (df.dbType.equalsIgnoreCase(type)
                        && df.version != null && !df.version.trim().isEmpty()) {
                    versionsSet.add(df.version.trim());
                }
            }
        } catch (Exception e) {
            log.warn("扫描资源目录失败: dbType={}", type, e);
        }

        // 去重后排序：按版本号降序（大版本在前）
        List<String> versions = new ArrayList<>(versionsSet);
        versions.sort((a, b) -> compareVersion(b, a));

        // ==================== 驱动类 ====================
        Set<String> classesSet = new LinkedHashSet<>();

        // ① 预置常见驱动类
        classesSet.addAll(PRESET_DRIVER_CLASSES.getOrDefault(type, Collections.emptyList()));

        // ② driver_jar 表推导
        try {
            List<DriverJar> jars = driverJarService.listByDbType(type);
            for (DriverJar j : jars) {
                String cls = DriverClassNameResolver.fromFileName(j.getFileName());
                if (cls == null && j.getJarData() != null && j.getJarData().length > 0) {
                    cls = DriverClassNameResolver.fromJarDirectory(j.getJarData());
                }
                if (cls != null) classesSet.add(cls);
            }
        } catch (Exception e) {
            log.warn("从 driver_jar 推导驱动类失败: dbType={}", type, e);
        }

        // ③ resources/drivers 目录推导
        try {
            for (DriverResourceScanner.DriverFile df : DriverResourceScanner.scan()) {
                if (!df.dbType.equalsIgnoreCase(type)) continue;
                String cls = DriverClassNameResolver.fromFileName(df.originalFileName);
                if (cls == null) {
                    try {
                        cls = DriverClassNameResolver.fromJarDirectory(
                                java.nio.file.Files.readAllBytes(df.jarPath));
                    } catch (Exception ignored) {}
                }
                if (cls != null) classesSet.add(cls);
            }
        } catch (Exception e) {
            log.warn("从资源目录推导驱动类失败: dbType={}", type, e);
        }

        List<String> driverClasses = new ArrayList<>(classesSet);

        // ==================== 返回 ====================
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("dbType", type);
        result.put("versions", versions);
        result.put("driverClasses", driverClasses);
        log.info("combobox 数据: dbType={}, versions={}, driverClasses={}",
                type, versions, driverClasses);
        return result;
    }

    // ==================== 版本号比较 ====================

    /**
     * 比较两个版本号（按点分段数字比较）。
     * 返回负数 a < b，0 a == b，正数 a > b。
     */
    private static int compareVersion(String v1, String v2) {
        if (v1 == null) v1 = "";
        if (v2 == null) v2 = "";
        String[] p1 = v1.split("\\.");
        String[] p2 = v2.split("\\.");
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