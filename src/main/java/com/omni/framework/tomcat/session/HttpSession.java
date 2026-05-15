package com.omni.framework.tomcat.session;

import java.util.Enumeration;

public interface HttpSession {
    String getId();
    long getCreationTime();
    long getLastAccessedTime();
    void setAttribute(String name, Object value);
    Object getAttribute(String name);
    void removeAttribute(String name);
    Enumeration<String> getAttributeNames();
    void invalidate();
    boolean isNew();
}