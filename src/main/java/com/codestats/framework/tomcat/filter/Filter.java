package com.codestats.framework.tomcat.filter;

import com.codestats.framework.tomcat.connector.Request;
import com.codestats.framework.tomcat.connector.Response;

public interface Filter {
    void init(FilterConfig config) throws Exception;
    void doFilter(Request request, Response response, FilterChain chain) throws Exception;
    void destroy();
}