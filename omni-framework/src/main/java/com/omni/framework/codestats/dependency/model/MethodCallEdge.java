package com.omni.framework.codestats.dependency.model;

/**
 * 方法调用边。
 */
public class MethodCallEdge {
    public enum CallType {
        VIRTUAL, INTERFACE, STATIC, SPECIAL, UNKNOWN
    }

    private final String callerId;
    private final String calleeId;
    private final CallType callType;
    private final int lineNumber;
    private final boolean resolved;

    public MethodCallEdge(String callerId, String calleeId, CallType callType,
                          int lineNumber, boolean resolved) {
        this.callerId = callerId;
        this.calleeId = calleeId;
        this.callType = callType;
        this.lineNumber = lineNumber;
        this.resolved = resolved;
    }

    public String getCallerId() { return callerId; }
    public String getCalleeId() { return calleeId; }
    public CallType getCallType() { return callType; }
    public int getLineNumber() { return lineNumber; }
    public boolean isResolved() { return resolved; }
}