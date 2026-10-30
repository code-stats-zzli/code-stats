package com.omni.framework.codestats.pipeline;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class MergeFilesStage implements Stage<List<Path>, Void> {
    private static final Logger log = LoggerFactory.getLogger(MergeFilesStage.class);
    private final Path outputFile;

    public MergeFilesStage(Path outputFile) {
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

        try (OutputStream out = Files.newOutputStream(outputFile, StandardOpenOption.CREATE_NEW)) {
            for (Path file : files) {
                if (!Files.isReadable(file)) {
                    log.warn("Skipping unreadable file: {}", file);
                    continue;
                }
                String header = "\n// ======== " + file + " ========\n";
                out.write(header.getBytes(StandardCharsets.UTF_8));
                Files.copy(file, out);
                out.write('\n');
            }
            // ★ 追加「AI 提示 · 代码导入解析格式」注释块
            // 注意：所有注释行均不是 "// ======== <path> ========" 形式，
            // 因此 MergeFileFormatParser 解析本文件时不会误把它当成一个文件块。
            out.write(buildAiPrompt().getBytes(StandardCharsets.UTF_8));
        }
        log.info("Merged {} files into: {}", files.size(), outputFile);
        return null;
    }

    /**
     * 合并文件末尾追加的 AI 上下文提示块。
     *
     * <p>安全约束（与 MergeFileFormatParser 的正则对齐）：</p>
     * <ul>
     *   <li>解析正则：{@code ^// ======== (.+?) ========\s*$}（MULTILINE）</li>
     *   <li>该正则要求：行首 "// " + 恰好 8 个 '=' + 空格 + 路径 + 空格 + 恰好 8 个 '='</li>
     *   <li>因此本方法所有注释行均使用 60 个 '=' 或不同前缀，不会命中该正则</li>
     * </ul>
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
        sb.append("//       // ======== <文件路径> ========\n");
        sb.append("//   分隔头之后紧跟该文件的原始内容（原样保留，不转义、不缩进）。\n");
        sb.append("//   系统解析时会读取分隔头中的路径，并恢复为对应目录结构下的文件。\n");
        sb.append("//\n");
        sb.append("// 【示例】\n");
        sb.append("//   // ======== src/main/java/com/example/Foo.java ========\n");
        sb.append("//   package com.example;\n");
        sb.append("//   public class Foo { ... }\n");
        sb.append("//\n");
        sb.append("//   // ======== src/main/resources/config.yaml ========\n");
        sb.append("//   name: demo\n");
        sb.append("//   version: 1.0\n");
        sb.append("//\n");
        sb.append("// 【支持的源文件类型（示例）】\n");
        sb.append("//   .java .js .ts .py .go .c .cpp .h .md .xml .json .yml .yaml .properties .sql .html .css\n");
        sb.append("//\n");
        sb.append("// 【AI 使用建议】\n");
        sb.append("//   - 若要求「还原项目结构」，请按分隔头中的路径重新组织文件；\n");
        sb.append("//   - 若要求「生成补丁」，请保留分隔头并在对应块内修改；\n");
        sb.append("//   - 若要求「总结项目」，请先按分隔头聚合统计再分析。\n");
        sb.append("// ============================================================\n");
        sb.append("// （本提示块不参与代码导入解析，仅作为 AI 上下文说明）\n");
        sb.append("// ============================================================\n");
        return sb.toString();
    }
}