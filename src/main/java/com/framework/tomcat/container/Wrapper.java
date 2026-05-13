package com.framework.tomcat.container;

public interface Wrapper extends Container {
    String getServletClass();
    void setServletClass(String servletClass);
}