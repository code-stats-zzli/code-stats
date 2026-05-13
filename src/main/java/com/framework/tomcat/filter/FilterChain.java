package com.framework.tomcat.filter;

import com.framework.tomcat.connector.Request;
import com.framework.tomcat.connector.Response;

public interface FilterChain {
    void doFilter(Request request, Response response) throws Exception;
}