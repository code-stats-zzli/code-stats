package com.omni.business.assistance.sys.model;

import java.util.List;
import java.util.Map;

/**
 * 端口探测结果。
 * error 非空时表示采集失败（但结构仍返回给前端展示）。
 */
public class PortScanResult {
    private String connectionId;
    private String host;
    private String osType;
    private long startTime;
    private long endTime;
    private long durationMs;
    private List<PortInfo> ports;
    private Map<String, Object> summary;
    private String error;

    public String getConnectionId() { return connectionId; }
    public void setConnectionId(String connectionId) { this.connectionId = connectionId; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public String getOsType() { return osType; }
    public void setOsType(String osType) { this.osType = osType; }
    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public List<PortInfo> getPorts() { return ports; }
    public void setPorts(List<PortInfo> ports) { this.ports = ports; }
    public Map<String, Object> getSummary() { return summary; }
    public void setSummary(Map<String, Object> summary) { this.summary = summary; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}