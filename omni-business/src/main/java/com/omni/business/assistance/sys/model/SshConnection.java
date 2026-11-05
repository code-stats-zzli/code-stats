package com.omni.business.assistance.sys.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * SSH 远程连接配置。
 *
 * <p><b>密码不入库 / 不回显</b>：</p>
 * <ul>
 *   <li>{@code password} 标注 {@code @JsonProperty(access = WRITE_ONLY)}：
 *       反序列化接收，序列化时忽略，保证写入 ssh-connections.json 时不包含密码；</li>
 *   <li>{@code password} 标注 {@code transient}，Java 序列化也忽略；</li>
 *   <li>密码只由 {@code SshCredentialCache} 内存持有，按 connectionId 绑定；</li>
 *   <li>回显字段 {@code hasPassword} / {@code hasKeyPath} 告诉前端是否可用。</li>
 * </ul>
 */
public class SshConnection implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String host;
    private int port = 22;
    private String username;

    /** 私钥路径（可选，与密码二选一或同时配置） */
    private String keyPath;

    /**
     * 密码：仅内存使用，不落盘、不回显。
     * WRITE_ONLY 保证序列化（对象 -> JSON）时忽略。
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private transient String password;

    /** 回显：后端缓存里是否存在该连接的密码 */
    private boolean hasPassword;

    /** 回显：是否配置了私钥路径 */
    private boolean hasKeyPath;

    private String osType = "auto";   // linux / windows / auto
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
    public boolean isHasPassword() { return hasPassword; }
    public void setHasPassword(boolean hasPassword) { this.hasPassword = hasPassword; }
    public boolean isHasKeyPath() { return hasKeyPath; }
    public void setHasKeyPath(boolean hasKeyPath) { this.hasKeyPath = hasKeyPath; }
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