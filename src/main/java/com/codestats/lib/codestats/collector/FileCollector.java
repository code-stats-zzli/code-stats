package com.codestats.lib.codestats.collector;

import com.codestats.lib.codestats.filter.CompositeFilter;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class FileCollector {
    private final CompositeFilter filterChain;

    public FileCollector(CompositeFilter filterChain) {
        this.filterChain = filterChain;
    }

    public ConcurrentLinkedQueue<Path> collect(Path rootDir) throws IOException {
        ConcurrentLinkedQueue<Path> queue = new ConcurrentLinkedQueue<>();
        AtomicInteger fileCount = new AtomicInteger(0);
        Files.walkFileTree(rootDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (filterChain.accept(file)) {
                    queue.add(file);
                    fileCount.incrementAndGet();
                }
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                System.err.println("Skip file: " + file + " - " + exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });
        System.out.println("Collected " + fileCount.get() + " files.");
        return queue;
    }
}