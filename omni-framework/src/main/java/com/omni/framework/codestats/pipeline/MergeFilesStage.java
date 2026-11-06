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
import java.util.List;

public class MergeFilesStage implements Stage<List<Path>, Void> {
    private static final Logger log = LoggerFactory.getLogger(MergeFilesStage.class);

    private final Path outputFile;
    /** 项目根目录（相对路径基准）；null 表示沿用旧行为（写绝对路径） */
    private final Path rootDir;
    /** extraPaths 中的目录列表（相对路径第二基准） */
    private final List<Path> extraPathDirs;

    /**
     * 旧构造器（不推荐）：header 写绝对路径 → 无法被「代码导入」还原。
     * 保留仅为了兼容可能的旧调用点。
     */
    public MergeFilesStage(Path outputFile) {
        this(null, null, outputFile);
    }

    /**
     * 推荐构造器：header 写相对 rootDir 的路径 → 可被「代码导入」还原。
     *
     * @param rootDir       代码分析的 rootDir
     * @param extraPathDirs extraPaths 中实际存在的目录列表（用于给额外目录的文件算相对路径）
     * @param outputFile    合并文件输出路径
     */
    public MergeFilesStage(Path rootDir, List<Path> extraPathDirs, Path outputFile) {
        this.rootDir = rootDir != null ? rootDir.toAbsolutePath().normalize() : null;
        this.extraPathDirs = new ArrayList<>();
        if (extraPathDirs != null) {
            for (Path p : extraPathDirs) {
                if (p != null) {
                    this.extraPathDirs.add(p.toAbsolutePath().normalize());
                }
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
                out.write(header.getBytes(StandardCharsets.UTF_8));
                Files.copy(file, out);
                out.write('\n');
                written++;
            }
            out.write(buildAiPrompt().getBytes(StandardCharsets.UTF_8));
        }
        log.info("Merged {} files into: {} (skipped {} unreadable/out-of-root)", written, outputFile, skipped);
        return null;
    }

    /**
     * 计算文件的相对路径：
     *   1) 在 rootDir 下       → rootDir.relativize(file)
     *   2) 在某个 extraPathDir → extraPathDir.relativize(file)
     *   3) 旧构造器（两者皆空）→ 保留绝对路径（向后兼容）
     *   4) 新构造器找不到 base → 用文件名兜底（保证可导入）
     */
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

    /**
     * 合并文件末尾追加的 AI 上下文提示块。
     * 文档与实现现已一致：header 中写的是相对 rootDir 的相对路径。
     */
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
        sb.append("//   相对路径相对本次代码分析的 rootDir，例如：\n");
        sb.append("//       omni-common/src/main/java/com/omni/common/cache/Cache.java\n");
        sb.append("//   分隔头之后紧跟该文件的原始内容（原样保留，不转义、不缩进）。\n");
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
        sb.append("// （本提示块不参与代码导入解析，仅作为 AI 上下文说明）\n");
        sb.append("// ============================================================\n");
        return sb.toString();
    }
}