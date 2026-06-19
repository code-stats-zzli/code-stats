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
import java.util.stream.Collectors;

/**
 * 依赖分析阶段：并行分析文件列表，构建完整的依赖图
 * 修复：增加超时控制和健壮的中断处理
 */
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

        // 并行分析每个文件
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<ExtractedResult>> futures = new ArrayList<>();

        for (Path file : files) {
            futures.add(executor.submit(() -> {
                try {
                    return extractor.extract(file);
                } catch (IOException e) {
                    log.error("解析文件失败: {}", file, e);
                    return new ExtractedResult(null, Collections.emptySet(), null);
                }
            }));
        }

        // 合并结果
        Map<String, ClassInfo> allClassInfo = new HashMap<>();
        DependencyGraph globalGraph = new DependencyGraph();

        // 增加超时控制，避免无限等待
        int totalTasks = futures.size();
        int completed = 0;
        for (Future<ExtractedResult> future : futures) {
            try {
                // 每个任务最多等待 30 秒，防止单个文件卡死
                ExtractedResult result = future.get(30, TimeUnit.SECONDS);
                completed++;
                if (result.getClassInfo() != null) {
                    ClassInfo info = result.getClassInfo();
                    allClassInfo.put(info.getFullyQualifiedName(), info);
                    for (String dep : result.getDependencies()) {
                        globalGraph.addDependency(info.getFullyQualifiedName(), dep);
                    }
                }
            } catch (InterruptedException e) {
                // 线程被中断，停止等待并退出
                Thread.currentThread().interrupt();
                log.warn("依赖分析被中断，已处理 {}/{} 个文件", completed, totalTasks);
                break;
            } catch (ExecutionException e) {
                log.error("解析任务执行异常", e);
            } catch (TimeoutException e) {
                log.warn("单个文件解析超时 (30s)，已处理 {}/{} 个文件", completed, totalTasks);
                future.cancel(true); // 取消该任务
            }
        }

        // 关闭线程池并等待所有任务结束（剩余任务将被中断）
        executor.shutdownNow(); // 立即中断未完成的任务
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