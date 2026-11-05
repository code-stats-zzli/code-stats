package com.omni.framework.spider.pipeline;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MergePipeline {
    private static final Logger log = LoggerFactory.getLogger(MergePipeline.class);

    public static void merge(String sourceDir, String outputFile) throws IOException {
        Path sourcePath = Paths.get(sourceDir);
        Path outputPath = Paths.get(outputFile);
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            log.warn("源目录不存在或不是目录: {}", sourceDir);
            return;
        }
        Files.deleteIfExists(outputPath);
        Files.createDirectories(outputPath.getParent());

        List<Path> txtFiles;
        try (Stream<Path> walk = Files.walk(sourcePath)) {
            txtFiles = walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".txt"))
                    .sorted()
                    .collect(Collectors.toList());
        }

        try (OutputStream out = Files.newOutputStream(outputPath, StandardOpenOption.CREATE_NEW)) {
            for (Path file : txtFiles) {
                out.write(("// ======== " + file.getFileName() + " ========\n").getBytes());
                Files.copy(file, out);
                out.write('\n');
            }
        }
        log.info("合并完成，共 {} 个文件 -> {}", txtFiles.size(), outputFile);
    }
}