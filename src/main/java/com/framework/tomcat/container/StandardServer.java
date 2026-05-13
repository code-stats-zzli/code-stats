package com.framework.tomcat.container;

import com.framework.tomcat.core.Lifecycle;
import com.framework.tomcat.core.LifecycleState;

import java.util.ArrayList;
import java.util.List;

public class StandardServer extends ContainerBase implements Server {
    private final List<Service> services = new ArrayList<>();

    @Override public void addService(Service service) { services.add(service); service.setServer(this); }
    @Override public Service[] findServices() { return services.toArray(new Service[0]); }
    @Override public void removeService(Service service) { services.remove(service); service.setServer(null); }

    @Override
    public void init() { state = LifecycleState.INITIALIZING; fireLifecycleEvent(Lifecycle.INIT_EVENT, null);
        for (Service s : services) s.init(); state = LifecycleState.INITIALIZED; }
    @Override
    public void start() { state = LifecycleState.STARTING; fireLifecycleEvent(Lifecycle.START_EVENT, null);
        for (Service s : services) s.start(); state = LifecycleState.STARTED; }
    @Override
    public void stop() { state = LifecycleState.STOPPING; fireLifecycleEvent(Lifecycle.STOP_EVENT, null);
        for (Service s : services) s.stop(); state = LifecycleState.STOPPED; }
    @Override
    public void destroy() { state = LifecycleState.DESTROYING; fireLifecycleEvent(Lifecycle.DESTROY_EVENT, null);
        for (Service s : services) s.destroy(); state = LifecycleState.DESTROYED; }
}