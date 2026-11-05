package com.omni.framework.unzip;

public class ArchiveEntryInfo {
    private String path;
    private long size;
    private boolean directory;
    private String language;
    private boolean binary;

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public boolean isDirectory() { return directory; }
    public void setDirectory(boolean directory) { this.directory = directory; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public boolean isBinary() { return binary; }
    public void setBinary(boolean binary) { this.binary = binary; }
}