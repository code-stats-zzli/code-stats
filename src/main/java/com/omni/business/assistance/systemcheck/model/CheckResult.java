package com.omni.business.assistance.systemcheck.model;

import java.util.ArrayList;
import java.util.List;

/** 单个检查项的执行结果 */
public class CheckResult {
    private String checkId;
    private String checkName;
    private boolean success;
    private String errorMessage;
    private long durationMs;
    private List<Finding> findings = new ArrayList<>();

    public CheckResult() {}
    public CheckResult(String checkId, String checkName) {
        this.checkId = checkId;
        this.checkName = checkName;
    }
    public String getCheckId() { return checkId; }
    public void setCheckId(String checkId) { this.checkId = checkId; }
    public String getCheckName() { return checkName; }
    public void setCheckName(String checkName) { this.checkName = checkName; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public List<Finding> getFindings() { return findings; }
    public void setFindings(List<Finding> findings) { this.findings = findings; }
}