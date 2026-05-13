package com.framework.tomcat.listener;

import com.framework.tomcat.core.LifecycleEvent;

public class ContextListenerDemo implements ServletContextListener {
    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        if ("start".equals(event.getType())) contextInitialized(event);
        else if ("destroy".equals(event.getType())) contextDestroyed(event);
    }
    @Override
    public void contextInitialized(LifecycleEvent event) { System.out.println(">>> Context Initialized: " + event.getSource()); }
    @Override
    public void contextDestroyed(LifecycleEvent event) { System.out.println(">>> Context Destroyed: " + event.getSource()); }
}