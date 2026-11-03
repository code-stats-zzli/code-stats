package com.omni.framework.unzip;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ExtractResult {
    private boolean success;
    private String message;
    private ArchiveType type;
    private Path outputDir;
    private List<ArchiveEntryInfo> entries = new ArrayList<>();
    private int totalFiles;
    private int totalDirs;
    private int binaryFiles;
    private long totalBytes;
    private long durationMs;

    public static ExtractResult ok(ArchiveType type, Path outputDir) {
        ExtractResult r = new ExtractResult();
        r.success = true;
        r.type = type;
        r.outputDir = outputDir;
        return r;
    }
    public static ExtractResult error(String msg) {
        ExtractResult r = new ExtractResult();
        r.success = false;
        r.message = msg;
        return r;
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public ArchiveType getType() { return type; }
    public Path getOutputDir() { return outputDir; }
    public List<ArchiveEntryInfo> getEntries() { return entries; }
    public int getTotalFiles() { return totalFiles; }
    public void setTotalFiles(int totalFiles) { this.totalFiles = totalFiles; }
    public int getTotalDirs() { return totalDirs; }
    public void setTotalDirs(int totalDirs) { this.totalDirs = totalDirs; }
    public int getBinaryFiles() { return binaryFiles; }
    public void setBinaryFiles(int binaryFiles) { this.binaryFiles = binaryFiles; }
    public long getTotalBytes() { return totalBytes; }
    public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
}