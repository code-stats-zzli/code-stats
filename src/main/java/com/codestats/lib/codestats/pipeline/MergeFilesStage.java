package com.codestats.lib.codestats.pipeline;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * 将所有收集到的文件内容合并输出到一个指定文件。
 * 如果目标文件已存在，则先删除再创建。
 */
public class MergeFilesStage implements Stage<List<Path>, Void> {

    private final Path outputFile;

    /**
     * @param outputFile 合并后的输出文件路径（包含文件名）
     */
    public MergeFilesStage(Path outputFile) {
        this.outputFile = outputFile;
    }

    @Override
    public Void process(List<Path> files) throws IOException {
        if (files == null || files.isEmpty()) {
            System.out.println("No files to merge.");
            return null;
        }

        // 如果目标文件已存在，先删除
        Files.deleteIfExists(outputFile);

        // 确保父目录存在
        Path parent = outputFile.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        // 使用缓冲流写入，避免占用过多内存
        try (OutputStream out = Files.newOutputStream(outputFile, StandardOpenOption.CREATE_NEW)) {
            for (Path file : files) {
                if (!Files.isReadable(file)) {
                    System.err.println("Skipping unreadable file: " + file);
                    continue;
                }
                // 写入文件分隔标识（可选）
                String header = "\n// ======== " + file + " ========\n";
                out.write(header.getBytes());
                Files.copy(file, out);
                out.write('\n');
            }
        }

        System.out.println("Merged " + files.size() + " files into: " + outputFile);
        return null;
    }
}