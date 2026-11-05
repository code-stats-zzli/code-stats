package com.omni.framework.spider.core;

import com.omni.framework.spider.model.Article;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

public class PageParser {

    /**
     * 从作者首页解析出所有文章链接（支持“加载更多”分页）
     * @param html 作者博客首页 HTML
     * @param baseUrl 当前页面的 URL（用于拼接相对路径）
     * @return 文章 URL 列表
     */
    public static List<String> parseArticleUrls(String html, String baseUrl) {
        List<String> urls = new ArrayList<>();
        Document doc = Jsoup.parse(html);

        // 新版 CSDN 个人主页：每个 article.blog-list-box 里的 a 标签
        Elements articleLinks = doc.select("article.blog-list-box > a");
        for (Element a : articleLinks) {
            String href = a.attr("href");
            if (href != null && !href.isEmpty()) {
                urls.add(absoluteUrl(href, baseUrl));
            }
        }
        return urls;
    }

    /**
     * 获取“加载更多”的下一页链接（CSDN 新版分页风格）
     * 例如：https://blog.csdn.net/qq_41652036/article/list/2
     */
    public static String parseNextPageUrl(String html, String currentUrl) {
        Document doc = Jsoup.parse(html);
        // 查找“加载更多”按钮（通常是一个包含 /article/list/ 的链接）
        Element loadMore = doc.select("div.item-loading a[href*='/article/list/']").first();
        if (loadMore != null) {
            String href = loadMore.attr("href");
            return absoluteUrl(href, currentUrl);
        }
        return null;
    }

    /**
     * 解析单篇文章详情页
     * @param html 文章页面 HTML
     * @param url 文章 URL
     * @return Article 对象
     */
    public static Article parseArticle(String html, String url) {
        Document doc = Jsoup.parse(html);
        Article article = new Article();
        article.setUrl(url);

        // 标题：常见的有 #articleContentId 或 h1.title-article
        Element titleElem = doc.select("#articleContentId, h1.title-article, .article-title").first();
        article.setTitle(titleElem != null ? titleElem.text() : "无标题");

        // 作者：多种可能
        Element authorElem = doc.select(".user-name, .profile-name, .follow-nickName, .blog-name").first();
        if (authorElem == null) {
            authorElem = doc.select("meta[name=author]").first();
        }
        if (authorElem != null) {
            String author = authorElem.hasAttr("content") ? authorElem.attr("content") : authorElem.text();
            article.setAuthor(author);
        }

        // 正文内容
        Element contentElem = doc.select("#content_views, .article_content, .blog-content-box").first();
        if (contentElem != null) {
            // 移除干扰元素：脚本、样式、广告、折叠块
            contentElem.select("script, style, .hide-article-box, .recommend-box, .ad-container").remove();
            article.setContent(contentElem.text()); // 纯文本；若需要 HTML 可改用 .html()
        } else {
            article.setContent("");
        }

        // 阅读数（可选）
        Element readElem = doc.select(".read-count, .view-num, .articleInfo-read").first();
        if (readElem != null) {
            try {
                int views = Integer.parseInt(readElem.text().replaceAll("[^0-9]", ""));
                article.setViewCount(views);
            } catch (Exception ignored) {}
        }

        return article;
    }

    // 辅助方法：将相对路径 / 协议相对路径转为绝对 URL
    private static String absoluteUrl(String href, String baseUrl) {
        if (href.startsWith("//")) {
            return "https:" + href;
        } else if (!href.startsWith("http")) {
            // 如果 baseUrl 以 / 结尾，注意处理
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            if (href.startsWith("/")) {
                // 提取域名部分
                String domain = baseUrl.replaceAll("(https?://[^/]+).*", "$1");
                return domain + href;
            } else {
                // 相对当前目录
                return baseUrl + "/" + href;
            }
        }
        return href;
    }
}