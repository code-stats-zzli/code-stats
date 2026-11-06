package com.omni.common.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 启动时把 fat jar 内的 META-INF/app-resources/ 解压到 user.dir。
 *
 * <p>本次修复：支持通过配置 strip 掉 jar 内的包装目录前缀（默认 {@code omni-bootstrap/}），
 * 保证无论 maven-resources-plugin 怎么配置，最终都解压到 user.dir 的对应位置。</p>
 *
 * <p>配置项（优先级：system property > classpath:application.properties > 默认值）：</p>
 * <pre>
 *   # jar 内 META-INF/app-resources/ 下需要剥掉的包装目录前缀（逗号分隔）
 *   # 例如 jar 内是 META-INF/app-resources/omni-bootstrap/webapps/demo/index.html，
 *   # 配置此项后会解压到 user.dir/webapps/demo/index.html（而非 user.dir/omni-bootstrap/webapps/...）
 *   app.resources.strip-prefixes=omni-bootstrap
 * </pre>
 */
public final class AppResourceExtractor {

    private static final String PREFIX = "META-INF/app-resources/";
    private static final String VERSION_FILE = ".app-resources-version";

    private AppResourceExtractor() {
    }

    public static void extractIfNeeded() {
        try {
            Path jarPath = locateJar();
            if (jarPath == null) {
                // IDE 开发模式，资源在 src/main/... 目录，无需解压
                return;
            }

            Path targetDir = Paths.get(System.getProperty("user.dir"))
                    .toAbsolutePath().normalize();

            File jarFile = jarPath.toFile();
            String version = jarFile.lastModified() + "-" + jarFile.length();

            Path versionFile = targetDir.resolve(VERSION_FILE);
            if (Files.exists(versionFile)) {
                String existing = new String(Files.readAllBytes(versionFile), StandardCharsets.UTF_8).trim();
                if (version.equals(existing)) {
                    return;
                }
            }

            List<String> stripPrefixes = loadStripPrefixes();
            int count = doExtract(jarPath, targetDir, stripPrefixes);
            Files.write(versionFile, version.getBytes(StandardCharsets.UTF_8));
            System.out.println("[AppResourceExtractor] 已解压 " + count
                    + " 个资源文件到: " + targetDir
                    + "，strip-prefixes=" + stripPrefixes);
        } catch (Throwable t) {
            // 解压失败不能阻止启动，但必须打日志
            System.err.println("[AppResourceExtractor] 解压失败: " + t.getClass().getSimpleName()
                    + ": " + t.getMessage());
            t.printStackTrace();
        }
    }

    private static Path locateJar() {
        try {
            URL url = AppResourceExtractor.class
                    .getProtectionDomain().getCodeSource().getLocation();
            if (url == null) return null;
            Path p = Paths.get(url.toURI());
            if (Files.isDirectory(p)) return null;              // 开发模式
            if (p.toString().toLowerCase().endsWith(".jar")) return p;
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 加载 strip 前缀列表。
     * 优先级：system property > classpath:application.properties > 默认 omni-bootstrap。
     */
    private static List<String> loadStripPrefixes() {
        String cfg = null;
        try {
            cfg = System.getProperty("app.resources.strip-prefixes");
        } catch (Throwable ignored) {
        }
        if (cfg == null) {
            try {
                Properties p = new Properties();
                try (InputStream is = AppResourceExtractor.class.getClassLoader()
                        .getResourceAsStream("application.properties")) {
                    if (is != null) {
                        try (Reader r = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                            p.load(r);
                        }
                    }
                }
                cfg = p.getProperty("app.resources.strip-prefixes", "omni-bootstrap");
            } catch (Throwable t) {
                cfg = "omni-bootstrap";
            }
        }
        List<String> result = new ArrayList<>();
        if (cfg != null) {
            for (String s : cfg.split(",")) {
                String t = s.trim().replace('\\', '/');
                if (t.isEmpty()) continue;
                if (t.startsWith("/")) t = t.substring(1);
                if (!t.endsWith("/")) t = t + "/";
                if (!result.contains(t)) result.add(t);
            }
        }
        if (result.isEmpty()) result.add("omni-bootstrap/");
        return result;
    }

    /**
     * 反复剥离 rel 头部命中 stripPrefixes 的前缀，直到不再命中为止。
     * 返回剥离后的 rel（可能为空串，调用方丢弃）。
     */
    private static String stripAllPrefixes(String rel, List<String> stripPrefixes) {
        if (rel == null || rel.isEmpty()) return rel;
        boolean changed = true;
        while (changed && !rel.isEmpty()) {
            changed = false;
            for (String p : stripPrefixes) {
                if (rel.startsWith(p) && rel.length() > p.length()) {
                    rel = rel.substring(p.length());
                    changed = true;
                    break;
                }
            }
        }
        return rel;
    }

    private static int doExtract(Path jarPath, Path targetDir, List<String> stripPrefixes) throws IOException {
        JarFile jar = new JarFile(jarPath.toFile());
        int count = 0;
        try {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith(PREFIX)) continue;
                if (entry.isDirectory()) continue;

                String rel = name.substring(PREFIX.length());
                if (rel.isEmpty()) continue;

                // ★ 剥掉配置的包装前缀（默认 omni-bootstrap/）
                rel = stripAllPrefixes(rel, stripPrefixes);
                if (rel.isEmpty()) continue;

                Path out = targetDir.resolve(rel).normalize();
                // 防路径穿越
                if (!out.startsWith(targetDir)) continue;

                // 用户上传的驱动 jar 不允许被覆盖
                boolean isUserDriver = rel.startsWith("resources/drivers/")
                        || rel.startsWith("resources" + File.separator + "drivers" + File.separator);
                if (isUserDriver && Files.exists(out)) continue;

                Path parent = out.getParent();
                if (parent != null) Files.createDirectories(parent);

                InputStream is = jar.getInputStream(entry);
                try {
                    Files.copy(is, out, StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    try { is.close(); } catch (Exception ignored) {}
                }
                count++;
            }
        } finally {
            try { jar.close(); } catch (Exception ignored) {}
        }
        return count;
    }
}