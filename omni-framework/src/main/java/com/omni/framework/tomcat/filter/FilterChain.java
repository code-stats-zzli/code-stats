package com.omni.framework.tomcat.filter;

import com.omni.framework.tomcat.connector.Request;
import com.omni.framework.tomcat.connector.Response;

public interface FilterChain {
    void doFilter(Request request, Response response) throws Exception;
}