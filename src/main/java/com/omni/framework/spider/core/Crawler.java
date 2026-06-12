package com.omni.framework.spider.core;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spider.config.SpiderConfig;
import com.omni.framework.spider.model.Article;
import com.omni.framework.spider.pipeline.MergePipeline;
import com.omni.framework.spider.storage.FileStorage;
import com.omni.framework.spider.storage.Storage;
import com.omni.framework.spider.util.HttpUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class Crawler {
    private static final Logger log = LoggerFactory.getLogger(Crawler.class);
    private final SpiderConfig config;
    private final Storage storage;
    private final ExecutorService executor;
    private final Set<String> downloadedUrls = new HashSet<>();
    private String mergeFilePath;
    private volatile boolean stop = false;          // 停止标志
    private final long startTime;                   // 爬取开始时间戳

    public Crawler(SpiderConfig config) throws IOException {
        this.config = config;
        this.storage = new FileStorage(config.getOutputDir());
        this.executor = Executors.newFixedThreadPool(config.getMaxConcurrent());
        this.startTime = System.currentTimeMillis();
        loadDownloadedUrls();
    }

    private void loadDownloadedUrls() throws IOException {
        Path dir = Paths.get(config.getOutputDir());
        if (Files.exists(dir) && Files.isDirectory(dir)) {
            try (var stream = Files.list(dir)) {
                downloadedUrls.addAll(stream.filter(p -> p.toString().endsWith(".txt"))
                        .map(p -> p.getFileName().toString().replace(".txt", ""))
                        .collect(Collectors.toSet()));
            }
        }
    }

    private boolean shouldStop(int currentArticleCount) {
        // 检查文章数量限制
        if (config.getMaxArticles() > 0 && currentArticleCount >= config.getMaxArticles()) {
            log.info("已达到最大文章数限制: {}", config.getMaxArticles());
            return true;
        }
        // 检查时间限制
        if (config.getMaxTimeSeconds() > 0) {
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            if (elapsed >= config.getMaxTimeSeconds()) {
                log.info("已达到最大时间限制: {} 秒", config.getMaxTimeSeconds());
                return true;
            }
        }
        return false;
    }

    public void start() {
        log.info("开始爬取 CSDN 作者: {}", config.getCsdnUsername());
        String blogIndexUrl = "https://blog.csdn.net/" + config.getCsdnUsername();
        String currentUrl = blogIndexUrl;
        List<Future<Article>> futures = new ArrayList<>();
        int submittedCount = 0;

        while (currentUrl != null && !stop) {
            log.info("抓取列表页: {}", currentUrl);
            String listHtml = HttpUtil.fetchWithPlaywright(currentUrl);
            if (listHtml == null) break;

            List<String> articleUrls = PageParser.parseArticleUrls(listHtml, blogIndexUrl);
            for (String url : articleUrls) {
                if (stop) break;
                if (downloadedUrls.contains(extractFileNameFromUrl(url))) {
                    log.debug("已存在，跳过: {}", url);
                    continue;
                }
                // 检查限制
                if (shouldStop(submittedCount)) {
                    stop = true;
                    break;
                }
                ArticleProcessor processor = new ArticleProcessor(url, storage, config.getRequestIntervalMs());
                futures.add(executor.submit(processor));
                submittedCount++;
            }

            if (stop) break;
            currentUrl = PageParser.parseNextPageUrl(listHtml, currentUrl);
            try { Thread.sleep(config.getRequestIntervalMs()); } catch (InterruptedException e) { break; }
        }

        // 等待所有已提交的任务完成
        for (Future<Article> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                log.error("文章下载失败", e);
            }
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.MINUTES)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        storage.close();

        if (config.isMergeEnabled()) {
            try {
                String merged = Paths.get(config.getOutputDir(), config.getMergeFileName()).toString();
                MergePipeline.merge(config.getOutputDir(), merged);
                this.mergeFilePath = merged;
                log.info("合并文件已生成: {}", merged);
            } catch (IOException e) {
                log.error("合并文件失败", e);
            }
        }
        log.info("爬取完成，共下载 {} 篇文章（已达到限制条件则提前结束）", futures.size());
    }

    private String extractFileNameFromUrl(String url) {
        String[] parts = url.split("/");
        String last = parts[parts.length-1];
        if (last.isEmpty() && parts.length>1) last = parts[parts.length-2];
        return last;
    }

    public String getMergeFilePath() {
        return mergeFilePath;
    }
}