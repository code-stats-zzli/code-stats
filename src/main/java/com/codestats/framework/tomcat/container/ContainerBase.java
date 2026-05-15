package com.codestats.framework.tomcat.container;


import com.codestats.framework.tomcat.core.LifecycleEvent;
import com.codestats.framework.tomcat.core.LifecycleListener;
import com.codestats.framework.tomcat.core.LifecycleState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public abstract class ContainerBase implements Container {
    protected String name;
    protected Container parent;
    protected final ConcurrentHashMap<String, Container> children = new ConcurrentHashMap<>();
    protected final List<LifecycleListener> listeners = new ArrayList<>();
    protected LifecycleState state = LifecycleState.NEW;
    protected Pipeline pipeline;

    public ContainerBase() { this.pipeline = new SimplePipeline(this); }

    @Override public void addLifecycleListener(LifecycleListener listener) { listeners.add(listener); }
    @Override public LifecycleListener[] findLifecycleListeners() { return listeners.toArray(new LifecycleListener[0]); }
    @Override public void removeLifecycleListener(LifecycleListener listener) { listeners.remove(listener); }
    @Override public LifecycleState getState() { return state; }
    protected void fireLifecycleEvent(String type, Object data) {
        LifecycleEvent event = new LifecycleEvent(this, type, data);
        for (LifecycleListener listener : listeners) listener.lifecycleEvent(event);
    }
    @Override public String getName() { return name; }
    @Override public void setName(String name) { this.name = name; }
    @Override public Container getParent() { return parent; }
    @Override public void setParent(Container parent) { this.parent = parent; }
    @Override public void addChild(Container child) { child.setParent(this); children.put(child.getName(), child); }
    @Override public Container findChild(String name) { return children.get(name); }
    @Override public Container[] findChildren() { return children.values().toArray(new Container[0]); }
    @Override public void removeChild(Container child) { children.remove(child.getName()); }
    @Override public Pipeline getPipeline() { return pipeline; }
    @Override public ClassLoader getLoader() { return (parent != null) ? parent.getLoader() : null; }
    @Override public abstract void init();
    @Override public abstract void start();
    @Override public abstract void stop();
    @Override public abstract void destroy();
}