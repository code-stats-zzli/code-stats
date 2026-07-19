// JStatsException.java
package com.omni.framework.jstats;

public class JStatsException extends Exception {
    public JStatsException(String message) {
        super(message);
    }
    public JStatsException(String message, Throwable cause) {
        super(message, cause);
    }
}