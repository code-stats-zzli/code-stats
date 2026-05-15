package com.omni.framework.tomcat.servlet;

import java.io.InputStream;
import java.util.Map;

public interface ServletRequest {
    String getMethod();
    String getUri();
    String getContextPath();
    String getQueryString();
    String getBody();
    Map<String, String> getParameters();
    String getParameter(String name);
    String getHeader(String name);
    String getContentType();
    InputStream getInputStream();
}