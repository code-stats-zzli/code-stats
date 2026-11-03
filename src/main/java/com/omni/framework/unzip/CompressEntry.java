package com.omni.framework.unzip;

/** 压缩条目（一条 = 文件 或 目录） */
public class CompressEntry {
    private String path;        // 相对路径（用 / 分隔，不允许 .. 和盘符）
    private byte[] content;     // 目录时为 null
    private boolean directory;

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public byte[] getContent() { return content; }
    public void setContent(byte[] content) { this.content = content; }
    public boolean isDirectory() { return directory; }
    public void setDirectory(boolean directory) { this.directory = directory; }
}