package com.omni.framework.codestats.pipeline;

import com.omni.framework.codestats.core.FileInfo;
import com.omni.framework.codestats.core.ProgressMonitor;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class ParallelAnalyzeStage implements Stage<List<Path>, List<FileInfo>> {
    private final ExecutorService executor;
    private final AnalyzeStage analyzeStage;
    private final ProgressMonitor monitor;

    public ParallelAnalyzeStage(int threads, boolean excludeComments, ProgressMonitor monitor) {
        this.executor = Executors.newFixedThreadPool(threads);
        this.analyzeStage = new AnalyzeStage(excludeComments);
        this.monitor = monitor;
    }

    @Override
    public List<FileInfo> process(List<Path> paths) {
        List<CompletableFuture<FileInfo>> futures = paths.stream()
                .map(path -> CompletableFuture.supplyAsync(() -> {
                    FileInfo info = analyzeStage.process(path);
                    // 使用新的携带文件路径的通知方法（若monitor非空）
                    if (monitor != null) {
                        monitor.incrementWithFile(path);
                    }
                    return info;
                }, executor))
                .collect(Collectors.toList());
        List<FileInfo> result = futures.stream().map(CompletableFuture::join).collect(Collectors.toList());
        executor.shutdown();
        return result;
    }
}