package com.omni.common.util;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.config.ConfigLoader;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Driver;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.jar.JarInputStream;

/**
 * 资源目录驱动扫描器（DriverLoader 兜底 + 下拉数据来源）。
 *
 * <p>物理目录：{@code {user.dir}/resources/drivers}（可由 {@code driver.resource.dir} 覆盖）</p>
 *
 * <p>文件命名：{@code {dbType}_{version}_{原始文件名}.jar}</p>
 *
 * <p>★ 递归扫描：支持 {@code resources/drivers/} 下按 dbType 分目录（最多 5 层）</p>
 *
 * <p>加载失败时抛 {@link DriverLoadException}，携带来源路径、期望类、候选类、修复建议。</p>
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

    /**
     * 便捷方法：find + loadDriverFromFile。
     * 若 find 返回 null（目录里没匹配文件），返回 null 交由上层继续兜底；
     * 若找到但加载失败，抛 {@link DriverLoadException}（不静默回退）。
     */
    public static Driver loadDriver(String dbType, String version) {
        DriverFile df = find(dbType, version);
        if (df == null) return null;
        return loadDriverFromFile(df);
    }

    /**
     * 加载指定的 DriverFile。
     * 失败时抛 {@link DriverLoadException}，携带来源路径、期望类、候选类、修复建议。
     */
    public static Driver loadDriverFromFile(DriverFile df) {
        String jarLocation = df.jarPath.toString();

        // ① 读取 JAR 字节（后续用于候选类扫描）
        byte[] jarBytes;
        try {
            jarBytes = Files.readAllBytes(df.jarPath);
        } catch (Exception e) {
            throw new DriverLoadException(
                    "read_jar", df.dbType, df.version,
                    "resources/drivers(目录)", jarLocation,
                    null, Collections.emptyList(),
                    "读取 JAR 文件失败：" + e.getMessage() + "\n"
                            + "请检查文件是否存在、是否有读权限。", e);
        }

        // ② 解析驱动类名（三级推断）
        String className = DriverClassNameResolver.fromFileName(df.originalFileName);
        if (className == null) {
            className = DriverClassNameResolver.fromJarDirectory(jarBytes);
        }
        if (className == null) {
            className = DriverClassNameResolver.fromDbType(df.dbType);
        }
        if (className == null) {
            List<String> candidates = JarClassScanner.findDriverCandidates(jarBytes);
            String hint = "无法从文件名或 JAR 内容推断驱动类名。\n"
                    + "请重新上传按「{类型}_{版本}_{原始文件名}.jar」命名规范的文件，\n"
                    + "或在数据源配置中手动指定 driverClass。";
            if (!candidates.isEmpty()) {
                hint += "\nJAR 内发现候选类：\n  • " + String.join("\n  • ", candidates);
            }
            throw new DriverLoadException(
                    "resolve_class", df.dbType, df.version,
                    "resources/drivers(目录)", jarLocation,
                    null, candidates, hint, null);
        }

        // ③ 加载类
        Class<?> clazz;
        try {
            URL jarUrl = df.jarPath.toUri().toURL();
            URLClassLoader cl = new URLClassLoader(
                    new URL[]{jarUrl}, Thread.currentThread().getContextClassLoader());
            try {
                clazz = cl.loadClass(className);
            } catch (ClassNotFoundException e) {
                List<String> candidates = JarClassScanner.findDriverCandidates(jarBytes);
                String hint = buildClassNotFoundHint(df, className, candidates);
                throw new DriverLoadException(
                        "load_class", df.dbType, df.version,
                        "resources/drivers(目录)", jarLocation,
                        className, candidates, hint, e);
            } catch (LinkageError e) {
                List<String> candidates = JarClassScanner.findDriverCandidates(jarBytes);
                String hint = "驱动类 " + className + " 加载时依赖缺失：" + e.getMessage() + "\n"
                        + "该 JAR 可能不是完整驱动包，请重新下载官方完整版驱动 JAR 后上传。";
                throw new DriverLoadException(
                        "linkage", df.dbType, df.version,
                        "resources/drivers(目录)", jarLocation,
                        className, candidates, hint, e);
            }
        } catch (DriverLoadException e) {
            throw e;
        } catch (Exception e) {
            throw new DriverLoadException(
                    "instantiate", df.dbType, df.version,
                    "resources/drivers(目录)", jarLocation,
                    className, Collections.emptyList(),
                    "打开 JAR 文件失败：" + e.getMessage(), e);
        }

        if (!Driver.class.isAssignableFrom(clazz)) {
            throw new DriverLoadException(
                    "cast", df.dbType, df.version,
                    "resources/drivers(目录)", jarLocation,
                    className, Collections.emptyList(),
                    "类 " + className + " 存在但不是 java.sql.Driver 实现。\n"
                            + "该 JAR 可能不是数据库驱动包，请确认上传的文件类型。", null);
        }

        // ④ 实例化
        try {
            Driver driver = (Driver) clazz.getDeclaredConstructor().newInstance();
            log.info("从资源目录加载驱动成功: dbType={}, version={}, class={}, file={}",
                    df.dbType, df.version, className, df.jarPath.getFileName());
            return driver;
        } catch (Exception e) {
            throw new DriverLoadException(
                    "instantiate", df.dbType, df.version,
                    "resources/drivers(目录)", jarLocation,
                    className, Collections.emptyList(),
                    "驱动类 " + className + " 实例化失败：" + e.getMessage() + "\n"
                            + "请确认该类有无参构造函数，且不是抽象类。", e);
        }
    }

    private static String buildClassNotFoundHint(DriverFile df, String expectedClass,
                                                 List<String> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("驱动 JAR [").append(df.jarPath.getFileName()).append("] 中不存在类 [")
                .append(expectedClass).append("]。\n");
        if (!candidates.isEmpty()) {
            sb.append("JAR 内发现以下可能的驱动类：\n");
            for (String c : candidates) sb.append("  • ").append(c).append("\n");
            sb.append("请在数据源配置中将 driverClass 改为上述类之一，或重新上传匹配的驱动 JAR。");
        } else {
            sb.append("JAR 内未发现任何可能的驱动类（类名以 Driver 结尾或包含 jdbc/driver 包路径）。\n")
                    .append("请从数据库官方或 Maven Central 重新下载完整驱动 JAR 后上传。");
        }
        return sb.toString();
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