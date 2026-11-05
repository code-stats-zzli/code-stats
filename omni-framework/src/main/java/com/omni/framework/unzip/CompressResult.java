package com.omni.framework.unzip;

import java.nio.file.Path;

public class CompressResult {
    private boolean success;
    private String message;
    private ArchiveType type;
    private Path outputFile;
    private byte[] data;
    private int fileCount;
    private long rawBytes;
    private long archiveBytes;
    private long durationMs;

    public static CompressResult ok(ArchiveType type) {
        CompressResult r = new CompressResult();
        r.success = true;
        r.type = type;
        return r;
    }
    public static CompressResult error(String msg) {
        CompressResult r = new CompressResult();
        r.success = false;
        r.message = msg;
        return r;
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public ArchiveType getType() { return type; }
    public Path getOutputFile() { return outputFile; }
    public void setOutputFile(Path outputFile) { this.outputFile = outputFile; }
    public byte[] getData() { return data; }
    public void setData(byte[] data) { this.data = data; }
    public int getFileCount() { return fileCount; }
    public void setFileCount(int fileCount) { this.fileCount = fileCount; }
    public long getRawBytes() { return rawBytes; }
    public void setRawBytes(long rawBytes) { this.rawBytes = rawBytes; }
    public long getArchiveBytes() { return archiveBytes; }
    public void setArchiveBytes(long archiveBytes) { this.archiveBytes = archiveBytes; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
}