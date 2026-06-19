package com.omni.framework.codestats.pipeline;

import com.omni.framework.codestats.dependency.extractor.JavaDependencyExtractor;
import com.omni.framework.codestats.dependency.model.ClassInfo;
import com.omni.framework.codestats.dependency.model.DependencyGraph;
import com.omni.framework.codestats.dependency.model.DependencyResult;
import com.omni.framework.codestats.dependency.model.ExtractedResult;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class DependencyAnalysisStage implements Stage<List<Path>, DependencyResult> {
    private static final Logger log = LoggerFactory.getLogger(DependencyAnalysisStage.class);

    private final int threadCount;
    private final JavaDependencyExtractor extractor;

    public DependencyAnalysisStage(int threadCount) {
        this.threadCount = threadCount > 0 ? threadCount : Runtime.getRuntime().availableProcessors();
        this.extractor = new JavaDependencyExtractor();
    }

    @Override
    public DependencyResult process(List<Path> files) throws Exception {
        if (files == null || files.isEmpty()) {
            return new DependencyResult(Collections.emptyMap(), new DependencyGraph(), 0L);
        }

        long startTime = System.currentTimeMillis();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<ExtractedResult>> futures = new ArrayList<>();

        for (Path file : files) {
            futures.add(executor.submit(() -> {
                try {
                    return extractor.extract(file);
                } catch (IOException e) {
                    log.error("解析文件失败: {}", file, e);
                    return null;  // 返回 null 表示失败
                }
            }));
        }

        Map<String, ClassInfo> allClassInfo = new HashMap<>();
        DependencyGraph globalGraph = new DependencyGraph();

        int totalTasks = futures.size();
        int completed = 0;
        for (Future<ExtractedResult> future : futures) {
            try {
                ExtractedResult result = future.get(30, TimeUnit.SECONDS);
                completed++;
                if (result != null && result.getClassInfo() != null) {
                    ClassInfo info = result.getClassInfo();
                    allClassInfo.put(info.getFullyQualifiedName(), info);
                    for (String dep : result.getDependencies()) {
                        // ★ 防御：addDependency 内部已做 null 检查
                        globalGraph.addDependency(info.getFullyQualifiedName(), dep);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("依赖分析被中断，已处理 {}/{} 个文件", completed, totalTasks);
                break;
            } catch (ExecutionException e) {
                log.error("解析任务执行异常", e);
            } catch (TimeoutException e) {
                log.warn("单个文件解析超时 (30s)，已处理 {}/{} 个文件", completed, totalTasks);
                future.cancel(true);
            }
        }

        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("线程池未在 5 秒内完全终止");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("依赖分析完成，共分析 {} 个文件，成功 {} 个，耗时 {} ms", files.size(), completed, elapsed);
        return new DependencyResult(allClassInfo, globalGraph, elapsed);
    }
}