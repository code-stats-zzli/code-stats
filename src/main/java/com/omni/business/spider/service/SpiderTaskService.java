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
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class SpiderTaskService {

    private static final Logger log = LoggerFactory.getLogger(SpiderTaskService.class);
    private final Map<String, TaskContext> tasks = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private static class TaskContext {
        final String taskId;
        final String username;
        volatile String state;          // RUNNING, COMPLETED, ERROR
        volatile int totalArticles;
        volatile List<Article> articles = new ArrayList<>();
        volatile String mergedFilePath;
        volatile Map<String, Object> authorInfo = new HashMap<>();
        volatile String errorMessage;
        long startTime;

        TaskContext(String taskId, String username) {
            this.taskId = taskId;
            this.username = username;
            this.state = "RUNNING";
            this.startTime = System.currentTimeMillis();
        }
    }

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
                Crawler crawler = new Crawler(config);
                crawler.start();
                ctx.mergedFilePath = crawler.getMergeFilePath();

                Path outputDir = Paths.get("./spider_output/" + taskId);
                if (Files.exists(outputDir)) {
                    List<Article> articles = new ArrayList<>();
                    try (var stream = Files.list(outputDir)) {
                        List<Path> txtFiles = stream.filter(p -> p.toString().endsWith(".txt")).collect(Collectors.toList());
                        for (Path file : txtFiles) {
                            String content = Files.readString(file);
                            Article art = parseArticleFromFile(file.getFileName().toString(), content);
                            if (art != null) articles.add(art);
                        }
                    }
                    ctx.articles = articles;
                    ctx.totalArticles = articles.size();
                    if (!articles.isEmpty()) {
                        ctx.authorInfo.put("name", articles.get(0).getAuthor());
                        ctx.authorInfo.put("nickname", articles.get(0).getAuthor());
                        ctx.authorInfo.put("description", "CSDN 博主");
                    }
                    ctx.state = "COMPLETED";
                } else {
                    ctx.state = "ERROR";
                    ctx.errorMessage = "输出目录不存在";
                }
            } catch (Exception e) {
                log.error("爬虫任务失败: " + taskId, e);
                ctx.state = "ERROR";
                ctx.errorMessage = e.getMessage();
            }
        });
        return taskId;
    }

    private Article parseArticleFromFile(String fileName, String content) {
        Article art = new Article();
        String[] lines = content.split("\n");
        for (String line : lines) {
            if (line.startsWith("标题：")) art.setTitle(line.substring(3).trim());
            else if (line.startsWith("链接：")) art.setUrl(line.substring(3).trim());
            else if (line.startsWith("作者：")) art.setAuthor(line.substring(3).trim());
            else if (line.startsWith("阅读数：")) {
                try { art.setViewCount(Integer.parseInt(line.substring(5).trim())); } catch (Exception e) {}
            }
        }
        art.setContent(content);
        if (art.getTitle() == null) art.setTitle(fileName);
        return art;
    }

    public Map<String, Object> getTaskStatus(String taskId) {
        TaskContext ctx = tasks.get(taskId);
        if (ctx == null) return null;
        Map<String, Object> status = new HashMap<>();
        status.put("state", ctx.state);
        status.put("totalArticles", ctx.totalArticles);
        status.put("articles", ctx.articles);
        status.put("authorInfo", ctx.authorInfo);
        status.put("mergedFilePath", ctx.mergedFilePath);
        status.put("errorMessage", ctx.errorMessage);
        return status;
    }

    public List<Article> getArticles(String taskId) {
        TaskContext ctx = tasks.get(taskId);
        return ctx == null ? null : ctx.articles;
    }

    public String getMergedFilePath(String taskId) {
        TaskContext ctx = tasks.get(taskId);
        return ctx == null ? null : ctx.mergedFilePath;
    }
}