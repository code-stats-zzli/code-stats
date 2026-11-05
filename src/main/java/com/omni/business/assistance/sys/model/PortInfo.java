package com.omni.business.assistance.sys.model;

/**
 * 单个端口条目。
 * 由 PortCollector 从目标机命令输出解析得到，
 * 由 ServiceIdentifier 补充 serviceName / serviceCategory。
 */
public class PortInfo {
    private int port;
    private String protocol;      // TCP / UDP
    private String state;         // LISTEN / UNCONN / UNKNOWN
    private String address;       // 监听地址 0.0.0.0 / 127.0.0.1 / [::] 等
    private Long processPid;
    private String processName;
    private String processUser;
    private String serviceName;   // 识别出的服务名，如 MySQL
    private String serviceCategory; // 分类：系统服务 / Web / 数据库 / 缓存 / 中间件 / 容器 / 搜索 / 其他
    private Boolean reachable;    // 预留：是否探活成功
    private String banner;        // 预留：协议 banner

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public Long getProcessPid() { return processPid; }
    public void setProcessPid(Long processPid) { this.processPid = processPid; }
    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }
    public String getProcessUser() { return processUser; }
    public void setProcessUser(String processUser) { this.processUser = processUser; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public String getServiceCategory() { return serviceCategory; }
    public void setServiceCategory(String serviceCategory) { this.serviceCategory = serviceCategory; }
    public Boolean getReachable() { return reachable; }
    public void setReachable(Boolean reachable) { this.reachable = reachable; }
    public String getBanner() { return banner; }
    public void setBanner(String banner) { this.banner = banner; }
}