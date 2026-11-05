package com.omni.business.assistance.autotest.model;



import java.util.Date;

public class TestSnapshot {
    private String id;
    private String executionId;
    private String stepId;
    private Integer stepOrder;
    private String actionType;
    private String stepDesc;
    private String htmlContent;
    private Integer htmlSize;
    private String screenshotUrl;
    private String screenshotThumbnailUrl;
    private String elementTag;
    private String elementSelector;
    private String elementRect;
    private String elementText;
    private Integer isSuccess;
    private String errorMsg;
    private String errorStack;
    private Integer execTimeMs;
    private String variablesSnapshot;
    private String cookiesSnapshot;
    private Date createTime;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }
    public String getStepId() { return stepId; }
    public void setStepId(String stepId) { this.stepId = stepId; }
    public Integer getStepOrder() { return stepOrder; }
    public void setStepOrder(Integer stepOrder) { this.stepOrder = stepOrder; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getStepDesc() { return stepDesc; }
    public void setStepDesc(String stepDesc) { this.stepDesc = stepDesc; }
    public String getHtmlContent() { return htmlContent; }
    public void setHtmlContent(String htmlContent) { this.htmlContent = htmlContent; }
    public Integer getHtmlSize() { return htmlSize; }
    public void setHtmlSize(Integer htmlSize) { this.htmlSize = htmlSize; }
    public String getScreenshotUrl() { return screenshotUrl; }
    public void setScreenshotUrl(String screenshotUrl) { this.screenshotUrl = screenshotUrl; }
    public String getScreenshotThumbnailUrl() { return screenshotThumbnailUrl; }
    public void setScreenshotThumbnailUrl(String screenshotThumbnailUrl) { this.screenshotThumbnailUrl = screenshotThumbnailUrl; }
    public String getElementTag() { return elementTag; }
    public void setElementTag(String elementTag) { this.elementTag = elementTag; }
    public String getElementSelector() { return elementSelector; }
    public void setElementSelector(String elementSelector) { this.elementSelector = elementSelector; }
    public String getElementRect() { return elementRect; }
    public void setElementRect(String elementRect) { this.elementRect = elementRect; }
    public String getElementText() { return elementText; }
    public void setElementText(String elementText) { this.elementText = elementText; }
    public Integer getIsSuccess() { return isSuccess; }
    public void setIsSuccess(Integer isSuccess) { this.isSuccess = isSuccess; }
    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
    public String getErrorStack() { return errorStack; }
    public void setErrorStack(String errorStack) { this.errorStack = errorStack; }
    public Integer getExecTimeMs() { return execTimeMs; }
    public void setExecTimeMs(Integer execTimeMs) { this.execTimeMs = execTimeMs; }
    public String getVariablesSnapshot() { return variablesSnapshot; }
    public void setVariablesSnapshot(String variablesSnapshot) { this.variablesSnapshot = variablesSnapshot; }
    public String getCookiesSnapshot() { return cookiesSnapshot; }
    public void setCookiesSnapshot(String cookiesSnapshot) { this.cookiesSnapshot = cookiesSnapshot; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
