package com.framework.tomcat.listener;

import com.framework.tomcat.core.LifecycleEvent;
import com.framework.log.LoggerFactory;
import com.framework.log.core.Logger;

public class ContextListenerDemo implements ServletContextListener {
    private static final Logger log = LoggerFactory.getLogger(ContextListenerDemo.class);

    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        if ("start".equals(event.getType())) contextInitialized(event);
        else if ("destroy".equals(event.getType())) contextDestroyed(event);
    }

    @Override
    public void contextInitialized(LifecycleEvent event) {
        log.info("Context Initialized: {}", event.getSource());
    }

    @Override
    public void contextDestroyed(LifecycleEvent event) {
        log.info("Context Destroyed: {}", event.getSource());
    }
}