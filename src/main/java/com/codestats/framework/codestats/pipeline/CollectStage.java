package com.codestats.framework.codestats.pipeline;

import com.codestats.framework.codestats.filter.CompositeFilter;
import com.codestats.framework.log.LoggerFactory;
import com.codestats.framework.log.core.Logger;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class CollectStage implements Stage<Path, List<Path>> {
    private static final Logger log = LoggerFactory.getLogger(CollectStage.class);
    private final CompositeFilter filterChain;

    public CollectStage(CompositeFilter filterChain) { this.filterChain = filterChain; }

    @Override
    public List<Path> process(Path rootDir) throws IOException {
        List<Path> result = new ArrayList<>();
        Files.walkFileTree(rootDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (filterChain.accept(file)) result.add(file);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                log.warn("Skip file: {} - {}", file, exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });
        log.info("Collected {} files.", result.size());
        return result;
    }
}