package com.omni.framework.spider.core;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spider.model.Article;
import com.omni.framework.spider.storage.Storage;
import com.omni.framework.spider.util.HttpUtil;

import java.util.concurrent.Callable;

public class ArticleProcessor implements Callable<Article> {
    private static final Logger log = LoggerFactory.getLogger(ArticleProcessor.class);
    private final String articleUrl;
    private final Storage storage;
    private final long intervalMs;
    private final Crawler.StatusUpdater statusUpdater;

    public ArticleProcessor(String articleUrl, Storage storage, long intervalMs, Crawler.StatusUpdater statusUpdater) {
        this.articleUrl = articleUrl;
        this.storage = storage;
        this.intervalMs = intervalMs;
        this.statusUpdater = statusUpdater;
    }

    private void updateStatus(String step, String url, Article article) {
        if (statusUpdater != null) {
            statusUpdater.updateStatus(step, url, article);
        }
    }

    @Override
    public Article call() throws Exception {
        Thread.sleep(intervalMs);
        log.info("开始下载文章: {}", articleUrl);
        updateStatus("正在下载文章", articleUrl, null);

        String html = HttpUtil.fetchWithPlaywright(articleUrl);
        if (html == null) {
            log.warn("下载失败: {}", articleUrl);
            updateStatus("下载失败", articleUrl, null);
            return null;
        }
        Article article = PageParser.parseArticle(html, articleUrl);
        if (article != null && article.getContent() != null && !article.getContent().isEmpty()) {
            storage.save(article);
            log.info("文章已保存: {}", article.getTitle());
            updateStatus("已保存", articleUrl, article);
        } else {
            updateStatus("保存失败（内容为空）", articleUrl, null);
        }
        return article;
    }
}