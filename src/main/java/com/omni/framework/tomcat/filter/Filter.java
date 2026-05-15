package com.omni.framework.tomcat.filter;

import com.omni.framework.tomcat.connector.Request;
import com.omni.framework.tomcat.connector.Response;

public interface Filter {
    void init(FilterConfig config) throws Exception;
    void doFilter(Request request, Response response, FilterChain chain) throws Exception;
    void destroy();
}