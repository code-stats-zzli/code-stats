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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 爬虫核心引擎，支持实时状态回调（解耦设计）
 */
public class Crawler {
    private static final Logger log = LoggerFactory.getLogger(Crawler.class);

    /**
     * 状态更新回调接口（供外部实现，用于实时展示进度）
     */
    public interface StatusUpdater {
        void updateStatus(String step, String url, Article article);
        // 新增：支持失败信息上报
        default void updateStatus(String step, String url, String error) {
            // 默认空实现，不强制子类实现
        }
    }

    private final SpiderConfig config;
    private final Storage storage;
    private final ExecutorService executor;
    private final Set<String> downloadedUrls = new HashSet<>();
    private String mergeFilePath;
    private volatile boolean stop = false;
    private final long startTime;
    private final StatusUpdater statusUpdater;

    public Crawler(SpiderConfig config, StatusUpdater statusUpdater) throws IOException {
        this.config = config;
        this.statusUpdater = statusUpdater;
        this.storage = new FileStorage(config.getOutputDir());
        this.executor = Executors.newFixedThreadPool(config.getMaxConcurrent());
        this.startTime = System.currentTimeMillis();
        loadDownloadedUrls();
    }

    public Crawler(SpiderConfig config) throws IOException {
        this(config, null);
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
        if (config.getMaxArticles() > 0 && currentArticleCount >= config.getMaxArticles()) {
            log.info("已达到最大文章数限制: {}", config.getMaxArticles());
            return true;
        }
        if (config.getMaxTimeSeconds() > 0) {
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            if (elapsed >= config.getMaxTimeSeconds()) {
                log.info("已达到最大时间限制: {} 秒", config.getMaxTimeSeconds());
                return true;
            }
        }
        return false;
    }

    private void updateStatus(String step, String url, Article article) {
        if (statusUpdater != null) {
            statusUpdater.updateStatus(step, url, article);
        }
    }

    private void updateStatus(String step, String url, String error) {
        if (statusUpdater != null) {
            statusUpdater.updateStatus(step, url, error);
        }
    }

    public void start() {
        log.info("开始爬取 CSDN 作者: {}", config.getCsdnUsername());
        String blogIndexUrl = "https://blog.csdn.net/" + config.getCsdnUsername();
        String currentUrl = blogIndexUrl;
        List<Future<Article>> futures = new ArrayList<>();
        int submittedCount = 0;

        updateStatus("正在获取博客文章列表", blogIndexUrl, (Article) null);

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
                if (shouldStop(submittedCount)) {
                    stop = true;
                    break;
                }
                ArticleProcessor processor = new ArticleProcessor(url, storage, config.getRequestIntervalMs(), statusUpdater);
                futures.add(executor.submit(processor));
                submittedCount++;
            }

            if (stop) break;
            currentUrl = PageParser.parseNextPageUrl(listHtml, currentUrl);
            try { Thread.sleep(config.getRequestIntervalMs()); } catch (InterruptedException e) { break; }
        }

        for (Future<Article> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                log.error("文章下载失败", e);
                // 注意：异常已经在 processor 中捕获并上报，此处不再重复上报
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
        updateStatus("爬取完成", null, (Article) null);
        log.info("爬取完成，共下载 {} 篇文章", futures.size());
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