package com.omni.framework.codestats.pipeline;

import com.omni.framework.codestats.dependency.extractor.JavaDependencyExtractor;
import com.omni.framework.codestats.dependency.model.ClassInfo;
import com.omni.framework.codestats.dependency.model.DependencyGraph;
import com.omni.framework.codestats.dependency.model.DependencyResult;
import com.omni.framework.codestats.dependency.model.ExtractedResult;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

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
        Map<String, ClassInfo> allClassInfo = new HashMap<>();
        DependencyGraph globalGraph = new DependencyGraph();
        List<Future<ExtractedResult>> futures = new ArrayList<>();
        List<String> failedFiles = Collections.synchronizedList(new ArrayList<>());

        for (Path file : files) {
            futures.add(executor.submit(() -> {
                if (Thread.currentThread().isInterrupted()) return null;
                try {
                    return extractor.extract(file);
                } catch (Exception e) {
                    failedFiles.add(file + " : " + e.getClass().getSimpleName() + " - " + e.getMessage());
                    return null;
                }
            }));
        }

        int completed = 0;
        for (int i = 0; i < futures.size(); i++) {
            Future<ExtractedResult> f = futures.get(i);
            try {
                ExtractedResult r = f.get(60, TimeUnit.SECONDS);
                if (r != null && r.getClassInfo() != null) {
                    ClassInfo info = r.getClassInfo();
                    allClassInfo.put(info.getFullyQualifiedName(), info);
                    for (String dep : r.getDependencies()) {
                        globalGraph.addDependency(info.getFullyQualifiedName(), dep);
                    }
                }
                completed++;
            } catch (TimeoutException e) {
                f.cancel(true);
                failedFiles.add(files.get(i) + " : 解析超时(60s)");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException e) {
                failedFiles.add(files.get(i) + " : " + e.getCause());
            }
        }

        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) log.warn("线程池未在 5 秒内终止");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("依赖分析完成，共 {} 个文件，成功 {} 个，失败 {} 个，耗时 {} ms",
                files.size(), completed, failedFiles.size(), elapsed);
        DependencyResult result = new DependencyResult(allClassInfo, globalGraph, elapsed);
        result.setFailedFiles(failedFiles);
        return result;
    }
}