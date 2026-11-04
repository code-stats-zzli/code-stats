package com.omni.business.assistance.cluster.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 集群节点。
 * role：MASTER（主服务自身） / WORKER（部署的实例）
 * channel：local / ssh / manual
 * state：PENDING / STARTING / RUNNING / UNKNOWN / OFFLINE / ERROR
 */
public class ClusterNode {
    private String nodeId;
    private String role = "WORKER";
    private String channel;
    private String state = "PENDING";

    private String host;            // 目标机 host:port（ssh 用） / 空表示 local
    private String sshUser;
    private String sshKeyPath;
    private int    sshPort = 22;

    private int    port;            // worker 服务端口
    private String jarName;
    private String jarMd5;
    private String deployDir;
    private String javaPath;
    private String jdkVersion;

    private Long   pid;
    private long   startTime;
    private long   lastHeartbeat;
    private String errorMsg;
    private String masterUrl;       // 传给 worker 用来注册
    private String instanceId;      // worker 端生成的实例 ID（区分重启）

    public Map<String, Object> toView() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nodeId", nodeId);
        m.put("role", role);
        m.put("channel", channel);
        m.put("state", state);
        m.put("host", host);
        m.put("port", port);
        m.put("jarName", jarName);
        m.put("deployDir", deployDir);
        m.put("javaPath", javaPath);
        m.put("jdkVersion", jdkVersion);
        m.put("pid", pid);
        m.put("startTime", startTime);
        m.put("lastHeartbeat", lastHeartbeat);
        m.put("errorMsg", errorMsg);
        m.put("uptimeMs", startTime > 0 ? System.currentTimeMillis() - startTime : 0);
        return m;
    }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public String getSshUser() { return sshUser; }
    public void setSshUser(String sshUser) { this.sshUser = sshUser; }
    public String getSshKeyPath() { return sshKeyPath; }
    public void setSshKeyPath(String sshKeyPath) { this.sshKeyPath = sshKeyPath; }
    public int getSshPort() { return sshPort; }
    public void setSshPort(int sshPort) { this.sshPort = sshPort; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getJarName() { return jarName; }
    public void setJarName(String jarName) { this.jarName = jarName; }
    public String getJarMd5() { return jarMd5; }
    public void setJarMd5(String jarMd5) { this.jarMd5 = jarMd5; }
    public String getDeployDir() { return deployDir; }
    public void setDeployDir(String deployDir) { this.deployDir = deployDir; }
    public String getJavaPath() { return javaPath; }
    public void setJavaPath(String javaPath) { this.javaPath = javaPath; }
    public String getJdkVersion() { return jdkVersion; }
    public void setJdkVersion(String jdkVersion) { this.jdkVersion = jdkVersion; }
    public Long getPid() { return pid; }
    public void setPid(Long pid) { this.pid = pid; }
    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
    public long getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(long lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }
    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
    public String getMasterUrl() { return masterUrl; }
    public void setMasterUrl(String masterUrl) { this.masterUrl = masterUrl; }
    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
}