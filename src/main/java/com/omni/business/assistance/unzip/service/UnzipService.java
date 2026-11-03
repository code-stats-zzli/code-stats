package com.omni.business.assistance.unzip.service;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Service;
import com.omni.framework.spring.config.ConfigLoader;
import com.omni.framework.unzip.*;
import com.omni.framework.unzip.util.LanguageGuesser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class UnzipService {
    private static final Logger log = LoggerFactory.getLogger(UnzipService.class);

    @Autowired
    private ArchiveExtractorRegistry registry;

    private final long maxFileSize   = parseLong(ConfigLoader.getProperty("unzip.max-file-size", "209715200"), 209715200L);
    private final int  maxFiles      = parseInt (ConfigLoader.getProperty("unzip.max-files", "5000"), 5000);
    private final long maxTotalBytes = parseLong(ConfigLoader.getProperty("unzip.max-total-bytes", "1073741824"), 1073741824L);

    public List<Map<String, Object>> listSupportedTypes() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<ArchiveType, ArchiveExtractor> e : registry.getAll().entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", e.getKey().name());
            m.put("ext", e.getKey().ext);
            list.add(m);
        }
        return list;
    }

    public ExtractResult extract(ExtractRequest req) throws Exception {
        if (req.getSourceBytes() == null || req.getSourceBytes().length == 0)
            throw new ExtractException("压缩包内容为空");
        if (req.getSourceBytes().length > maxFileSize)
            throw new ExtractException("压缩包大小超过限制: " + maxFileSize + " 字节");

        byte[] src = req.getSourceBytes();
        byte[] head = Arrays.copyOf(src, Math.min(src.length, 512));

        ArchiveType type = ArchiveTypeDetector.detect(req.getSourceFileName(), head);
        if (type == null)
            throw new ExtractException("无法识别的压缩格式（支持 zip/tar/tar.gz/tgz/gz）");

        ArchiveExtractor extractor = registry.resolve(type, head, req.getSourceFileName());
        if (extractor == null)
            throw new ExtractException("不支持的压缩格式: " + type.name());

        req.setMaxFiles(maxFiles);
        req.setMaxTotalBytes(maxTotalBytes);

        log.info("开始解压: type={}, fileName={}, size={}B", type, req.getSourceFileName(), src.length);
        ExtractResult result = extractor.extract(req);
        if (result == null || !result.isSuccess())
            throw new ExtractException(result != null ? result.getMessage() : "解压失败");

        log.info("解压完成: type={}, files={}, binary={}, bytes={}, duration={}ms",
                type, result.getTotalFiles(), result.getBinaryFiles(),
                result.getTotalBytes(), result.getDurationMs());
        return result;
    }

    public MergeFileWriter.Stat writeMergedFile(Path sourceDir, Path outputFile) throws IOException {
        return MergeFileWriter.write(sourceDir, outputFile);
    }

    public Path zipDirectoryToFile(Path sourceDir, Path outputZip) throws IOException {
        Path parent = outputZip.getParent();
        if (parent != null) Files.createDirectories(parent);

        List<Path> files = new ArrayList<>();
        try (var s = Files.walk(sourceDir)) {
            s.filter(Files::isRegularFile).forEach(files::add);
        }
        files.sort(Comparator.comparing(p ->
                sourceDir.relativize(p).toString().replace('\\', '/')));

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outputZip,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            for (Path file : files) {
                String rel = sourceDir.relativize(file).toString().replace('\\', '/');
                zos.putNextEntry(new ZipEntry(rel));
                Files.copy(file, zos);
                zos.closeEntry();
            }
        }
        return outputZip;
    }

    public Map<String, Object> readEntry(Path outputDir, String relPath) throws Exception {
        if (outputDir == null || !Files.isDirectory(outputDir))
            throw new ExtractException("任务输出目录不存在");
        Path resolved = PathTraversalGuard.resolveSafe(outputDir, relPath);
        if (!Files.isRegularFile(resolved))
            throw new ExtractException("文件不存在: " + relPath);

        long size = Files.size(resolved);
        boolean binary = LanguageGuesser.isBinary(relPath);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("path", relPath);
        r.put("size", size);
        r.put("language", LanguageGuesser.guess(relPath));
        r.put("binary", binary);

        if (binary) {
            r.put("content", "（二进制文件 · 大小 " + size + " B，合并文件将包含完整 base64 编码）");
        } else {
            try (InputStream in = Files.newInputStream(resolved)) {
                byte[] data = in.readAllBytes();
                r.put("content", new String(data, StandardCharsets.UTF_8));
            }
        }
        return r;
    }

    private static long parseLong(String s, long def) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return def; }
    }
    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
}