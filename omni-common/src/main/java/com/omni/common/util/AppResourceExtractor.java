package com.omni.common.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 启动时把 fat jar 内的 META-INF/app-resources/ 解压到 user.dir。
 *
 * - 开发模式（classes 目录）：直接跳过，资源本来就在源码目录
 * - fat jar 模式：解压 webapps / resources / tessdata 到 user.dir
 * - resources/drivers/ 下已存在的文件不覆盖（保护用户上传的驱动）
 * - 用 jar 的 lastModified+size 做版本标记，无变化则跳过解压
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

            int count = doExtract(jarPath, targetDir);
            Files.write(versionFile, version.getBytes(StandardCharsets.UTF_8));
            System.out.println("[AppResourceExtractor] 已解压 " + count
                    + " 个资源文件到: " + targetDir);
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

    private static int doExtract(Path jarPath, Path targetDir) throws IOException {
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