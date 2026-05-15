package com.codestats.framework.log.core;

import com.codestats.framework.log.Level;

public class LogEvent {
    private final Level level;
    private final String loggerName;
    private final String message;
    private final Throwable throwable;
    private final long timestamp;
    private final String threadName;
    private final StackTraceElement caller; // 调用位置信息

    private LogEvent(Builder builder) {
        this.level = builder.level;
        this.loggerName = builder.loggerName;
        this.message = builder.message;
        this.throwable = builder.throwable;
        this.timestamp = builder.timestamp;
        this.threadName = builder.threadName;
        this.caller = builder.caller;
    }

    // getters
    public Level getLevel() { return level; }
    public String getLoggerName() { return loggerName; }
    public String getMessage() { return message; }
    public Throwable getThrowable() { return throwable; }
    public long getTimestamp() { return timestamp; }
    public String getThreadName() { return threadName; }
    public StackTraceElement getCaller() { return caller; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Level level;
        private String loggerName;
        private String message;
        private Throwable throwable;
        private long timestamp;
        private String threadName;
        private StackTraceElement caller;

        public Builder level(Level level) { this.level = level; return this; }
        public Builder loggerName(String loggerName) { this.loggerName = loggerName; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder throwable(Throwable throwable) { this.throwable = throwable; return this; }
        public Builder timestamp(long timestamp) { this.timestamp = timestamp; return this; }
        public Builder threadName(String threadName) { this.threadName = threadName; return this; }
        public Builder caller(StackTraceElement caller) { this.caller = caller; return this; }

        public LogEvent build() {
            if (timestamp == 0) timestamp = System.currentTimeMillis();
            if (threadName == null) threadName = Thread.currentThread().getName();
            return new LogEvent(this);
        }
    }
}