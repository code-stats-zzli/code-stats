package com.omni.common.util;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.config.ConfigLoader;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Driver;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 资源目录驱动扫描器（DriverLoader 最后兜底）。
 *
 * <p>扫描 {@code {user.dir}/resources/drivers} 目录，文件名规则：
 * {@code {dbType}_{version}_{originalFileName}.jar}</p>
 *
 * <p>{@code dbType} 只有 4 个简写：mysql / oracle / sqlserver / postgresql</p>
 *
 * <p>默认目录可被 {@code driver.resource.dir} 配置覆盖（相对路径基于 user.dir）。</p>
 */
public final class DriverResourceScanner {
    private static final Logger log = LoggerFactory.getLogger(DriverResourceScanner.class);

    /** 允许的数据库类型简写 */
    public static final List<String> ALLOWED_DB_TYPES =
            java.util.Arrays.asList("mysql", "oracle", "sqlserver", "postgresql");

    /** {dbType}_{version}_{fileName}.jar —— fileName 保留 .jar 后缀 */
    private static final Pattern JAR_NAME_PATTERN = Pattern.compile(
            "^(.+?)_(.+?)_(.+\\.jar)$", Pattern.CASE_INSENSITIVE);

    private DriverResourceScanner() {}

    // ==================== 类型工具 ====================

    /** 是否是受支持的数据库类型简写 */
    public static boolean isAllowedDbType(String dbType) {
        if (dbType == null) return false;
        return ALLOWED_DB_TYPES.contains(dbType.trim().toLowerCase());
    }

    /** 归一化：小写 + 去空格；对 pg → postgresql 做别名兼容 */
    public static String normalizeDbType(String dbType) {
        if (dbType == null) return null;
        String t = dbType.trim().toLowerCase();
        if ("pg".equals(t)) return "postgresql";
        return t;
    }

    // ==================== 目录定位 ====================

    public static Path getDriversDir() {
        String configured = ConfigLoader.getProperty("driver.resource.dir", "");
        if (configured != null && !configured.trim().isEmpty()) {
            Path p = Paths.get(configured.trim());
            if (!p.isAbsolute()) {
                p = Paths.get(System.getProperty("user.dir")).resolve(p);
            }
            return p.toAbsolutePath().normalize();
        }
        return Paths.get(System.getProperty("user.dir"), "resources", "drivers")
                .toAbsolutePath().normalize();
    }

    // ==================== 扫描与解析 ====================

    public static class DriverFile {
        /** 就是 dbType（mysql/oracle/sqlserver/postgresql） */
        public final String dbType;
        public final String version;
        public final String originalFileName;
        public final Path jarPath;

        DriverFile(String dbType, String version, String originalFileName, Path jarPath) {
            this.dbType = dbType;
            this.version = version;
            this.originalFileName = originalFileName;
            this.jarPath = jarPath;
        }

        @Override
        public String toString() {
            return "DriverFile{dbType='" + dbType + "', version='" + version +
                    "', file='" + originalFileName + "', path='" + jarPath + "'}";
        }
    }

    /** 列出资源目录中所有能按 {dbType}_{version}_{fileName}.jar 解析的文件 */
    public static List<DriverFile> scan() {
        Path dir = getDriversDir();
        if (!Files.isDirectory(dir)) return new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".jar"))
                    .map(DriverResourceScanner::parse)
                    .filter(Objects::nonNull)
                    .sorted((a, b) -> b.jarPath.getFileName().toString()
                            .compareTo(a.jarPath.getFileName().toString()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("扫描资源目录失败: {}", dir, e);
            return new ArrayList<>();
        }
    }

    static DriverFile parse(Path jarPath) {
        String fileName = jarPath.getFileName().toString();
        Matcher m = JAR_NAME_PATTERN.matcher(fileName);
        if (!m.matches()) {
            log.debug("跳过不符合命名规则的 jar: {}", fileName);
            return null;
        }
        String dbType = normalizeDbType(m.group(1));
        if (!isAllowedDbType(dbType)) {
            log.debug("跳过未知 dbType 的 jar: {}, dbType={}", fileName, dbType);
            return null;
        }
        return new DriverFile(dbType, m.group(2), m.group(3), jarPath);
    }

    // ==================== 查找与加载 ====================

    /**
     * 按 dbType + version 匹配。
     *   - version 非空：必须精确匹配（不做宽松匹配，避免误用）
     *   - version 为空：返回 dbType 匹配的任意一个（按文件名倒序取第一个）
     */
    public static DriverFile find(String dbType, String version) {
        String type = normalizeDbType(dbType);
        if (!isAllowedDbType(type)) return null;

        List<DriverFile> all = scan();
        if (all.isEmpty()) return null;

        String v = (version == null) ? null : version.trim().toLowerCase();
        boolean hasVersion = (v != null && !v.isEmpty());

        if (hasVersion) {
            for (DriverFile df : all) {
                if (df.dbType.equalsIgnoreCase(type) && df.version.equalsIgnoreCase(v)) {
                    return df;
                }
            }
            return null;
        }
        for (DriverFile df : all) {
            if (df.dbType.equalsIgnoreCase(type)) return df;
        }
        return null;
    }

    /** 从资源目录加载 Driver 实例。失败返回 null。 */
    public static Driver loadDriver(String dbType, String version) {
        DriverFile df = find(dbType, version);
        if (df == null) return null;

        try {
            // 类名解析（复用 DriverClassNameResolver 的三级推导）
            String className = DriverClassNameResolver.fromFileName(df.originalFileName);
            if (className == null) {
                byte[] jarBytes = Files.readAllBytes(df.jarPath);
                className = DriverClassNameResolver.fromJarDirectory(jarBytes);
            }
            if (className == null) {
                className = DriverClassNameResolver.fromDbType(df.dbType);
            }
            if (className == null) {
                log.warn("资源目录驱动无法确定类名: {}", df);
                return null;
            }

            URL jarUrl = df.jarPath.toUri().toURL();
            URLClassLoader cl = new URLClassLoader(
                    new URL[]{jarUrl},
                    Thread.currentThread().getContextClassLoader());
            Class<?> clazz = cl.loadClass(className);
            Driver driver = (Driver) clazz.getDeclaredConstructor().newInstance();
            log.info("从资源目录加载驱动成功: dbType={}, version={}, class={}, file={}",
                    df.dbType, df.version, className, df.jarPath.getFileName());
            return driver;
        } catch (Exception e) {
            log.error("从资源目录加载驱动失败: {}", df, e);
            return null;
        }
    }

    // ==================== 落盘命名 ====================

    /**
     * 构造存储名：{dbType}_{version}_{fileName}
     * dbType 只允许 mysql/oracle/sqlserver/postgresql
     * version 中的下划线会替换为 '.'（防止破坏解析）
     */
    public static String buildStorageName(String dbType, String version, String originalFileName) {
        String type = normalizeDbType(dbType);
        if (!isAllowedDbType(type))
            throw new IllegalArgumentException("不支持的数据库类型: " + dbType
                    + "（仅支持 " + String.join("/", ALLOWED_DB_TYPES) + "）");
        if (version == null || version.trim().isEmpty())
            throw new IllegalArgumentException("驱动版本不能为空");
        if (originalFileName == null || originalFileName.trim().isEmpty())
            throw new IllegalArgumentException("原始文件名不能为空");

        String cleanVersion = version.trim().replace('_', '.');
        String cleanFile = originalFileName.trim();
        if (!cleanFile.toLowerCase().endsWith(".jar")) cleanFile = cleanFile + ".jar";

        return type + "_" + cleanVersion + "_" + cleanFile;
    }

    /** 把驱动 jar 写入资源目录。返回落盘后的绝对路径。 */
    public static Path writeJar(String dbType, String version,
                                String originalFileName, byte[] data) throws Exception {
        Path dir = getDriversDir();
        Files.createDirectories(dir);
        String storageName = buildStorageName(dbType, version, originalFileName);
        Path target = dir.resolve(storageName);
        Files.write(target, data);
        log.info("驱动 jar 已落盘: {}", target);
        return target;
    }
}