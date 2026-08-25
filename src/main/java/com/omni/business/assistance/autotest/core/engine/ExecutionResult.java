package com.omni.business.assistance.autotest.core.engine;

import java.util.ArrayList;
import java.util.List;

public class ExecutionResult {
    private String caseId;
    private List<StepResult> stepResults = new ArrayList<>();
    private long startTime;
    private long endTime;
    private boolean allPassed = true;

    public ExecutionResult(String caseId) {
        this.caseId = caseId;
        this.startTime = System.currentTimeMillis();
    }

    public void addStepResult(StepResult result) {
        stepResults.add(result);
        if (!result.isPassed()) allPassed = false;
    }

    // Getters and Setters
    public String getCaseId() { return caseId; }
    public List<StepResult> getStepResults() { return stepResults; }
    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }
    public boolean isAllPassed() { return allPassed; }
}