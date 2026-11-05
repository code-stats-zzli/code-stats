package com.omni.framework.tomcat.servlet;

import com.omni.framework.spring.mvc.MultipartResolver;
import com.omni.framework.tomcat.connector.Request;
import com.omni.framework.tomcat.session.HttpSession;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Map;

public interface HttpServletRequest extends ServletRequest {
    // 原有
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
    MultipartResolver getMultipartResolver();

    // 新增
    String getRequestURI();
    StringBuffer getRequestURL();
    String getServletPath();
    String getPathInfo();
    String getRemoteAddr();
    String getRemoteHost();
    int getRemotePort();
    String getLocalAddr();
    int getLocalPort();
    String getServerName();
    int getServerPort();
    String getScheme();
    String getProtocol();
    boolean isSecure();
    Enumeration<String> getHeaderNames();
    Enumeration<String> getHeaders(String name);
    Enumeration<String> getParameterNames();
    String[] getParameterValues(String name);
    Map<String, String[]> getParameterMap();
    BufferedReader getReader() throws IOException;
    String getCharacterEncoding();
    void setCharacterEncoding(String env);
    int getContentLength();
    long getContentLengthLong();
    HttpSession getSession();
    HttpSession getSession(boolean create);
    Request.Cookie[] getCookies();
    Object getAttribute(String name);
    void setAttribute(String name, Object value);
    void removeAttribute(String name);
    Enumeration<String> getAttributeNames();
}