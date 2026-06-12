package com.omni.framework.spider.model;

import java.time.LocalDateTime;

public class Article {
    private String title;
    private String url;
    private String content;      // 文章正文（Markdown或HTML）
    private String author;
    private LocalDateTime publishTime;
    private int viewCount;
    private int commentCount;

    // 全参构造、getter/setter（使用 Lombok 或手动生成）
    public Article() {}
    public Article(String title, String url, String content, String author) {
        this.title = title;
        this.url = url;
        this.content = content;
        this.author = author;
    }

    // getters and setters...
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public LocalDateTime getPublishTime() { return publishTime; }
    public void setPublishTime(LocalDateTime publishTime) { this.publishTime = publishTime; }
    public int getViewCount() { return viewCount; }
    public void setViewCount(int viewCount) { this.viewCount = viewCount; }
    public int getCommentCount() { return commentCount; }
    public void setCommentCount(int commentCount) { this.commentCount = commentCount; }

    @Override
    public String toString() {
        return String.format("[%s] %s (%s)", author, title, url);
    }
}