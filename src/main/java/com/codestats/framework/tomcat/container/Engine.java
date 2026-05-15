package com.codestats.framework.tomcat.container;

public interface Engine extends Container {
    String getDefaultHost();
    void setDefaultHost(String defaultHost);
}