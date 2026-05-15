package com.omni.framework.codestats.pipeline;

import com.omni.framework.codestats.config.Config;
import com.omni.framework.codestats.core.ProgressMonitor;
import com.omni.framework.codestats.filter.CompositeFilter;
import com.omni.framework.codestats.filter.ExcludePathFilter;
import com.omni.framework.codestats.filter.ExtensionFilter;
import com.omni.framework.codestats.filter.GitIgnoreFilter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Pipeline<T, R> {
    private final List<Stage> stages = new ArrayList<>();

    // 用于向 OutputStage 传递管道开始时间（同一线程顺序执行，安全）
    static final ThreadLocal<Long> START_TIME_HOLDER = new ThreadLocal<>();

    public Pipeline<T, R> addStage(Stage<?, ?> stage) {
        stages.add(stage);
        return this;
    }

    @SuppressWarnings("unchecked")
    public R execute(T input) {
        // 记录管道开始时间
        START_TIME_HOLDER.set(System.currentTimeMillis());
        try {
            Object current = input;
            for (Stage stage : stages) {
                current = stage.process(current);
            }
            return (R) current;
        } catch (Exception e) {
            throw new RuntimeException("Pipeline execution failed", e);
        } finally {
            START_TIME_HOLDER.remove();
        }
    }

    public static Pipeline<Path, Void> createDefault(Config config) {
        // 构建过滤器链
        CompositeFilter filterChain = new CompositeFilter();
        if (config.getExtensions() != null && !config.getExtensions().isEmpty()) {
            filterChain.add(new ExtensionFilter(config.getExtensions()));
        }
        if (config.getExcludePaths() != null && !config.getExcludePaths().isEmpty()) {
            filterChain.add(new ExcludePathFilter(config.getExcludePaths()));
        }
        if (config.isUseGitIgnore()) {
            filterChain.add(new GitIgnoreFilter(config.getRootDir()));
        }

        Pipeline<Path, Void> pipeline = new Pipeline<>();
        pipeline.addStage(new CollectStage(filterChain));
        ProgressMonitor monitor = null; // 控制台模式不绑定进度条
        pipeline.addStage(new ParallelAnalyzeStage(config.getThreadCount(), config.isExcludeComments(), monitor));
        pipeline.addStage(new AggregateStage(config.getSortBy()));
        try {
            pipeline.addStage(new OutputStage(config));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return pipeline;
    }
}