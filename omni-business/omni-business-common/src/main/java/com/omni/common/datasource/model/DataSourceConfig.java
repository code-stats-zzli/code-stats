package com.omni.common.datasource.model;

import java.util.Date;
import java.util.UUID;

public class DataSourceConfig {
    private String id;
    private String name;
    private String dbType;
    private String host;
    private Integer port;
    private String databaseName;
    private String username;
    private String password;
    private String driverClass;
    private String driverJar;
    private String connectionProperties;
    private String version;
    private Integer status;
    private Date createTime;
    private Date updateTime;

    /**
     * 若 id 为空则生成一个 UUID；若已有 id 则保持不变。
     *
     * @return true  —— 本次生成了新 id（说明原来为空，属于「新增」场景）；
     *         false —— id 本来就有（属于「更新」场景）。
     *
     * ⚠️ 重要提醒：
     *   调用本方法后 this.id 一定非空，因此业务层**不能**再用
     *   `if (id == null || id.isEmpty())` 来判断新增/更新，
     *   必须使用本方法的返回值。
     *
     *   正确用法：
     *     boolean isNew = config.generateIdIfNull();
     *     if (isNew) { INSERT } else { UPDATE }
     *
     *   错误用法（会导致新增永远走 UPDATE）：
     *     config.generateIdIfNull();
     *     if (config.getId() == null) { INSERT } else { UPDATE }
     */
    public boolean generateIdIfNull() {
        if (id == null || id.trim().isEmpty()) {
            this.id = UUID.randomUUID().toString().replace("-", "");
            return true;
        }
        return false;
    }

    // ==================== getters / setters ====================

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDbType() { return dbType; }
    public void setDbType(String dbType) { this.dbType = dbType; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }
    public String getDatabaseName() { return databaseName; }
    public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getDriverClass() { return driverClass; }
    public void setDriverClass(String driverClass) { this.driverClass = driverClass; }
    public String getDriverJar() { return driverJar; }
    public void setDriverJar(String driverJar) { this.driverJar = driverJar; }
    public String getConnectionProperties() { return connectionProperties; }
    public void setConnectionProperties(String connectionProperties) { this.connectionProperties = connectionProperties; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}