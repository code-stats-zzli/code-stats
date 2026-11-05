package com.omni.business.assistance.sys.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SSH 远程连接配置。
 * <p>支持两种认证方式（二选一或同时配置，JSch 会依次尝试）：</p>
 * <ul>
 *   <li>私钥认证：{@link #keyPath}（为空则使用默认 ~/.ssh/id_rsa）</li>
 *   <li>密码认证：{@link #password}（仅内存持有，不写盘）</li>
 * </ul>
 *
 * <p><b>安全说明</b></p>
 * <ul>
 *   <li>password 标注 {@code @JsonProperty(access = WRITE_ONLY)}：
 *       反序列化接收，序列化时忽略（不落盘）</li>
 *   <li>服务重启后 password 会被清空，用户重新输入即可</li>
 *   <li>若要持久化密码，请自行加 AES 加密，不要把明文写进 JSON</li>
 * </ul>
 */
public class SshConnection {
    private String id;
    private String name;
    private String host;
    private int port = 22;
    private String username;
    private String keyPath;         // 为空则使用默认 ~/.ssh/id_rsa

    /** 密码（可选，与 keyPath 二选一；只内存持有，不落盘） */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

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
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
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