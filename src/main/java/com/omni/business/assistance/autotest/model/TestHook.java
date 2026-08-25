package com.omni.business.assistance.autotest.model;

import java.util.Date;

public class TestHook {
    private String id;
    private String caseId;
    private Integer hookType;
    private Integer hookOrder;
    private String scriptType;
    private String scriptContent;
    private String scriptDesc;
    private Integer enabled;
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

    public Integer getHookType() {
        return hookType;
    }

    public void setHookType(Integer hookType) {
        this.hookType = hookType;
    }

    public Integer getHookOrder() {
        return hookOrder;
    }

    public void setHookOrder(Integer hookOrder) {
        this.hookOrder = hookOrder;
    }

    public String getScriptType() {
        return scriptType;
    }

    public void setScriptType(String scriptType) {
        this.scriptType = scriptType;
    }

    public String getScriptContent() {
        return scriptContent;
    }

    public void setScriptContent(String scriptContent) {
        this.scriptContent = scriptContent;
    }

    public String getScriptDesc() {
        return scriptDesc;
    }

    public void setScriptDesc(String scriptDesc) {
        this.scriptDesc = scriptDesc;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
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

    // Getters and Setters...
}