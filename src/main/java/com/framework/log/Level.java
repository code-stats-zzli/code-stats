package com.framework.log;

public enum Level {
    TRACE(0), DEBUG(1), INFO(2), WARN(3), ERROR(4), OFF(5);

    private final int intValue;

    Level(int intValue) {
        this.intValue = intValue;
    }

    public int intValue() {
        return intValue;
    }

    public boolean isGreaterOrEqual(Level other) {
        return this.intValue >= other.intValue;
    }

    public static Level toLevel(String s) {
        if (s == null) return DEBUG;
        switch (s.toUpperCase()) {
            case "TRACE": return TRACE;
            case "DEBUG": return DEBUG;
            case "INFO":  return INFO;
            case "WARN":  return WARN;
            case "ERROR": return ERROR;
            case "OFF":   return OFF;
            default:      return DEBUG;
        }
    }
}