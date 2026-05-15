package com.omni.framework.tomcat.core;

public enum LifecycleState {
    NEW(false), INITIALIZING(false), INITIALIZED(false), STARTING_PREP(false),
    STARTING(true), STARTED(true), STOPPING_PREP(true), STOPPING(false),
    STOPPED(false), DESTROYING(false), DESTROYED(false);
    private final boolean available;
    LifecycleState(boolean available) { this.available = available; }
    public boolean isAvailable() { return available; }
}