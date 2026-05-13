package com.codestats.lib.codestats.pipeline;

import com.codestats.lib.codestats.filter.CompositeFilter;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class CollectStage implements Stage<Path, List<Path>> {
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
                System.err.println("Skip file: " + file + " - " + exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });
        System.out.println("Collected " + result.size() + " files.");
        return result;
    }
}