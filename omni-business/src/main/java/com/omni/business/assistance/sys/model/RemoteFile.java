package com.omni.business.assistance.sys.model;

/** 远程目录中的一项（文件或子目录）。 */
public class RemoteFile {
    private String name;
    private String path;
    private long size;
    private String modified;
    private boolean directory;
    private String permissions;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public String getModified() { return modified; }
    public void setModified(String modified) { this.modified = modified; }
    public boolean isDirectory() { return directory; }
    public void setDirectory(boolean directory) { this.directory = directory; }
    public String getPermissions() { return permissions; }
    public void setPermissions(String permissions) { this.permissions = permissions; }
}