package com.omni.business.assistance.autotest.core.engine;


import java.util.ArrayList;
import java.util.List;

public class StepResult {
    private String stepId;
    private int order;
    private boolean passed;
    private long durationMs;
    private Throwable error;
    private List<AssertionResult> assertionResults = new ArrayList<>();
    private Object actionResult;

    public StepResult(String stepId, int order) {
        this.stepId = stepId;
        this.order = order;
    }

    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public Throwable getError() {
        return error;
    }

    public void setError(Throwable error) {
        this.error = error;
    }

    public List<AssertionResult> getAssertionResults() {
        return assertionResults;
    }

    public void setAssertionResults(List<AssertionResult> assertionResults) {
        this.assertionResults = assertionResults;
    }

    public Object getActionResult() {
        return actionResult;
    }

    public void setActionResult(Object actionResult) {
        this.actionResult = actionResult;
    }
    // getters/setters
}