package com.omni.common.converter;

import com.omni.framework.converter.ConvertResult;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.config.ConfigLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 转换产物临时文件管理器。
 * <p>把转换结果落盘为临时文件，返回 safe storedName；下载时根据 storedName 取路径（严格校验）。</p>
 */
public final class TempFileManager {
    private static final Logger log = LoggerFactory.getLogger(TempFileManager.class);
    private static final Path ROOT = resolveRoot();

    private TempFileManager() {}

    private static Path resolveRoot() {
        String dir = ConfigLoader.getProperty("converter.tmp-dir",
                System.getProperty("user.dir") + "/tmp/converter");
        try {
            Path p = Paths.get(dir).toAbsolutePath().normalize();
            Files.createDirectories(p);
            return p;
        } catch (Exception e) {
            log.warn("converter.tmp-dir 创建失败，回退到 java.io.tmpdir", e);
            Path fallback = Paths.get(System.getProperty("java.io.tmpdir"), "omni-converter");
            try {
                Files.createDirectories(fallback);
            } catch (Exception ignored) {}
            return fallback;
        }
    }

    /** 转换结果落盘，返回安全的 storedName */
    public static String store(ConvertResult result) throws Exception {
        if (result == null || !result.isSuccess())
            throw new IllegalArgumentException("结果为空或失败");
        String ext = result.getTargetType().ext;
        String base = result.getSuggestedFileName();
        if (base == null || base.isEmpty()) base = "converted";
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        String storedName = base + "_" + UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path target = ROOT.resolve(storedName);
        if (result.getData() != null) {
            Files.write(target, result.getData());
        } else if (result.getText() != null) {
            Files.write(target, result.getText().getBytes(StandardCharsets.UTF_8));
        } else {
            Files.write(target, new byte[0]);
        }
        log.info("转换结果落盘: {}", target);
        return storedName;
    }

    /** 根据 storedName 解析路径，严格校验路径穿越 */
    public static Path resolve(String name) {
        if (name == null || name.isEmpty()) return null;
        if (name.contains("..") || name.contains("/") || name.contains("\\")) return null;
        Path p = ROOT.resolve(name).normalize();
        if (!p.startsWith(ROOT)) return null;
        return p;
    }
}