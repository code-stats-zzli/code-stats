package com.omni.business.assistance.autotest.core.engine;

public class AssertionResult {
    private String assertionId;
    private boolean passed;
    private String message;
    private String expected;
    private String actual;

    public AssertionResult(String assertionId) { this.assertionId = assertionId; }

    // Getters and Setters
    public String getAssertionId() { return assertionId; }
    public void setAssertionId(String assertionId) { this.assertionId = assertionId; }
    public boolean isPassed() { return passed; }
    public void setPassed(boolean passed) { this.passed = passed; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getExpected() { return expected; }
    public void setExpected(String expected) { this.expected = expected; }
    public String getActual() { return actual; }
    public void setActual(String actual) { this.actual = actual; }
}