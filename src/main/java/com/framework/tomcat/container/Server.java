package com.framework.tomcat.container;

public interface Server extends Container {
    void addService(Service service);
    Service[] findServices();
    void removeService(Service service);
}