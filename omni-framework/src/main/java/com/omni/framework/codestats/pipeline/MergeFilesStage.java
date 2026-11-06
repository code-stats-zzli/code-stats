package com.omni.framework.codestats.pipeline;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class MergeFilesStage implements Stage<List<Path>, Void> {
    private static final Logger log = LoggerFactory.getLogger(MergeFilesStage.class);

    private static final String BASE64_TAG = "[BASE64]";
    private static final Base64.Encoder BASE64_ENCODER =
            Base64.getMimeEncoder(76, "\n".getBytes(StandardCharsets.US_ASCII));

    private final Path outputFile;
    private final Path rootDir;
    private final List<Path> extraPathDirs;

    public MergeFilesStage(Path outputFile) {
        this(null, null, outputFile);
    }

    public MergeFilesStage(Path rootDir, List<Path> extraPathDirs, Path outputFile) {
        this.rootDir = rootDir != null ? rootDir.toAbsolutePath().normalize() : null;
        this.extraPathDirs = new ArrayList<>();
        if (extraPathDirs != null) {
            for (Path p : extraPathDirs) {
                if (p != null) this.extraPathDirs.add(p.toAbsolutePath().normalize());
            }
        }
        this.outputFile = outputFile;
    }

    @Override
    public Void process(List<Path> files) throws IOException {
        if (files == null || files.isEmpty()) {
            log.info("No files to merge.");
            return null;
        }

        Files.deleteIfExists(outputFile);
        Path parent = outputFile.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        int written = 0;
        int skipped = 0;
        try (OutputStream out = Files.newOutputStream(outputFile, StandardOpenOption.CREATE_NEW)) {
            for (Path file : files) {
                if (!Files.isReadable(file)) {
                    log.warn("Skipping unreadable file: {}", file);
                    skipped++;
                    continue;
                }
                String rel = relativize(file);
                if (rel == null) {
                    log.warn("Skipping file outside any base dir: {}", file);
                    skipped++;
                    continue;
                }
                String header = "\n// ======== " + rel + " ========\n";

                // 白名单已排除二进制，这里 base64 分支仅作为最后兜底
                if (isBinaryFile(rel)) {
                    out.write((header + BASE64_TAG + "\n").getBytes(StandardCharsets.UTF_8));
                    byte[] data = Files.readAllBytes(file);
                    String b64 = BASE64_ENCODER.encodeToString(data);
                    out.write(b64.getBytes(StandardCharsets.US_ASCII));
                    if (!b64.endsWith("\n")) out.write('\n');
                    out.write('\n');
                } else {
                    out.write(header.getBytes(StandardCharsets.UTF_8));
                    Files.copy(file, out);
                    out.write('\n');
                }
                written++;
            }
            out.write(buildAiPrompt().getBytes(StandardCharsets.UTF_8));
        }
        log.info("Merged {} files into: {} (skipped {} unreadable/out-of-root)",
                written, outputFile, skipped);
        return null;
    }

    private boolean isBinaryFile(String path) {
        if (path == null) return false;
        int dot = path.lastIndexOf('.');
        if (dot < 0) return false;
        switch (path.substring(dot + 1).toLowerCase()) {
            case "jar": case "class": case "zip": case "gz": case "tar": case "tgz":
            case "png": case "jpg": case "jpeg": case "gif": case "bmp": case "ico":
            case "webp": case "pdf": case "exe": case "dll": case "so": case "dylib":
            case "bin": case "mp3": case "mp4": case "wav": case "flac":
            case "ttf": case "woff": case "woff2": case "eot":
            case "xlsx": case "xls": case "docx": case "doc": case "pptx": case "ppt":
                return true;
            default:
                return false;
        }
    }

    private String relativize(Path file) {
        Path abs = file.toAbsolutePath().normalize();
        if (rootDir != null && abs.startsWith(rootDir)) {
            return rootDir.relativize(abs).toString().replace('\\', '/');
        }
        for (Path base : extraPathDirs) {
            if (abs.startsWith(base)) {
                return base.relativize(abs).toString().replace('\\', '/');
            }
        }
        if (rootDir == null && extraPathDirs.isEmpty()) {
            return abs.toString().replace('\\', '/');
        }
        Path name = abs.getFileName();
        return name != null ? name.toString() : null;
    }

    private String buildAiPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append('\n');
        sb.append("// ============================================================\n");
        sb.append("// 📌 AI 提示 · 代码导入解析格式（merge-file）\n");
        sb.append("// ============================================================\n");
        sb.append("// 本合并文件遵循 CodeStats「代码导入」解析格式，可被系统自动拆分还原为独立源文件。\n");
        sb.append("//\n");
        sb.append("// 【合并文件格式说明】\n");
        sb.append("//   每个源文件以如下分隔头开始：\n");
        sb.append("//       // ======== <相对路径> ========\n");
        sb.append("//   分隔头之后紧跟该文件的原始内容（原样保留，不转义、不缩进）。\n");
        sb.append("//   二进制文件（.png/.jar/.xlsx 等）以 [BASE64] 标记，后接 base64 内容（76 字符换行）。\n");
        sb.append("//   系统解析时会读取分隔头中的路径，并恢复为 targetRoot 下对应目录结构。\n");
        sb.append("//\n");
        sb.append("// 【示例】\n");
        sb.append("//   // ======== omni-common/src/main/java/com/omni/common/cache/Cache.java ========\n");
        sb.append("//   package com.omni.common.cache;\n");
        sb.append("//   public interface Cache<K,V> { ... }\n");
        sb.append("//\n");
        sb.append("//   // ======== omni-bootstrap/src/main/resources/application.properties ========\n");
        sb.append("//   server.port=28080\n");
        sb.append("//   context.path=/omni\n");
        sb.append("//\n");
        sb.append("// 【使用建议】\n");
        sb.append("//   导入时 targetRoot 设为原项目根目录，即可完整还原多模块结构。\n");
        sb.append("// ============================================================\n");
        return sb.toString();
    }
}