package com.omni.business.assistance.cluster.model;

/** 前端提交的部署请求。channel 决定走哪条通道。 */
public class DeployRequest {
    private String channel;         // local / ssh / manual
    private String jarName;
    private int    port;            // 0 = 自动探测

    // SSH 通道参数
    private String host;
    private String sshUser;
    private String sshKeyPath;
    private int    sshPort = 22;

    // JDK 检测结果里选定的 java
    private String javaPath;

    // 部署目录（local/ssh 时必填）
    private String deployDir;

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getJarName() { return jarName; }
    public void setJarName(String jarName) { this.jarName = jarName; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public String getSshUser() { return sshUser; }
    public void setSshUser(String sshUser) { this.sshUser = sshUser; }
    public String getSshKeyPath() { return sshKeyPath; }
    public void setSshKeyPath(String sshKeyPath) { this.sshKeyPath = sshKeyPath; }
    public int getSshPort() { return sshPort; }
    public void setSshPort(int sshPort) { this.sshPort = sshPort; }
    public String getJavaPath() { return javaPath; }
    public void setJavaPath(String javaPath) { this.javaPath = javaPath; }
    public String getDeployDir() { return deployDir; }
    public void setDeployDir(String deployDir) { this.deployDir = deployDir; }
}