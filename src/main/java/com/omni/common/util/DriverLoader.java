package com.omni.common.util;

import com.omni.common.datasource.driver.DriverJar;
import com.omni.framework.jdbc.JdbcTemplate;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.sql.Driver;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 驱动加载器。
 *
 * <p>加载顺序（命中即返回，classpath 放最后兜底）：</p>
 *   <ol>
 *     <li><b>driver_jar 表</b>（数据库 blob，dbType+version 精确；无 version 取该 dbType 最新）</li>
 *     <li><b>resources/drivers 目录</b>（文件名 {dbType}_{version}_{fileName}.jar）</li>
 *     <li><b>classpath</b> 内置驱动（最后兜底）</li>
 *   </ol>
 *
 * <p>失败时统一抛 {@link DriverLoadException}，携带来源 / JAR 位置 / 期望类 / 候选类 / 修复建议，
 * 供上层 Controller 序列化到前端展示。</p>
 */
public class DriverLoader {
    private static final Logger log = LoggerFactory.getLogger(DriverLoader.class);
    private static final Map<String, Driver> driverCache = new ConcurrentHashMap<>();

    public static Driver getDriver(String dbType, String version) throws Exception {
        String type = DriverFileNameParser.normalizeDbType(dbType);
        if (!DriverFileNameParser.isAllowedDbType(type)) {
            throw new DriverLoadException(
                    "resolve_class", type, version, "none", null, null,
                    Collections.emptyList(),
                    "不支持的数据库类型: " + dbType
                            + "（仅支持 " + String.join("/", DriverFileNameParser.ALLOWED_DB_TYPES) + "）",
                    null);
        }

        String key = type + ":" + (version == null ? "" : version);
        Driver cached = driverCache.get(key);
        if (cached != null) return cached;

        // ---------- ① driver_jar 表 ----------
        DriverJar matched = findMatchedDriver(type, version);
        if (matched != null) {
            String className = DriverClassNameResolver.resolve(matched);
            if (className == null) {
                List<String> candidates = JarClassScanner.findDriverCandidates(matched.getJarData());
                String hint;
                if (!candidates.isEmpty()) {
                    hint = "无法从 JAR 文件名或内容推断驱动类名。\n"
                            + "JAR 内发现候选类：\n  • " + String.join("\n  • ", candidates)
                            + "\n请在数据源配置中将 driverClass 改为上述类之一。";
                } else {
                    hint = "无法从 JAR 文件名或内容推断驱动类名，且 JAR 内未发现任何可能的驱动类。\n"
                            + "该文件可能不是有效的数据库驱动包，请重新下载官方完整版驱动。";
                }
                throw new DriverLoadException(
                        "resolve_class", type, matched.getVersion(),
                        "driver_jar(表)",
                        "id=" + matched.getId() + ", file=" + matched.getFileName(),
                        null, candidates, hint, null);
            }
            return loadDriverFromJar(matched, className, key);
        }

        // ---------- ② resources/drivers 目录 ----------
        // 若目录里找到匹配文件，直接加载；失败抛 DriverLoadException（不静默回退）
        Driver dirDriver = DriverResourceScanner.loadDriver(type, version);
        if (dirDriver != null) {
            driverCache.put(key, dirDriver);
            return dirDriver;
        }

        // ---------- ③ classpath 兜底 ----------
        try {
            Driver d = loadDefaultDriver(type);
            driverCache.put(key, d);
            log.info("classpath 兜底加载驱动成功: dbType={}, class={}", type, d.getClass().getName());
            return d;
        } catch (ClassNotFoundException e) {
            log.info("classpath 也未找到驱动: dbType={}", type);
        }

        // ---------- ④ 全部失败 ----------
        String hint = "驱动加载失败。请按以下顺序排查：\n"
                + "1) 是否已在「系统管理 → 资源管理 → 驱动管理」上传 " + type + " 驱动 JAR？\n"
                + "2) 上传的 JAR 是否完整、是否是对应数据库的驱动包？\n"
                + "3) 若已上传但类名不匹配，请在数据源配置中手动指定 driverClass；\n"
                + "4) 或确认 classpath 中是否包含该数据库的驱动依赖。";
        throw new DriverLoadException(
                "not_found", type, version, "all", null, null,
                Collections.emptyList(), hint, null);
    }

    // ==================== ① driver_jar 表匹配 ====================

    private static DriverJar findMatchedDriver(String dbType, String version) {
        try {
            JdbcTemplate jt = new JdbcTemplate();
            if (version != null && !version.trim().isEmpty()) {
                List<DriverJar> exact = jt.queryForList(
                        "SELECT * FROM driver_jar " +
                                "WHERE LOWER(db_type)=LOWER(?) AND LOWER(version)=LOWER(?) LIMIT 1",
                        DriverJar.class, dbType, version);
                if (!exact.isEmpty()) return exact.get(0);
            }
            List<DriverJar> byType = jt.queryForList(
                    "SELECT * FROM driver_jar WHERE LOWER(db_type)=LOWER(?) " +
                            "ORDER BY create_time DESC LIMIT 1",
                    DriverJar.class, dbType);
            if (!byType.isEmpty()) return byType.get(0);
        } catch (Exception e) {
            log.debug("查询 driver_jar 失败", e);
        }
        return null;
    }

    /**
     * 从 driver_jar 表加载。失败时抛 {@link DriverLoadException}，携带候选类与修复建议。
     */
    private static Driver loadDriverFromJar(DriverJar driverJar, String driverClassName, String cacheKey) {
        ByteArrayClassLoader classLoader = new ByteArrayClassLoader(
                driverJar.getJarData(), Thread.currentThread().getContextClassLoader());

        String jarLocation = "id=" + driverJar.getId() + ", file=" + driverJar.getFileName();
        Class<?> clazz;
        try {
            clazz = classLoader.loadClass(driverClassName);
        } catch (ClassNotFoundException e) {
            List<String> candidates = JarClassScanner.findDriverCandidates(driverJar.getJarData());
            String hint = buildClassNotFoundHint(driverJar.getFileName(), driverClassName, candidates);
            throw new DriverLoadException(
                    "load_class", driverJar.getDbType(), driverJar.getVersion(),
                    "driver_jar(表)", jarLocation,
                    driverClassName, candidates, hint, e);
        } catch (LinkageError e) {
            List<String> candidates = JarClassScanner.findDriverCandidates(driverJar.getJarData());
            String hint = "驱动类 " + driverClassName + " 加载时依赖缺失：" + e.getMessage() + "\n"
                    + "该 JAR 可能不是完整驱动包（缺少依赖），请重新下载官方完整版驱动 JAR 后上传。";
            throw new DriverLoadException(
                    "linkage", driverJar.getDbType(), driverJar.getVersion(),
                    "driver_jar(表)", jarLocation,
                    driverClassName, candidates, hint, e);
        }

        if (!Driver.class.isAssignableFrom(clazz)) {
            String hint = "类 " + driverClassName + " 存在，但不是 java.sql.Driver 实现。\n"
                    + "该 JAR 可能不是数据库驱动包，请确认上传的文件类型。";
            throw new DriverLoadException(
                    "cast", driverJar.getDbType(), driverJar.getVersion(),
                    "driver_jar(表)", jarLocation,
                    driverClassName, Collections.emptyList(), hint, null);
        }

        try {
            Driver driver = (Driver) clazz.getDeclaredConstructor().newInstance();
            driverCache.put(cacheKey, driver);
            log.info("driver_jar 加载驱动成功: {} ({}), 类名: {}",
                    driverJar.getName(), driverJar.getId(), driverClassName);
            return driver;
        } catch (Exception e) {
            String hint = "驱动类 " + driverClassName + " 实例化失败：" + e.getMessage() + "\n"
                    + "请确认该类有无参构造函数，且不是抽象类。";
            throw new DriverLoadException(
                    "instantiate", driverJar.getDbType(), driverJar.getVersion(),
                    "driver_jar(表)", jarLocation,
                    driverClassName, Collections.emptyList(), hint, e);
        }
    }

    /** 拼装"类不存在"的修复建议 */
    private static String buildClassNotFoundHint(String jarFileName, String expectedClass,
                                                 List<String> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("驱动 JAR [").append(jarFileName).append("] 中不存在类 [")
                .append(expectedClass).append("]。\n");
        if (!candidates.isEmpty()) {
            sb.append("JAR 内发现以下可能的驱动类：\n");
            for (String c : candidates) sb.append("  • ").append(c).append("\n");
            sb.append("请在数据源配置中将 driverClass 改为上述类之一，或重新上传匹配的驱动 JAR。");
        } else {
            sb.append("JAR 内未发现任何可能的驱动类（类名以 Driver 结尾或包含 jdbc/driver 包路径）。\n")
                    .append("该文件可能不是有效的数据库驱动包，请从数据库官方或 Maven Central 重新下载完整驱动 JAR 后上传。");
        }
        return sb.toString();
    }

    // ==================== ③ classpath 兜底 ====================

    private static Driver loadDefaultDriver(String dbType) throws Exception {
        switch (dbType.toLowerCase()) {
            case "mysql":
                try {
                    return (Driver) Class.forName("com.mysql.cj.jdbc.Driver")
                            .getDeclaredConstructor().newInstance();
                } catch (ClassNotFoundException e) {
                    return (Driver) Class.forName("com.mysql.jdbc.Driver")
                            .getDeclaredConstructor().newInstance();
                }
            case "oracle":
                return (Driver) Class.forName("oracle.jdbc.OracleDriver")
                        .getDeclaredConstructor().newInstance();
            case "postgresql":
            case "pg":
                return (Driver) Class.forName("org.postgresql.Driver")
                        .getDeclaredConstructor().newInstance();
            case "sqlserver":
                return (Driver) Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver")
                        .getDeclaredConstructor().newInstance();
            default:
                throw new RuntimeException("不支持的数据库类型: " + dbType);
        }
    }

    /** 清空缓存（运维/升级驱动时调用） */
    public static void clearCache() {
        driverCache.clear();
        log.info("DriverLoader 缓存已清空");
    }
}