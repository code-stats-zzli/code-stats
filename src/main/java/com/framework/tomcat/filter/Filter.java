package com.framework.tomcat.filter;

import com.framework.tomcat.connector.Request;
import com.framework.tomcat.connector.Response;

public interface Filter {
    void init(FilterConfig config) throws Exception;
    void doFilter(Request request, Response response, FilterChain chain) throws Exception;
    void destroy();
}