package com.omni.business.assistance.importfile.model;

public class ImportFile {
    private String path;
    private String content;
    private String language;
    private long size;
    private int lineCount;
    private String error;
    private String origin;
    private boolean binary;

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public int getLineCount() { return lineCount; }
    public void setLineCount(int lineCount) { this.lineCount = lineCount; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public boolean isBinary() { return binary; }
    public void setBinary(boolean binary) { this.binary = binary; }
}