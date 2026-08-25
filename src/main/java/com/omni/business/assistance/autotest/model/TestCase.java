package com.omni.autotest.core.model;

import java.util.Date;

public class TestCase {
    private String id;
    private String projectId;
    private String caseName;
    private String caseCode;
    private String modulePath;
    private Integer priority;
    private Integer envType;
    private String browserType;
    private String baseUrl;
    private Integer timeoutSec;
    private Integer retryTimes;
    private Integer status;
    private String tags;
    private String createBy;
    private Date createTime;
    private Date updateTime;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getCaseName() { return caseName; }
    public void setCaseName(String caseName) { this.caseName = caseName; }
    public String getCaseCode() { return caseCode; }
    public void setCaseCode(String caseCode) { this.caseCode = caseCode; }
    public String getModulePath() { return modulePath; }
    public void setModulePath(String modulePath) { this.modulePath = modulePath; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public Integer getEnvType() { return envType; }
    public void setEnvType(Integer envType) { this.envType = envType; }
    public String getBrowserType() { return browserType; }
    public void setBrowserType(String browserType) { this.browserType = browserType; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public Integer getTimeoutSec() { return timeoutSec; }
    public void setTimeoutSec(Integer timeoutSec) { this.timeoutSec = timeoutSec; }
    public Integer getRetryTimes() { return retryTimes; }
    public void setRetryTimes(Integer retryTimes) { this.retryTimes = retryTimes; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}