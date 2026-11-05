package com.omni.common.datasource.driver;

import java.util.Date;

public class DriverJar {
    private String id;
    private String name;
    private String dbType;      // ★ 新增：mysql / oracle / postgresql
    private String version;     // ★ 新增：驱动版本
    private byte[] jarData;
    private String fileName;
    private Date createTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDbType() { return dbType; }
    public void setDbType(String dbType) { this.dbType = dbType; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public byte[] getJarData() { return jarData; }
    public void setJarData(byte[] jarData) { this.jarData = jarData; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}