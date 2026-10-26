package com.omni.common.datasource.driver;

import java.util.Date;

public class DriverJar {
    private String id;
    private String name;
    private byte[] jarData;
    private String fileName;
    private Date createTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public byte[] getJarData() { return jarData; }
    public void setJarData(byte[] jarData) { this.jarData = jarData; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}