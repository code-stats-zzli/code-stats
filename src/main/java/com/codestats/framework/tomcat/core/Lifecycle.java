package com.codestats.framework.tomcat.core;

public interface Lifecycle {
    String INIT_EVENT = "init";
    String START_EVENT = "start";
    String STOP_EVENT = "stop";
    String DESTROY_EVENT = "destroy";

    void init();
    void start();
    void stop();
    void destroy();

    void addLifecycleListener(LifecycleListener listener);
    LifecycleListener[] findLifecycleListeners();
    void removeLifecycleListener(LifecycleListener listener);
    LifecycleState getState();
}