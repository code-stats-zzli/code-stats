package com.omni.framework.tomcat.container;

import com.omni.framework.tomcat.connector.Connector;

public interface Service extends Container {
    Server getServer();
    void setServer(Server server);
    Connector getConnector();
    void setConnector(Connector connector);
    Engine getEngine();
    void setEngine(Engine engine);
}