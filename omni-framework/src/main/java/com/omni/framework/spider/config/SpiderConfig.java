package com.omni.framework.spider.config;

public class SpiderConfig {
    private String csdnUsername;
    private int maxConcurrent = 3;
    private long requestIntervalMs = 1000;
    private String outputDir = "./csdn_articles";
    private boolean mergeEnabled = true;
    private String mergeFileName = "all_articles.txt";
    // 新增字段
    private int maxArticles = 100;        // 最大文章数限制，默认100
    private int maxTimeSeconds = 0;       // 最大爬取时间（秒），0表示不限制

    private SpiderConfig(Builder builder) {
        this.csdnUsername = builder.csdnUsername;
        this.maxConcurrent = builder.maxConcurrent;
        this.requestIntervalMs = builder.requestIntervalMs;
        this.outputDir = builder.outputDir;
        this.mergeEnabled = builder.mergeEnabled;
        this.mergeFileName = builder.mergeFileName;
        this.maxArticles = builder.maxArticles;
        this.maxTimeSeconds = builder.maxTimeSeconds;
    }

    public static class Builder {
        private String csdnUsername;
        private int maxConcurrent = 3;
        private long requestIntervalMs = 1000;
        private String outputDir = "./csdn_articles";
        private boolean mergeEnabled = true;
        private String mergeFileName = "all_articles.txt";
        private int maxArticles = 100;
        private int maxTimeSeconds = 0;

        public Builder username(String username) { this.csdnUsername = username; return this; }
        public Builder maxConcurrent(int max) { this.maxConcurrent = max; return this; }
        public Builder intervalMs(long ms) { this.requestIntervalMs = ms; return this; }
        public Builder outputDir(String dir) { this.outputDir = dir; return this; }
        public Builder mergeEnabled(boolean enabled) { this.mergeEnabled = enabled; return this; }
        public Builder mergeFileName(String name) { this.mergeFileName = name; return this; }
        public Builder maxArticles(int max) { this.maxArticles = max; return this; }
        public Builder maxTimeSeconds(int seconds) { this.maxTimeSeconds = seconds; return this; }

        public SpiderConfig build() { return new SpiderConfig(this); }
    }

    // getters
    public String getCsdnUsername() { return csdnUsername; }
    public int getMaxConcurrent() { return maxConcurrent; }
    public long getRequestIntervalMs() { return requestIntervalMs; }
    public String getOutputDir() { return outputDir; }
    public boolean isMergeEnabled() { return mergeEnabled; }
    public String getMergeFileName() { return mergeFileName; }
    public int getMaxArticles() { return maxArticles; }
    public int getMaxTimeSeconds() { return maxTimeSeconds; }
}