package com.omni.framework.tomcat.listener;

import com.omni.framework.tomcat.core.LifecycleEvent;
import com.omni.framework.tomcat.core.LifecycleListener;

public interface ServletContextListener extends LifecycleListener {
    void contextInitialized(LifecycleEvent event);
    void contextDestroyed(LifecycleEvent event);
}