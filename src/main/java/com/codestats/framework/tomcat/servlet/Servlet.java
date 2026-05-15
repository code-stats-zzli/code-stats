package com.codestats.framework.tomcat.servlet;

import com.codestats.framework.tomcat.connector.Request;
import com.codestats.framework.tomcat.connector.Response;

public interface Servlet {
    void init() throws Exception;
    void service(Request req, Response res) throws Exception;
    void destroy();
}