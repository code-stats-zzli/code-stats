package com.omni.framework.tomcat.servlet;

import com.omni.framework.spring.mvc.MultipartResolver;
import com.omni.framework.tomcat.connector.Request;
import com.omni.framework.tomcat.session.HttpSession;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 包装 HttpServletRequest，缓存请求体以便多次读取
 */
public class CachedBodyHttpServletRequest implements HttpServletRequest {
    private final HttpServletRequest originalRequest;
    private final byte[] cachedBody;

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        this.originalRequest = request;
        try (InputStream is = request.getInputStream()) {
            this.cachedBody = is.readAllBytes();
        }
    }

    // ========== 委托给原始 request 的方法 ==========
    @Override public String getMethod() { return originalRequest.getMethod(); }
    @Override public String getUri() { return originalRequest.getUri(); }
    @Override public String getContextPath() { return originalRequest.getContextPath(); }
    @Override public String getQueryString() { return originalRequest.getQueryString(); }
    @Override public String getBody() { return new String(cachedBody, StandardCharsets.UTF_8); }
    @Override public Map<String, String> getParameters() { return originalRequest.getParameters(); }
    @Override public String getParameter(String name) { return originalRequest.getParameter(name); }
    @Override public String getHeader(String name) { return originalRequest.getHeader(name); }
    @Override public String getContentType() { return originalRequest.getContentType(); }
    @Override public MultipartResolver getMultipartResolver() { return originalRequest.getMultipartResolver(); }

    @Override public String getRequestURI() { return originalRequest.getRequestURI(); }
    @Override public StringBuffer getRequestURL() { return originalRequest.getRequestURL(); }
    @Override public String getServletPath() { return originalRequest.getServletPath(); }
    @Override public String getPathInfo() { return originalRequest.getPathInfo(); }
    @Override public String getRemoteAddr() { return originalRequest.getRemoteAddr(); }
    @Override public String getRemoteHost() { return originalRequest.getRemoteHost(); }
    @Override public int getRemotePort() { return originalRequest.getRemotePort(); }
    @Override public String getLocalAddr() { return originalRequest.getLocalAddr(); }
    @Override public int getLocalPort() { return originalRequest.getLocalPort(); }
    @Override public String getServerName() { return originalRequest.getServerName(); }
    @Override public int getServerPort() { return originalRequest.getServerPort(); }
    @Override public String getScheme() { return originalRequest.getScheme(); }
    @Override public String getProtocol() { return originalRequest.getProtocol(); }
    @Override public boolean isSecure() { return originalRequest.isSecure(); }
    @Override public Enumeration<String> getHeaderNames() { return originalRequest.getHeaderNames(); }
    @Override public Enumeration<String> getHeaders(String name) { return originalRequest.getHeaders(name); }
    @Override public Enumeration<String> getParameterNames() { return originalRequest.getParameterNames(); }
    @Override public String[] getParameterValues(String name) { return originalRequest.getParameterValues(name); }
    @Override public Map<String, String[]> getParameterMap() { return originalRequest.getParameterMap(); }
    @Override public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream()));
    }
    @Override public String getCharacterEncoding() { return originalRequest.getCharacterEncoding(); }
    @Override public void setCharacterEncoding(String env) { originalRequest.setCharacterEncoding(env); }
    @Override public int getContentLength() { return originalRequest.getContentLength(); }
    @Override public long getContentLengthLong() { return originalRequest.getContentLengthLong(); }
    @Override public HttpSession getSession() { return originalRequest.getSession(); }
    @Override public HttpSession getSession(boolean create) { return originalRequest.getSession(create); }
    @Override public Request.Cookie[] getCookies() { return originalRequest.getCookies(); }
    @Override public Object getAttribute(String name) { return originalRequest.getAttribute(name); }
    @Override public void setAttribute(String name, Object value) { originalRequest.setAttribute(name, value); }
    @Override public void removeAttribute(String name) { originalRequest.removeAttribute(name); }
    @Override public Enumeration<String> getAttributeNames() { return originalRequest.getAttributeNames(); }

    // ========== 覆盖输入流方法，使用缓存 ==========
    @Override
    public InputStream getInputStream() {
        return new CachedBodyServletInputStream(cachedBody);
    }
}