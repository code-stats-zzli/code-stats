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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 资源目录驱动扫描器（DriverLoader 兜底 + 下拉数据来源）。
 *
 * <p>物理目录：{@code {user.dir}/resources/drivers}（可由 {@code driver.resource.dir} 覆盖）</p>
 *
 * <p>文件命名：{@code {dbType}_{version}_{原始文件名}.jar}</p>
 *
 * <p>★ 递归扫描：支持 {@code resources/drivers/} 下按 dbType 分目录（最多 5 层）</p>
 */
public final class DriverResourceScanner {
    private static final Logger log = LoggerFactory.getLogger(DriverResourceScanner.class);

    /** 兼容旧调用，委托给 DriverFileNameParser */
    public static final List<String> ALLOWED_DB_TYPES =
            Arrays.asList(DriverFileNameParser.ALLOWED_DB_TYPES);

    /** {dbType}_{version}_{fileName}.jar —— fileName 保留 .jar 后缀 */
    private static final Pattern JAR_NAME_PATTERN = Pattern.compile(
            "^(.+?)_(.+?)_(.+\\.jar)$", Pattern.CASE_INSENSITIVE);

    /** 递归最大深度（防止误扫深层目录） */
    private static final int MAX_SCAN_DEPTH = 5;

    private DriverResourceScanner() {}

    // ==================== 兼容旧调用的工具方法 ====================

    public static boolean isAllowedDbType(String dbType) {
        return DriverFileNameParser.isAllowedDbType(dbType);
    }

    public static String normalizeDbType(String dbType) {
        return DriverFileNameParser.normalizeDbType(dbType);
    }

    public static String buildStorageName(String dbType, String version, String originalFileName) {
        return DriverFileNameParser.buildStorageName(dbType, version, originalFileName);
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
        public final String dbType;            // mysql/oracle/sqlserver/postgresql
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

    /**
     * 递归扫描驱动目录（限深 5 层），返回所有能按命名规则解析的 jar。
     * 按文件名倒序排列（新上传在前）。
     */
    public static List<DriverFile> scan() {
        Path dir = getDriversDir();
        if (!Files.isDirectory(dir)) return new ArrayList<>();
        try (Stream<Path> stream = Files.walk(dir, MAX_SCAN_DEPTH)) {
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
        String dbType = DriverFileNameParser.normalizeDbType(m.group(1));
        if (!DriverFileNameParser.isAllowedDbType(dbType)) {
            log.debug("跳过未知 dbType 的 jar: {}, dbType={}", fileName, dbType);
            return null;
        }
        return new DriverFile(dbType, m.group(2), m.group(3), jarPath);
    }

    // ==================== 查找与加载 ====================

    /**
     * 按 dbType + version 匹配。
     *   - version 非空：精确匹配
     *   - version 为空：返回 dbType 匹配的第一个（文件名倒序，新文件优先）
     */
    public static DriverFile find(String dbType, String version) {
        String type = DriverFileNameParser.normalizeDbType(dbType);
        if (!DriverFileNameParser.isAllowedDbType(type)) return null;
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

    public static Driver loadDriver(String dbType, String version) {
        DriverFile df = find(dbType, version);
        if (df == null) return null;

        try {
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

    public static Path writeJar(String dbType, String version,
                                String originalFileName, byte[] data) throws Exception {
        Path dir = getDriversDir();
        Files.createDirectories(dir);
        String storageName = DriverFileNameParser.buildStorageName(dbType, version, originalFileName);
        Path target = dir.resolve(storageName);
        Files.write(target, data);
        log.info("驱动 jar 已落盘: {}", target);
        return target;
    }
}