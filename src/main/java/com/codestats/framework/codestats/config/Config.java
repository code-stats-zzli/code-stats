package com.codestats.framework.codestats.config;

import java.nio.file.Path;
import java.util.List;

public class Config {
    private final Path rootDir;
    private final List<String> extensions;
    private final List<String> excludePaths;
    private final boolean excludeComments;
    private final int threadCount;
    private final String sortBy;
    private final String outputFormat;
    private final Path outputFile;
    private final boolean useGitIgnore;
    private final Path defaultInputPath;
    private final Path defaultOutputPath;
    private final List<String> extraPaths;   // ★ 新增

    private Config(Builder builder) {
        this.rootDir = builder.rootDir;
        this.extensions = builder.extensions != null ? builder.extensions : new java.util.ArrayList<String>(){{add("java");}};
        this.excludePaths = builder.excludePaths != null ? builder.excludePaths : new java.util.ArrayList<>();
        this.excludeComments = builder.excludeComments;
        this.threadCount = builder.threadCount > 0 ? builder.threadCount : Runtime.getRuntime().availableProcessors();
        this.sortBy = builder.sortBy != null ? builder.sortBy : "lines";
        this.outputFormat = builder.outputFormat != null ? builder.outputFormat : "console";
        this.outputFile = builder.outputFile;
        this.useGitIgnore = builder.useGitIgnore;
        this.defaultInputPath = builder.defaultInputPath;
        this.defaultOutputPath = builder.defaultOutputPath;
        this.extraPaths = builder.extraPaths != null ? builder.extraPaths : new java.util.ArrayList<>();
    }

    public static class Builder {
        private Path rootDir;
        private List<String> extensions;
        private List<String> excludePaths;
        private boolean excludeComments = true;
        private int threadCount = 0;
        private String sortBy = "lines";
        private String outputFormat = "console";
        private Path outputFile;
        private boolean useGitIgnore = true;
        private Path defaultInputPath;
        private Path defaultOutputPath;
        private List<String> extraPaths;  // ★ 新增

        public Builder rootDir(Path rootDir) { this.rootDir = rootDir; return this; }
        public Builder extensions(List<String> exts) { this.extensions = exts; return this; }
        public Builder excludePaths(List<String> paths) { this.excludePaths = paths; return this; }
        public Builder excludeComments(boolean b) { this.excludeComments = b; return this; }
        public Builder threadCount(int n) { this.threadCount = n; return this; }
        public Builder sortBy(String sort) { this.sortBy = sort; return this; }
        public Builder outputFormat(String fmt) { this.outputFormat = fmt; return this; }
        public Builder outputFile(Path path) { this.outputFile = path; return this; }
        public Builder useGitIgnore(boolean use) { this.useGitIgnore = use; return this; }
        public Builder defaultInputPath(Path path) { this.defaultInputPath = path; return this; }
        public Builder defaultOutputPath(Path path) { this.defaultOutputPath = path; return this; }
        public Builder extraPaths(List<String> paths) { this.extraPaths = paths; return this; } // ★ 新增

        public Config build() { return new Config(this); }

        // Getters and Setters
        public Path getRootDir() { return rootDir; }
        public void setRootDir(Path rootDir) { this.rootDir = rootDir; }
        public List<String> getExtensions() { return extensions; }
        public void setExtensions(List<String> extensions) { this.extensions = extensions; }
        public List<String> getExcludePaths() { return excludePaths; }
        public void setExcludePaths(List<String> excludePaths) { this.excludePaths = excludePaths; }
        public boolean isExcludeComments() { return excludeComments; }
        public void setExcludeComments(boolean excludeComments) { this.excludeComments = excludeComments; }
        public int getThreadCount() { return threadCount; }
        public void setThreadCount(int threadCount) { this.threadCount = threadCount; }
        public String getSortBy() { return sortBy; }
        public void setSortBy(String sortBy) { this.sortBy = sortBy; }
        public String getOutputFormat() { return outputFormat; }
        public void setOutputFormat(String outputFormat) { this.outputFormat = outputFormat; }
        public Path getOutputFile() { return outputFile; }
        public void setOutputFile(Path outputFile) { this.outputFile = outputFile; }
        public boolean isUseGitIgnore() { return useGitIgnore; }
        public void setUseGitIgnore(boolean useGitIgnore) { this.useGitIgnore = useGitIgnore; }
        public Path getDefaultInputPath() { return defaultInputPath; }
        public void setDefaultInputPath(Path defaultInputPath) { this.defaultInputPath = defaultInputPath; }
        public Path getDefaultOutputPath() { return defaultOutputPath; }
        public void setDefaultOutputPath(Path defaultOutputPath) { this.defaultOutputPath = defaultOutputPath; }
        public List<String> getExtraPaths() { return extraPaths; }  // ★ 新增
        public void setExtraPaths(List<String> extraPaths) { this.extraPaths = extraPaths; }  // ★ 新增
    }

    public Path getRootDir() { return rootDir; }
    public List<String> getExtensions() { return extensions; }
    public List<String> getExcludePaths() { return excludePaths; }
    public boolean isExcludeComments() { return excludeComments; }
    public int getThreadCount() { return threadCount; }
    public String getSortBy() { return sortBy; }
    public String getOutputFormat() { return outputFormat; }
    public Path getOutputFile() { return outputFile; }
    public boolean isUseGitIgnore() { return useGitIgnore; }
    public Path getDefaultInputPath() { return defaultInputPath; }
    public Path getDefaultOutputPath() { return defaultOutputPath; }
    public List<String> getExtraPaths() { return extraPaths; }  // ★ 新增
}