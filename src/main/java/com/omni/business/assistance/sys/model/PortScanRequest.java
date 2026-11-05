package com.omni.business.assistance.sys.model;

import java.util.List;

/**
 * 端口探测请求。
 * 目前前端只传 connectionId，其余字段预留。
 */
public class PortScanRequest {
    /** SSH 连接 ID（必填） */
    private String connectionId;
    /** 只关注这些端口（可选，为空表示全部） */
    private List<Integer> onlyPorts;
    /** 是否在目标机上做 TCP 探活（默认 false） */
    private boolean probeReachable = false;
    private int probeTimeoutMs = 1000;

    public String getConnectionId() { return connectionId; }
    public void setConnectionId(String connectionId) { this.connectionId = connectionId; }
    public List<Integer> getOnlyPorts() { return onlyPorts; }
    public void setOnlyPorts(List<Integer> onlyPorts) { this.onlyPorts = onlyPorts; }
    public boolean isProbeReachable() { return probeReachable; }
    public void setProbeReachable(boolean probeReachable) { this.probeReachable = probeReachable; }
    public int getProbeTimeoutMs() { return probeTimeoutMs; }
    public void setProbeTimeoutMs(int probeTimeoutMs) { this.probeTimeoutMs = probeTimeoutMs; }
}