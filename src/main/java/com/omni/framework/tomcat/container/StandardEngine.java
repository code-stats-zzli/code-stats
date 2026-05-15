package com.omni.framework.tomcat.container;

import com.omni.framework.tomcat.core.Lifecycle;
import com.omni.framework.tomcat.core.LifecycleState;

public class StandardEngine extends ContainerBase implements Engine {
    private String defaultHost = "localhost";

    @Override public String getDefaultHost() { return defaultHost; }
    @Override public void setDefaultHost(String defaultHost) { this.defaultHost = defaultHost; }

    @Override
    public void init() { state = LifecycleState.INITIALIZING; fireLifecycleEvent(Lifecycle.INIT_EVENT, null);
        for (Container child : findChildren()) child.init(); state = LifecycleState.INITIALIZED; }
    @Override
    public void start() { state = LifecycleState.STARTING; fireLifecycleEvent(Lifecycle.START_EVENT, null);
        for (Container child : findChildren()) child.start(); state = LifecycleState.STARTED; }
    @Override
    public void stop() { state = LifecycleState.STOPPING; fireLifecycleEvent(Lifecycle.STOP_EVENT, null);
        for (Container child : findChildren()) child.stop(); state = LifecycleState.STOPPED; }
    @Override
    public void destroy() { state = LifecycleState.DESTROYING; fireLifecycleEvent(Lifecycle.DESTROY_EVENT, null);
        for (Container child : findChildren()) child.destroy(); state = LifecycleState.DESTROYED; }
}