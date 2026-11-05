package com.omni.framework.tomcat.listener;

import com.omni.framework.tomcat.core.LifecycleEvent;

// 实际可直接复用 LifecycleEvent，此处仅为接口定义
public class ServletContextEvent extends LifecycleEvent {
    public ServletContextEvent(Object source, String type, Object data) { super(null, type, data); }
}