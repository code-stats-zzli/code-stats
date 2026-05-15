package com.omni.framework.tomcat.core;

import java.util.EventObject;

public class LifecycleEvent extends EventObject {
    private final String type;
    private final Object data;

    public LifecycleEvent(Lifecycle source, String type, Object data) {
        super(source);
        this.type = type;
        this.data = data;
    }
    public String getType() { return type; }
    public Object getData() { return data; }
}