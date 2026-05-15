package com.omni.run;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

public class LogDemo {
    private static final Logger log = LoggerFactory.getLogger(LogDemo.class);

    public static void main(String[] args) {
        log.info("Application started");
        log.debug("Debug message, user: {},Alice"); // 实际需要自己 format
        try {
            throw new RuntimeException("Test exception");
        } catch (Exception e) {
            log.error("Error occurred", e);
        }
    }
}
