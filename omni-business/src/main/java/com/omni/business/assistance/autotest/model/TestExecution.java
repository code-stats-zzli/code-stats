package com.omni.business.assistance.autotest.model;

import java.util.Date;

public class TestExecution {
    private String id;
    private String caseId;
    private String executionCode;
    private Integer triggerType;
    private String triggerUser;
    private String triggerSource;
    private Integer envType;
    private String browserType;
    private String browserVersion;
    private String osType;
    private String osVersion;
    private Date startTime;
    private Date endTime;
    private Integer totalSteps;
    private Integer passCount;
    private Integer failCount;
    private Integer skipCount;
    private Double passRate;
    private Integer status;
    private String errorMessage;
    private Integer totalTimeMs;
    private Integer avgStepTimeMs;
    private Integer maxStepTimeMs;
    private String reportUrl;
    private String reportJsonUrl;
    private String videoUrl;
    private String logUrl;
    private Date createTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCaseId() {
        return caseId;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    public String getExecutionCode() {
        return executionCode;
    }

    public void setExecutionCode(String executionCode) {
        this.executionCode = executionCode;
    }

    public Integer getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(Integer triggerType) {
        this.triggerType = triggerType;
    }

    public String getTriggerUser() {
        return triggerUser;
    }

    public void setTriggerUser(String triggerUser) {
        this.triggerUser = triggerUser;
    }

    public String getTriggerSource() {
        return triggerSource;
    }

    public void setTriggerSource(String triggerSource) {
        this.triggerSource = triggerSource;
    }

    public Integer getEnvType() {
        return envType;
    }

    public void setEnvType(Integer envType) {
        this.envType = envType;
    }

    public String getBrowserType() {
        return browserType;
    }

    public void setBrowserType(String browserType) {
        this.browserType = browserType;
    }

    public String getBrowserVersion() {
        return browserVersion;
    }

    public void setBrowserVersion(String browserVersion) {
        this.browserVersion = browserVersion;
    }

    public String getOsType() {
        return osType;
    }

    public void setOsType(String osType) {
        this.osType = osType;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public void setOsVersion(String osVersion) {
        this.osVersion = osVersion;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public Integer getTotalSteps() {
        return totalSteps;
    }

    public void setTotalSteps(Integer totalSteps) {
        this.totalSteps = totalSteps;
    }

    public Integer getPassCount() {
        return passCount;
    }

    public void setPassCount(Integer passCount) {
        this.passCount = passCount;
    }

    public Integer getFailCount() {
        return failCount;
    }

    public void setFailCount(Integer failCount) {
        this.failCount = failCount;
    }

    public Integer getSkipCount() {
        return skipCount;
    }

    public void setSkipCount(Integer skipCount) {
        this.skipCount = skipCount;
    }

    public Double getPassRate() {
        return passRate;
    }

    public void setPassRate(Double passRate) {
        this.passRate = passRate;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getTotalTimeMs() {
        return totalTimeMs;
    }

    public void setTotalTimeMs(Integer totalTimeMs) {
        this.totalTimeMs = totalTimeMs;
    }

    public Integer getAvgStepTimeMs() {
        return avgStepTimeMs;
    }

    public void setAvgStepTimeMs(Integer avgStepTimeMs) {
        this.avgStepTimeMs = avgStepTimeMs;
    }

    public Integer getMaxStepTimeMs() {
        return maxStepTimeMs;
    }

    public void setMaxStepTimeMs(Integer maxStepTimeMs) {
        this.maxStepTimeMs = maxStepTimeMs;
    }

    public String getReportUrl() {
        return reportUrl;
    }

    public void setReportUrl(String reportUrl) {
        this.reportUrl = reportUrl;
    }

    public String getReportJsonUrl() {
        return reportJsonUrl;
    }

    public void setReportJsonUrl(String reportJsonUrl) {
        this.reportJsonUrl = reportJsonUrl;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public String getLogUrl() {
        return logUrl;
    }

    public void setLogUrl(String logUrl) {
        this.logUrl = logUrl;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    // Getters and Setters (all fields)
    // ...
}