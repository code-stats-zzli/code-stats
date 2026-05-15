package com.codestats.framework.tomcat.listener;

import com.codestats.framework.tomcat.core.LifecycleEvent;
import com.codestats.framework.tomcat.core.LifecycleListener;

public interface ServletContextListener extends LifecycleListener {
    void contextInitialized(LifecycleEvent event);
    void contextDestroyed(LifecycleEvent event);
}