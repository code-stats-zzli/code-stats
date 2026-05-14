package com.framework.log.core;

import com.framework.log.Level;
import com.framework.log.LoggerFactory;
import com.framework.log.appender.Appender;
import com.framework.log.config.Configuration;

public class Logger {
    private final String name;
    private final Configuration.LoggerConfig config;

    public Logger(String name, Configuration.LoggerConfig config) {
        this.name = name;
        this.config = config;
    }

    public boolean isTraceEnabled() { return config.level.isGreaterOrEqual(Level.TRACE); }
    public boolean isDebugEnabled() { return config.level.isGreaterOrEqual(Level.DEBUG); }
    public boolean isInfoEnabled()  { return config.level.isGreaterOrEqual(Level.INFO); }
    public boolean isWarnEnabled()  { return config.level.isGreaterOrEqual(Level.WARN); }
    public boolean isErrorEnabled() { return config.level.isGreaterOrEqual(Level.ERROR); }

    public void trace(String msg) { log(Level.TRACE, msg, null); }
    public void debug(String msg) { log(Level.DEBUG, msg, null); }
    public void info(String msg)  { log(Level.INFO, msg, null); }
    public void warn(String msg)  { log(Level.WARN, msg, null); }
    public void error(String msg) { log(Level.ERROR, msg, null); }
    public void error(String msg, Throwable t) { log(Level.ERROR, msg, t); }

    // 通用格式化方法
    private String formatMessage(String format, Object... args) {
        if (args == null || args.length == 0) {
            return format;
        }
        StringBuilder sb = new StringBuilder();
        int argIdx = 0;
        int lastIdx = 0;
        int idx;
        while ((idx = format.indexOf("{}", lastIdx)) != -1 && argIdx < args.length) {
            sb.append(format, lastIdx, idx);
            sb.append(args[argIdx++]);
            lastIdx = idx + 2;
        }
        sb.append(format.substring(lastIdx));
        return sb.toString();
    }

    // 新增带参数的 info 方法
    public void info(String format, Object... args) {
        if (isInfoEnabled()) {
            String msg = formatMessage(format, args);
            log(Level.INFO, msg, null);
        }
    }

    // debug
    public void debug(String format, Object... args) {
        if (isDebugEnabled()) {
            String msg = formatMessage(format, args);
            log(Level.DEBUG, msg, null);
        }
    }

    // warn
    public void warn(String format, Object... args) {
        if (isWarnEnabled()) {
            String msg = formatMessage(format, args);
            log(Level.WARN, msg, null);
        }
    }

    // error (无异常)
    public void error(String format, Object... args) {
        if (isErrorEnabled()) {
            String msg = formatMessage(format, args);
            log(Level.ERROR, msg, null);
        }
    }

    // error (带异常) – 参数顺序：先格式化字符串和参数，最后是 Throwable
    public void error(String format, Throwable t, Object... args) {
        if (isErrorEnabled()) {
            String msg = formatMessage(format, args);
            log(Level.ERROR, msg, t);
        }
    }

    private void log(Level level, String msg, Throwable t) {
        if (!level.isGreaterOrEqual(config.level)) return;

        LogEvent event = LogEvent.builder()
                .level(level)
                .loggerName(name)
                .message(msg)
                .throwable(t)
                .timestamp(System.currentTimeMillis())
                .threadName(Thread.currentThread().getName())
                .caller(findCaller())
                .build();

        for (Appender appender : config.appenders) {
            appender.doAppend(event);
        }
        // 支持 additivity 传播到父 Logger
        if (config.additive && !"ROOT".equals(name)) {
            propagateToParent(level, msg, t);
        }
    }

    private void propagateToParent(Level level, String msg, Throwable t) {
        // 简化：使用 LoggerFactory 获取父 logger（按点分隔）
        String parentName = name.lastIndexOf('.') > 0 ? name.substring(0, name.lastIndexOf('.')) : "ROOT";
        Logger parent = LoggerFactory.getLogger(parentName);
        if (parent != this) {
            parent.log(level, msg, t);
        }
    }

    private StackTraceElement findCaller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        boolean foundLog = false;
        for (StackTraceElement ste : stack) {
            String className = ste.getClassName();
            if (foundLog && !className.startsWith("com.framework.log.")) {
                return ste;
            }
            if (className.equals(Logger.class.getName()) || className.equals(LoggerFactory.class.getName())) {
                foundLog = true;
            }
        }
        return null;
    }
}