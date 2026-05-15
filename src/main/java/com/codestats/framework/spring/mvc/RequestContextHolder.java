package com.codestats.framework.spring.mvc;

import com.codestats.framework.tomcat.connector.Request;

public class RequestContextHolder {
    private static final ThreadLocal<Request> requestHolder = new ThreadLocal<>();

    public static void setRequest(Request request) {
        requestHolder.set(request);
    }

    public static Request getRequest() {
        return requestHolder.get();
    }

    public static void clear() {
        requestHolder.remove();
    }
}