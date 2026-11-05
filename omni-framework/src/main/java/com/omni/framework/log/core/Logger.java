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

    // ★ FIX: 原实现方向写反了。
    //   原：return config.level.isGreaterOrEqual(Level.ERROR);   // INFO >= ERROR = false
    //   正确语义：当前事件级别 >= 配置级别 → 才允许输出。
    //   等价写法：Level.ERROR.isGreaterOrEqual(config.level)
    //   额外：config.level == OFF 时全部返回 false。
    public boolean isTraceEnabled() { return isEnabled(Level.TRACE); }
    public boolean isDebugEnabled() { return isEnabled(Level.DEBUG); }
    public boolean isInfoEnabled()  { return isEnabled(Level.INFO);  }
    public boolean isWarnEnabled()  { return isEnabled(Level.WARN);  }
    public boolean isErrorEnabled() { return isEnabled(Level.ERROR); }

    private boolean isEnabled(Level eventLevel) {
        if (config == null) return false;
        if (config.level == Level.OFF) return false;
        return eventLevel.isGreaterOrEqual(config.level);
    }

    public void trace(String msg) { log(Level.TRACE, msg, null); }
    public void debug(String msg) { log(Level.DEBUG, msg, null); }
    public void info(String msg)  { log(Level.INFO, msg, null); }
    public void warn(String msg)  { log(Level.WARN, msg, null); }
    public void error(String msg) { log(Level.ERROR, msg, null); }
    public void error(String msg, Throwable t) { log(Level.ERROR, msg, t); }

    private String formatMessage(String format, Object... args) {
        if (args == null || args.length == 0) return format;
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

    private Object[] extractThrowable(Object[] args) {
        if (args != null && args.length > 0 && args[args.length - 1] instanceof Throwable) {
            Throwable t = (Throwable) args[args.length - 1];
            Object[] realArgs = Arrays.copyOf(args, args.length - 1);
            return new Object[]{realArgs, t};
        }
        return new Object[]{args, null};
    }

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

    public void error(String format, Object... args) {
        if (!isErrorEnabled()) return;
        Object[] r = extractThrowable(args);
        log(Level.ERROR, formatMessage(format, (Object[]) r[0]), (Throwable) r[1]);
    }

    public void error(String format, Throwable t, Object... args) {
        if (!isErrorEnabled()) return;
        log(Level.ERROR, formatMessage(format, args), t);
    }

    private void log(Level level, String msg, Throwable t) {
        if (config == null) return;
        if (config.level == Level.OFF) return;
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