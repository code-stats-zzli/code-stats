package com.omni.framework.log.core;

import com.omni.framework.log.Level;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.appender.Appender;
import com.omni.framework.log.config.Configuration;

import java.util.Arrays;

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

    // ==================== 核心：占位符格式化 ====================
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

    /**
     * ★ 新增：检测 args 末尾是否带了 Throwable
     * 让 log.error("xxx {} {}", a, b, e) 这种调用不再丢失异常堆栈。
     * 返回值：[0] = 去掉 Throwable 后的真实参数数组，[1] = 提取出的 Throwable（可能为 null）
     */
    private Object[] extractThrowable(Object[] args) {
        if (args != null && args.length > 0 && args[args.length - 1] instanceof Throwable) {
            Throwable t = (Throwable) args[args.length - 1];
            Object[] realArgs = Arrays.copyOf(args, args.length - 1);
            return new Object[]{realArgs, t};
        }
        return new Object[]{args, null};
    }

    // ==================== 带占位符 + 可选 Throwable 的日志方法 ====================

    public void trace(String format, Object... args) {
        if (!isTraceEnabled()) return;
        Object[] r = extractThrowable(args);
        log(Level.TRACE, formatMessage(format, (Object[]) r[0]), (Throwable) r[1]);
    }

    public void debug(String format, Object... args) {
        if (!isDebugEnabled()) return;
        Object[] r = extractThrowable(args);
        log(Level.DEBUG, formatMessage(format, (Object[]) r[0]), (Throwable) r[1]);
    }

    public void info(String format, Object... args) {
        if (!isInfoEnabled()) return;
        Object[] r = extractThrowable(args);
        log(Level.INFO, formatMessage(format, (Object[]) r[0]), (Throwable) r[1]);
    }

    public void warn(String format, Object... args) {
        if (!isWarnEnabled()) return;
        Object[] r = extractThrowable(args);
        log(Level.WARN, formatMessage(format, (Object[]) r[0]), (Throwable) r[1]);
    }

    /**
     * 带占位符的 error；args 末尾若是 Throwable 会被自动识别为异常并打印完整堆栈。
     * 这样 log.error("处理请求失败: {} {}", method, uri, e) 也能正确工作。
     */
    public void error(String format, Object... args) {
        if (!isErrorEnabled()) return;
        Object[] r = extractThrowable(args);
        log(Level.ERROR, formatMessage(format, (Object[]) r[0]), (Throwable) r[1]);
    }

    /**
     * 显式 Throwable 版本：log.error("失败: {}", "ctx", e) —— 第二个参数必须是 Throwable
     * 保留此重载以兼容原有调用，语义最清晰。
     */
    public void error(String format, Throwable t, Object... args) {
        if (!isErrorEnabled()) return;
        log(Level.ERROR, formatMessage(format, args), t);
    }

    // ==================== 内部 ====================

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
        if (config.additive && !"ROOT".equals(config.name)) {
            propagateToParent(level, msg, t);
        }
    }

    private void propagateToParent(Level level, String msg, Throwable t) {
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
            if (foundLog && !className.startsWith("com.omni.framework.log.")) {
                return ste;
            }
            if (className.equals(Logger.class.getName())
                    || className.equals(LoggerFactory.class.getName())) {
                foundLog = true;
            }
        }
        return null;
    }
}