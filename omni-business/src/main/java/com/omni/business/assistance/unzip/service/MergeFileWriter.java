package com.omni.business.assistance.unzip.service;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.unzip.util.LanguageGuesser;

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
                w.write("// ======== " + rel + " ========\n");
                stat.totalFiles++;

                if (LanguageGuesser.isBinary(rel)) {
                    w.write(BASE64_TAG + "\n");
                    byte[] bytes = Files.readAllBytes(file);
                    String b64 = ENCODER.encodeToString(bytes);
                    w.write(b64);
                    if (!b64.endsWith("\n")) w.write("\n");
                    w.write("\n");
                    stat.binaryFiles++;
                    stat.bytesWritten += bytes.length;
                } else {
                    String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                    w.write(content);
                    if (!content.endsWith("\n")) w.write("\n");
                    w.write("\n");
                    stat.textFiles++;
                    stat.bytesWritten += content.getBytes(StandardCharsets.UTF_8).length;
                }
            }
            w.write(buildAiPrompt());
        }
        log.info("合并文件已生成: {} (总 {} 文件 / 文本 {} / 二进制 {} / 原始字节 {})",
                outputFile, stat.totalFiles, stat.textFiles, stat.binaryFiles, stat.bytesWritten);
        return stat;
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