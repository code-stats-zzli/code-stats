package com.omni.business.assistance.sys.model;

/**
 * SSH 远程连接配置。
 * 只支持密钥认证（keyPath 为空则走默认 ~/.ssh），不落盘密码。
 */
public class SshConnection {
    private String id;
    private String name;
    private String host;
    private int port = 22;
    private String username;
    private String keyPath;         // 为空则使用默认 ~/.ssh/id_rsa
    private String osType = "auto"; // linux / windows / auto
    private String remark;
    private long lastTestTime;
    private boolean lastTestOk;
    private String lastTestError;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getKeyPath() { return keyPath; }
    public void setKeyPath(String keyPath) { this.keyPath = keyPath; }
    public String getOsType() { return osType; }
    public void setOsType(String osType) { this.osType = osType; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public long getLastTestTime() { return lastTestTime; }
    public void setLastTestTime(long lastTestTime) { this.lastTestTime = lastTestTime; }
    public boolean isLastTestOk() { return lastTestOk; }
    public void setLastTestOk(boolean lastTestOk) { this.lastTestOk = lastTestOk; }
    public String getLastTestError() { return lastTestError; }
    public void setLastTestError(String lastTestError) { this.lastTestError = lastTestError; }
}