package com.omni.framework.tomcat.container;

import com.omni.framework.tomcat.connector.Connector;
import com.omni.framework.tomcat.core.Lifecycle;
import com.omni.framework.tomcat.core.LifecycleState;

public class StandardService extends ContainerBase implements Service {
    private Server server;
    private Connector connector;
    private Engine engine;

    @Override public Server getServer() { return server; }
    @Override public void setServer(Server server) { this.server = server; }
    @Override public Connector getConnector() { return connector; }
    @Override public void setConnector(Connector connector) { this.connector = connector; }
    @Override public Engine getEngine() { return engine; }
    @Override public void setEngine(Engine engine) { this.engine = engine; engine.setParent(this); }

    @Override
    public void init() { state = LifecycleState.INITIALIZING; fireLifecycleEvent(Lifecycle.INIT_EVENT, null);
        if (engine != null) engine.init();
        if (connector != null) connector.init();
        state = LifecycleState.INITIALIZED; }
    @Override
    public void start() { state = LifecycleState.STARTING; fireLifecycleEvent(Lifecycle.START_EVENT, null);
        if (engine != null) engine.start();
        if (connector != null) connector.start();
        state = LifecycleState.STARTED; }
    @Override
    public void stop() { state = LifecycleState.STOPPING; fireLifecycleEvent(Lifecycle.STOP_EVENT, null);
        if (connector != null) connector.stop();
        if (engine != null) engine.stop();
        state = LifecycleState.STOPPED; }
    @Override
    public void destroy() { state = LifecycleState.DESTROYING; fireLifecycleEvent(Lifecycle.DESTROY_EVENT, null);
        if (connector != null) connector.destroy();
        if (engine != null) engine.destroy();
        state = LifecycleState.DESTROYED; }
}