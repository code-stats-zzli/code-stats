package com.framework.tomcat.container;

public interface Host extends Container {
    String getAppBase();
    void setAppBase(String appBase);
}