package com.omni.business.assistance.spider.dto;


public class StartTaskRequest {

    private String username;


    private int maxConcurrent = 3;


    private long intervalMs = 1500;

    private boolean mergeEnabled = true;

    private String mergeFileName = "merged.txt";


    private int maxArticles = 100;

    private int maxTimeSeconds = 0;

    // getters and setters
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public int getMaxConcurrent() { return maxConcurrent; }
    public void setMaxConcurrent(int maxConcurrent) { this.maxConcurrent = maxConcurrent; }
    public long getIntervalMs() { return intervalMs; }
    public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }
    public boolean isMergeEnabled() { return mergeEnabled; }
    public void setMergeEnabled(boolean mergeEnabled) { this.mergeEnabled = mergeEnabled; }
    public String getMergeFileName() { return mergeFileName; }
    public void setMergeFileName(String mergeFileName) { this.mergeFileName = mergeFileName; }
    public int getMaxArticles() { return maxArticles; }
    public void setMaxArticles(int maxArticles) { this.maxArticles = maxArticles; }
    public int getMaxTimeSeconds() { return maxTimeSeconds; }
    public void setMaxTimeSeconds(int maxTimeSeconds) { this.maxTimeSeconds = maxTimeSeconds; }
}