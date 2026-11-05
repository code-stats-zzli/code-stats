package com.omni.business.assistance.autotest.model;

import java.util.Date;

public class TestStep {
    private String id;
    private String caseId;
    private Integer stepOrder;
    private String parentStepId;
    private Integer stepType;
    private String actionType;
    private String locatorType;
    private String locatorValue;
    private String locatorPriority;
    private String actionValue;
    private Integer actionValueType;
    private String waitType;
    private Integer waitSeconds;
    private String waitUntilValue;
    private Integer failHandle;
    private Integer ignoreException;
    private String stepDesc;
    private Date createTime;
    private Date updateTime;

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getStepDesc() {
        return stepDesc;
    }

    public void setStepDesc(String stepDesc) {
        this.stepDesc = stepDesc;
    }

    public Integer getIgnoreException() {
        return ignoreException;
    }

    public void setIgnoreException(Integer ignoreException) {
        this.ignoreException = ignoreException;
    }

    public Integer getFailHandle() {
        return failHandle;
    }

    public void setFailHandle(Integer failHandle) {
        this.failHandle = failHandle;
    }

    public String getWaitUntilValue() {
        return waitUntilValue;
    }

    public void setWaitUntilValue(String waitUntilValue) {
        this.waitUntilValue = waitUntilValue;
    }

    public Integer getWaitSeconds() {
        return waitSeconds;
    }

    public void setWaitSeconds(Integer waitSeconds) {
        this.waitSeconds = waitSeconds;
    }

    public String getWaitType() {
        return waitType;
    }

    public void setWaitType(String waitType) {
        this.waitType = waitType;
    }

    public Integer getActionValueType() {
        return actionValueType;
    }

    public void setActionValueType(Integer actionValueType) {
        this.actionValueType = actionValueType;
    }

    public String getActionValue() {
        return actionValue;
    }

    public void setActionValue(String actionValue) {
        this.actionValue = actionValue;
    }

    public String getLocatorPriority() {
        return locatorPriority;
    }

    public void setLocatorPriority(String locatorPriority) {
        this.locatorPriority = locatorPriority;
    }

    public String getLocatorValue() {
        return locatorValue;
    }

    public void setLocatorValue(String locatorValue) {
        this.locatorValue = locatorValue;
    }

    public String getLocatorType() {
        return locatorType;
    }

    public void setLocatorType(String locatorType) {
        this.locatorType = locatorType;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public Integer getStepType() {
        return stepType;
    }

    public void setStepType(Integer stepType) {
        this.stepType = stepType;
    }

    public String getParentStepId() {
        return parentStepId;
    }

    public void setParentStepId(String parentStepId) {
        this.parentStepId = parentStepId;
    }

    public Integer getStepOrder() {
        return stepOrder;
    }

    public void setStepOrder(Integer stepOrder) {
        this.stepOrder = stepOrder;
    }

    public String getCaseId() {
        return caseId;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    // Getters and Setters (omitted for brevity, generate all)
    // ...
}