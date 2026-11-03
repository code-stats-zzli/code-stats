package com.omni.business.assistance.compress.service;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Service;
import com.omni.framework.spring.config.ConfigLoader;
import com.omni.framework.unzip.*;
import com.omni.framework.unzip.util.MergeFileWriter;
import com.omni.framework.unzip.writer.ArchiveWriter;
import com.omni.framework.unzip.writer.ArchiveWriterRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service
public class CompressService {
    private static final Logger log = LoggerFactory.getLogger(CompressService.class);

    @Autowired
    private ArchiveWriterRegistry writerRegistry;

    private final long maxEntryBytes = parseLong(ConfigLoader.getProperty("compress.max-entry-size", "209715200"), 209715200L);
    private final int maxFiles       = parseInt (ConfigLoader.getProperty("compress.max-files", "5000"), 5000);
    private final long maxTotalBytes = parseLong(ConfigLoader.getProperty("compress.max-total-bytes", "1073741824"), 1073741824L);

    public List<Map<String, Object>> listSupportedTypes() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<ArchiveType, ArchiveWriter> e : writerRegistry.getAll().entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", e.getKey().name());
            m.put("ext", e.getKey().ext);
            m.put("singleFileOnly", e.getKey() == ArchiveType.GZ);
            list.add(m);
        }
        return list;
    }

    public CompressOutcome compress(List<CompressEntry> rawEntries,
                                    ArchiveType targetType,
                                    String archiveFileName,
                                    String encoding) throws Exception {
        if (rawEntries == null || rawEntries.isEmpty())
            throw new CompressException("没有可压缩的文件");
        if (targetType == null)
            throw new CompressException("请选择压缩格式");
        if (rawEntries.size() > maxFiles)
            throw new CompressException("文件数量超过限制 " + maxFiles);

        List<CompressEntry> entries = new ArrayList<>();
        long totalBytes = 0;
        int fileCount = 0;
        for (CompressEntry e : rawEntries) {
            String safe = safePath(e.getPath());
            if (safe == null || safe.isEmpty()) continue;
            if (!safe.equals(e.getPath())) e = copyEntry(e, safe);
            if (!e.isDirectory()) {
                if (e.getContent() == null) e.setContent(new byte[0]);
                if (e.getContent().length > maxEntryBytes)
                    throw new CompressException("文件过大: " + safe);
                totalBytes += e.getContent().length;
                fileCount++;
            }
            entries.add(e);
        }
        if (entries.isEmpty()) throw new CompressException("没有可压缩的有效文件");
        if (totalBytes > maxTotalBytes)
            throw new CompressException("总大小超过限制 " + maxTotalBytes);

        ArchiveWriter writer = writerRegistry.get(targetType);
        if (writer == null) throw new CompressException("不支持的压缩格式: " + targetType);

        Path workRoot = Paths.get(System.getProperty("java.io.tmpdir"), "omni-compress",
                UUID.randomUUID().toString().replace("-", ""));
        Files.createDirectories(workRoot);

        Path archiveFile = workRoot.resolve(archiveFileName);

        CompressRequest req = new CompressRequest();
        req.setTargetType(targetType);
        req.setEntries(entries);
        req.setOutputFile(archiveFile);
        req.setEncoding(encoding != null ? encoding : "UTF-8");

        long start = System.currentTimeMillis();
        CompressResult result = writer.compress(req);
        if (result == null || !result.isSuccess())
            throw new CompressException(result != null ? result.getMessage() : "压缩失败");

        // 同时生成 merge-file
        Path mergedFile = workRoot.resolve(baseName(archiveFileName) + "_merged.txt");
        List<MergeFileWriter.Entry> mergeEntries = new ArrayList<>();
        for (CompressEntry e : entries) {
            if (e.isDirectory()) continue;
            mergeEntries.add(new MergeFileWriter.Entry(e.getPath(), e.getContent()));
        }
        MergeFileWriter.Stat mergeStat = MergeFileWriter.writeEntries(mergeEntries, mergedFile);

        log.info("压缩完成: type={}, files={}, archive={}B, merged={}B, duration={}ms",
                targetType, result.getFileCount(), result.getArchiveBytes(),
                Files.size(mergedFile), System.currentTimeMillis() - start);

        return new CompressOutcome(result, archiveFile, mergedFile, mergeStat);
    }

    public static class CompressOutcome {
        public final CompressResult result;
        public final Path archiveFile;
        public final Path mergedFile;
        public final MergeFileWriter.Stat mergeStat;
        public CompressOutcome(CompressResult r, Path a, Path m, MergeFileWriter.Stat s) {
            this.result = r; this.archiveFile = a; this.mergedFile = m; this.mergeStat = s;
        }
    }

    private CompressEntry copyEntry(CompressEntry src, String newPath) {
        CompressEntry e = new CompressEntry();
        e.setPath(newPath);
        e.setContent(src.getContent());
        e.setDirectory(src.isDirectory());
        return e;
    }

    private String safePath(String path) {
        if (path == null) return null;
        String p = path.replace('\\', '/').trim();
        while (p.startsWith("/")) p = p.substring(1);
        if (p.isEmpty()) return null;
        for (String seg : p.split("/")) if ("..".equals(seg)) return null;
        if (p.matches("^[A-Za-z]:.*")) return null;
        return p;
    }

    private static String baseName(String fileName) {
        if (fileName == null) return "archive";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".tar.gz")) return fileName.substring(0, fileName.length() - 7);
        if (lower.endsWith(".tgz")) return fileName.substring(0, fileName.length() - 4);
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static long parseLong(String s, long def) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return def; }
    }
    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
}