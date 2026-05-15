package com.omni.framework.codestats.pipeline;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.io.IOException;
import java.io.OutputStream;
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
                out.write(header.getBytes());
                Files.copy(file, out);
                out.write('\n');
            }
        }
        log.info("Merged {} files into: {}", files.size(), outputFile);
        return null;
    }
}