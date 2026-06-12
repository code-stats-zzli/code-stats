package com.omni.business.spider.service;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spider.config.SpiderConfig;
import com.omni.framework.spider.core.Crawler;
import com.omni.framework.spider.model.Article;
import com.omni.framework.spring.annotation.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class SpiderTaskService {

    private static final Logger log = LoggerFactory.getLogger(SpiderTaskService.class);
    private final Map<String, TaskContext> tasks = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * 任务上下文，包含实时状态信息，并对外提供更新方法（供 Crawler 直接调用）
     */
    public static class TaskContext implements Crawler.StatusUpdater{
        final String taskId;
        final String username;
        volatile String state;          // RUNNING, COMPLETED, ERROR
        volatile int totalArticles;
        volatile List<Article> articles = new ArrayList<>();
        volatile String mergedFilePath;
        volatile Map<String, Object> authorInfo = new HashMap<>();
        volatile String errorMessage;
        long startTime;

        // 实时信息
        volatile String currentStep;    // 当前步骤描述
        volatile String currentUrl;     // 正在处理的文章URL
        volatile int processedCount;    // 已成功下载的文章数

        TaskContext(String taskId, String username) {
            this.taskId = taskId;
            this.username = username;
            this.state = "RUNNING";
            this.startTime = System.currentTimeMillis();
            this.currentStep = "初始化";
            this.processedCount = 0;
        }

        /**
         * 供 Crawler 调用的状态更新方法
         * @param step      当前步骤
         * @param url       正在处理的URL
         * @param article   新完成的文章（可为null）
         */
        public synchronized void updateStatus(String step, String url, Article article) {
            if (step != null) this.currentStep = step;
            if (url != null) this.currentUrl = url;
            if (article != null) {
                this.articles.add(article);
                this.processedCount = this.articles.size();
                this.totalArticles = this.processedCount;
            }
        }

        public Map<String, Object> toStatusMap() {
            Map<String, Object> status = new HashMap<>();
            status.put("state", state);
            status.put("totalArticles", totalArticles);
            status.put("articles", articles);
            status.put("authorInfo", authorInfo);
            status.put("mergedFilePath", mergedFilePath);
            status.put("errorMessage", errorMessage);
            status.put("currentStep", currentStep);
            status.put("currentUrl", currentUrl);
            status.put("processedCount", processedCount);
            return status;
        }
    }

    /**
     * 启动爬虫任务
     */
    public String startTask(String username, int maxConcurrent, long intervalMs,
                            boolean mergeEnabled, String mergeFileName,
                            int maxArticles, int maxTimeSeconds) {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        TaskContext ctx = new TaskContext(taskId, username);
        tasks.put(taskId, ctx);

        executor.submit(() -> {
            try {
                SpiderConfig config = new SpiderConfig.Builder()
                        .username(username)
                        .maxConcurrent(maxConcurrent)
                        .intervalMs(intervalMs)
                        .outputDir("./spider_output/" + taskId)
                        .mergeEnabled(mergeEnabled)
                        .mergeFileName(mergeFileName)
                        .maxArticles(maxArticles)
                        .maxTimeSeconds(maxTimeSeconds)
                        .build();

                // 创建 Crawler 时传入 TaskContext 引用（Crawler 不依赖 SpiderTaskService）
                Crawler crawler = new Crawler(config, ctx);
                crawler.start();

                ctx.mergedFilePath = crawler.getMergeFilePath();
                // 从输出目录加载作者信息（可选）
                loadAuthorInfo(ctx, username);
                ctx.state = "COMPLETED";
                ctx.currentStep = "爬取完成";
            } catch (Exception e) {
                log.error("爬虫任务失败: " + taskId, e);
                ctx.state = "ERROR";
                ctx.errorMessage = e.getMessage();
                ctx.currentStep = "失败: " + e.getMessage();
            }
        });
        return taskId;
    }

    /**
     * 加载作者信息（从已保存的文章中提取）
     */
    private void loadAuthorInfo(TaskContext ctx, String username) throws IOException {
        Path outputDir = Paths.get("./spider_output/" + ctx.taskId);
        if (!Files.exists(outputDir)) return;

        List<Path> txtFiles;
        try (var stream = Files.list(outputDir)) {
            txtFiles = stream.filter(p -> p.toString().endsWith(".txt")).collect(Collectors.toList());
        }
        if (!txtFiles.isEmpty()) {
            // 尝试从第一篇文章中读取作者
            String firstContent = Files.readString(txtFiles.get(0));
            String author = extractAuthor(firstContent);
            ctx.authorInfo.put("name", author != null ? author : username);
            ctx.authorInfo.put("nickname", author != null ? author : username);
            ctx.authorInfo.put("description", "CSDN 博主");
        } else {
            ctx.authorInfo.put("name", username);
            ctx.authorInfo.put("nickname", username);
            ctx.authorInfo.put("description", "CSDN 博主");
        }
    }

    private String extractAuthor(String content) {
        // 简单从文本中提取作者行
        for (String line : content.split("\n")) {
            if (line.startsWith("作者：")) {
                return line.substring(3).trim();
            }
        }
        return null;
    }

    /**
     * 获取任务状态
     */
    public Map<String, Object> getTaskStatus(String taskId) {
        TaskContext ctx = tasks.get(taskId);
        return ctx == null ? null : ctx.toStatusMap();
    }

    /**
     * 获取文章列表
     */
    public List<Article> getArticles(String taskId) {
        TaskContext ctx = tasks.get(taskId);
        return ctx == null ? null : ctx.articles;
    }

    /**
     * 获取合并文件路径
     */
    public String getMergedFilePath(String taskId) {
        TaskContext ctx = tasks.get(taskId);
        return ctx == null ? null : ctx.mergedFilePath;
    }

    /**
     * 清理所有已完成的任务（可选，避免内存泄漏）
     */
    public void cleanupExpiredTasks(long maxAgeMillis) {
        long now = System.currentTimeMillis();
        tasks.entrySet().removeIf(entry -> {
            TaskContext ctx = entry.getValue();
            return ("COMPLETED".equals(ctx.state) || "ERROR".equals(ctx.state))
                    && (now - ctx.startTime > maxAgeMillis);
        });
    }
}