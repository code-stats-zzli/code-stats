package com.omni.framework.jstats.core.model;

import java.util.List;

public class ThreadInfo {
    private final String threadName;
    private final long threadId;
    private final String state;
    private final List<String> stackTrace;

    public ThreadInfo(String threadName, long threadId, String state, List<String> stackTrace) {
        this.threadName = threadName;
        this.threadId = threadId;
        this.state = state;
        this.stackTrace = stackTrace;
    }
    public String getThreadName() { return threadName; }
    public long getThreadId() { return threadId; }
    public String getState() { return state; }
    public List<String> getStackTrace() { return stackTrace; }
}