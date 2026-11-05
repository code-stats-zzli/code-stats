package com.omni.framework.codestats.pipeline;

import com.omni.framework.codestats.core.FileInfo;
import com.omni.framework.codestats.core.ProgressMonitor;
import com.omni.framework.codestats.language.Language;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class ParallelAnalyzeStage implements Stage<List<Path>, List<FileInfo>> {
    private static final Logger log = LoggerFactory.getLogger(ParallelAnalyzeStage.class);

    private final ExecutorService executor;
    private final AnalyzeStage analyzeStage;
    private final ProgressMonitor monitor;

    public ParallelAnalyzeStage(int threads, boolean excludeComments, ProgressMonitor monitor) {
        this.executor = Executors.newFixedThreadPool(Math.max(1, threads));
        this.analyzeStage = new AnalyzeStage(excludeComments);
        this.monitor = monitor;
    }

    @Override
    public List<FileInfo> process(List<Path> paths) {
        List<CompletableFuture<FileInfo>> futures = paths.stream()
                .map(path -> CompletableFuture.supplyAsync(() -> {
                    try {
                        FileInfo info = analyzeStage.process(path);
                        if (monitor != null) monitor.incrementWithFile(path);
                        return info;
                    } catch (Exception e) {
                        // ★ [P1] 单文件失败不影响整体，返回占位 FileInfo
                        log.warn("分析文件失败: {}", path, e);
                        FileInfo err = new FileInfo(path);
                        err.setLanguage(Language.UNKNOWN);
                        if (monitor != null) monitor.incrementWithFile(path);
                        return err;
                    }
                }, executor))
                .collect(Collectors.toList());

        List<FileInfo> result = futures.stream()
                .map(f -> {
                    try { return f.join(); }
                    catch (Exception e) {
                        log.error("join 异常", e);
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

        executor.shutdown();
        return result;
    }
}