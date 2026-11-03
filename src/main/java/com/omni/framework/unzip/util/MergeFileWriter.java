package com.omni.framework.unzip.util;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 通用 merge-file 生成器（框架层）。
 * 输出格式与 ImportFileService 完全对齐：
 *   // ======== <path> ========
 *   <原样文本>
 * 或
 *   // ======== <path> ========
 *   [BASE64]
 *   <base64（76 字符换行）>
 */
public final class MergeFileWriter {
    private static final Logger log = LoggerFactory.getLogger(MergeFileWriter.class);

    private static final String BASE64_TAG = "[BASE64]";
    private static final Base64.Encoder ENCODER =
            Base64.getMimeEncoder(76, "\n".getBytes(StandardCharsets.US_ASCII));

    private MergeFileWriter() {}

    public static class Stat {
        public int totalFiles;
        public int textFiles;
        public int binaryFiles;
        public long bytesWritten;
    }

    public static class Entry {
        public final String path;
        public final byte[] content;
        public final boolean directory;
        public Entry(String path, byte[] content) {
            this.path = path; this.content = content; this.directory = false;
        }
        public Entry(String path, boolean directory) {
            this.path = path; this.content = null; this.directory = directory;
        }
    }

    /** 从目录生成 merge-file */
    public static Stat write(Path sourceDir, Path outputFile) throws IOException {
        Stat stat = new Stat();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> s = Files.walk(sourceDir)) {
            s.filter(Files::isRegularFile).forEach(files::add);
        }
        files.sort(Comparator.comparing(p ->
                sourceDir.relativize(p).toString().replace('\\', '/')));
        Path parent = outputFile.getParent();
        if (parent != null) Files.createDirectories(parent);
        try (BufferedWriter w = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
            for (Path file : files) {
                String rel = sourceDir.relativize(file).toString().replace('\\', '/');
                writeOne(w, rel, Files.readAllBytes(file), stat);
            }
            w.write(buildAiPrompt());
        }
        log.info("合并文件已生成: {} (总 {} 文件 / 文本 {} / 二进制 {})",
                outputFile, stat.totalFiles, stat.textFiles, stat.binaryFiles);
        return stat;
    }

    /** 直接给一组 (path, content) 写 merge-file */
    public static Stat writeEntries(List<Entry> entries, Path outputFile) throws IOException {
        Stat stat = new Stat();
        entries.sort(Comparator.comparing(e -> e.path));
        Path parent = outputFile.getParent();
        if (parent != null) Files.createDirectories(parent);
        try (BufferedWriter w = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
            for (Entry e : entries) {
                if (e.directory) continue;
                writeOne(w, e.path, e.content != null ? e.content : new byte[0], stat);
            }
            w.write(buildAiPrompt());
        }
        return stat;
    }

    private static void writeOne(BufferedWriter w, String rel, byte[] content, Stat stat) throws IOException {
        w.write("// ======== " + rel + " ========\n");
        stat.totalFiles++;
        if (isBinary(rel)) {
            w.write(BASE64_TAG + "\n");
            String b64 = ENCODER.encodeToString(content);
            w.write(b64);
            if (!b64.endsWith("\n")) w.write("\n");
            w.write("\n");
            stat.binaryFiles++;
            stat.bytesWritten += content.length;
        } else {
            String text = new String(content, StandardCharsets.UTF_8);
            w.write(text);
            if (!text.endsWith("\n")) w.write("\n");
            w.write("\n");
            stat.textFiles++;
            stat.bytesWritten += content.length;
        }
    }

    public static boolean isBinary(String path) {
        if (path == null) return false;
        int dot = path.lastIndexOf('.');
        if (dot < 0) return false;
        String ext = path.substring(dot + 1).toLowerCase();
        switch (ext) {
            case "jar": case "class": case "zip": case "gz": case "tar": case "tgz":
            case "png": case "jpg": case "jpeg": case "gif": case "bmp": case "ico":
            case "webp": case "pdf": case "exe": case "dll": case "so": case "dylib":
            case "bin": case "mp3": case "mp4": case "wav": case "flac":
            case "ttf": case "woff": case "woff2": case "eot":
            case "xlsx": case "xls": case "docx": case "doc": case "pptx": case "ppt":
                return true;
            default: return false;
        }
    }

    private static String buildAiPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append('\n');
        sb.append("// ============================================================\n");
        sb.append("// 📌 AI 提示 · 代码导入解析格式（merge-file）\n");
        sb.append("// ============================================================\n");
        sb.append("// 块头格式：// ======== <文件路径> ========\n");
        sb.append("// 文本块：块头后紧跟文件原始内容（原样保留）\n");
        sb.append("// 二进制块：块头后紧跟一行 [BASE64]，再接 base64 内容（76 字符换行）\n");
        sb.append("// ============================================================\n");
        return sb.toString();
    }
}