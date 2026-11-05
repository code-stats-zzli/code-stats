package com.omni.framework.spring.context;


public class SpringContextHolder {
    private static AbstractApplicationContext applicationContext;

    public static void setApplicationContext(AbstractApplicationContext context) {
        applicationContext = context;
    }

    public static AbstractApplicationContext getApplicationContext() {
        if (applicationContext == null) {
            throw new IllegalStateException("ApplicationContext not initialized");
        }
        return applicationContext;
    }
}