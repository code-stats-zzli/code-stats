package com.codestats.framework.tomcat.filter;

import com.codestats.framework.tomcat.connector.Request;
import com.codestats.framework.tomcat.connector.Response;

public interface FilterChain {
    void doFilter(Request request, Response response) throws Exception;
}