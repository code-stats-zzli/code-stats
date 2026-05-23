package     com.omni.framework.codestats.pipeline;

import com.omni.framework.codestats.dependency.extractor.JavaDependencyExtractor;
import com.omni.framework.codestats.dependency.model.ClassInfo;
import com.omni.framework.codestats.dependency.model.DependencyGraph;
import com.omni.framework.codestats.dependency.model.DependencyResult;
import com.omni.framework.codestats.dependency.model.ExtractedResult;
import com.omni.framework.codestats.pipeline.Stage;
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
                    // 返回空结果，不影响其他文件
                    return new ExtractedResult(null, Collections.emptySet(), null);
                }
            }));
        }

        // 合并结果
        Map<String, ClassInfo> allClassInfo = new HashMap<>();
        DependencyGraph globalGraph = new DependencyGraph();

        for (Future<ExtractedResult> future : futures) {
            try {
                ExtractedResult result = future.get();
                if (result.getClassInfo() != null) {
                    ClassInfo info = result.getClassInfo();
                    allClassInfo.put(info.getFullyQualifiedName(), info);
                    for (String dep : result.getDependencies()) {
                        globalGraph.addDependency(info.getFullyQualifiedName(), dep);
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
                log.error("等待分析结果失败", e);
                Thread.currentThread().interrupt();
            }
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("依赖分析完成，共分析 {} 个文件，耗时 {} ms", files.size(), elapsed);
        return new DependencyResult(allClassInfo, globalGraph, elapsed);
    }
}