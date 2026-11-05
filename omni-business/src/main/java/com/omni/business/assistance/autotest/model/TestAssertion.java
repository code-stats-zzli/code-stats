package com.omni.business.assistance.autotest.model;

import java.util.Date;

public class TestAssertion {
    private String id;
    private String caseId;
    private String stepId;
    private Integer assertOrder;
    private String assertType;
    private String locatorType;
    private String locatorValue;
    private String expectedValue;
    private Integer expectedValueType;
    private String compareType;
    private String customScript;
    private Integer failHandle;
    private String assertDesc;
    private Date createTime;
    private Date updateTime;

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

    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    public Integer getAssertOrder() {
        return assertOrder;
    }

    public void setAssertOrder(Integer assertOrder) {
        this.assertOrder = assertOrder;
    }

    public String getAssertType() {
        return assertType;
    }

    public void setAssertType(String assertType) {
        this.assertType = assertType;
    }

    public String getLocatorType() {
        return locatorType;
    }

    public void setLocatorType(String locatorType) {
        this.locatorType = locatorType;
    }

    public String getLocatorValue() {
        return locatorValue;
    }

    public void setLocatorValue(String locatorValue) {
        this.locatorValue = locatorValue;
    }

    public String getExpectedValue() {
        return expectedValue;
    }

    public void setExpectedValue(String expectedValue) {
        this.expectedValue = expectedValue;
    }

    public Integer getExpectedValueType() {
        return expectedValueType;
    }

    public void setExpectedValueType(Integer expectedValueType) {
        this.expectedValueType = expectedValueType;
    }

    public String getCompareType() {
        return compareType;
    }

    public void setCompareType(String compareType) {
        this.compareType = compareType;
    }

    public String getCustomScript() {
        return customScript;
    }

    public void setCustomScript(String customScript) {
        this.customScript = customScript;
    }

    public Integer getFailHandle() {
        return failHandle;
    }

    public void setFailHandle(Integer failHandle) {
        this.failHandle = failHandle;
    }

    public String getAssertDesc() {
        return assertDesc;
    }

    public void setAssertDesc(String assertDesc) {
        this.assertDesc = assertDesc;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    // Getters and Setters (all fields)
    // ... (omitted for brevity, generate all)
}