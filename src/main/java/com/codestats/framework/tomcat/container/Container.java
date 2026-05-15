package com.codestats.framework.tomcat.container;

import com.codestats.framework.tomcat.core.Lifecycle;

public interface Container extends Lifecycle {
    String getName();
    void setName(String name);
    Container getParent();
    void setParent(Container parent);
    void addChild(Container child);
    Container findChild(String name);
    Container[] findChildren();
    void removeChild(Container child);
    Pipeline getPipeline();
    ClassLoader getLoader();
}