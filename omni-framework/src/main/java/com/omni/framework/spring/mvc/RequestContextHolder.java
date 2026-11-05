package com.omni.framework.spring.mvc;


import com.omni.framework.tomcat.servlet.HttpServletRequest;

public class RequestContextHolder {
    private static final ThreadLocal<HttpServletRequest> requestHolder = new ThreadLocal<>();

    public static void setRequest(HttpServletRequest request) {
        requestHolder.set(request);
    }

    public static HttpServletRequest getRequest() {
        return requestHolder.get();
    }

    public static void clear() {
        requestHolder.remove();
    }
}