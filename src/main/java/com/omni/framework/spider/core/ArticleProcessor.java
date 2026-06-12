package com.omni.framework.spider.core;

import com.omni.framework.spider.model.Article;
import com.omni.framework.spider.storage.Storage;
import com.omni.framework.spider.util.HttpUtil;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.util.concurrent.Callable;

public class ArticleProcessor implements Callable<Article> {
    private static final Logger log = LoggerFactory.getLogger(ArticleProcessor.class);
    private final String articleUrl;
    private final Storage storage;
    private final long intervalMs;

    public ArticleProcessor(String articleUrl, Storage storage, long intervalMs) {
        this.articleUrl = articleUrl;
        this.storage = storage;
        this.intervalMs = intervalMs;
    }

    @Override
    public Article call() throws Exception {
        // 控制请求频率
        Thread.sleep(intervalMs);
        log.info("开始下载文章: {}", articleUrl);
        String html = HttpUtil.fetchWithPlaywright(articleUrl);
        if (html == null) {
            log.warn("下载失败: {}", articleUrl);
            return null;
        }
        Article article = PageParser.parseArticle(html, articleUrl);
        if (article != null && article.getContent() != null && !article.getContent().isEmpty()) {
            storage.save(article);
            log.info("文章已保存: {}", article.getTitle());
        }
        return article;
    }
}