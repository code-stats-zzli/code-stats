package com.omni.framework.unzip;

import java.nio.file.Path;

public class ExtractRequest {
    private byte[] sourceBytes;
    private String sourceFileName;
    private Path targetDir;
    private String encoding = "UTF-8";
    private boolean overwrite = true;
    private int maxFiles = 5000;
    private long maxTotalBytes = 1024L * 1024 * 1024;

    public byte[] getSourceBytes() { return sourceBytes; }
    public void setSourceBytes(byte[] sourceBytes) { this.sourceBytes = sourceBytes; }
    public String getSourceFileName() { return sourceFileName; }
    public void setSourceFileName(String sourceFileName) { this.sourceFileName = sourceFileName; }
    public Path getTargetDir() { return targetDir; }
    public void setTargetDir(Path targetDir) { this.targetDir = targetDir; }
    public String getEncoding() { return encoding; }
    public void setEncoding(String encoding) { this.encoding = encoding; }
    public boolean isOverwrite() { return overwrite; }
    public void setOverwrite(boolean overwrite) { this.overwrite = overwrite; }
    public int getMaxFiles() { return maxFiles; }
    public void setMaxFiles(int maxFiles) { this.maxFiles = maxFiles; }
    public long getMaxTotalBytes() { return maxTotalBytes; }
    public void setMaxTotalBytes(long maxTotalBytes) { this.maxTotalBytes = maxTotalBytes; }
}