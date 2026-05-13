package com.framework.tomcat.listener;

import com.framework.tomcat.core.LifecycleEvent;
import com.framework.tomcat.core.LifecycleListener;

public interface ServletContextListener extends LifecycleListener {
    void contextInitialized(LifecycleEvent event);
    void contextDestroyed(LifecycleEvent event);
}