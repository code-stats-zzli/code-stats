package com.framework.tomcat.servlet;

import com.framework.tomcat.connector.Request;
import com.framework.tomcat.connector.Response;

public interface Servlet {
    void init() throws Exception;
    void service(Request req, Response res) throws Exception;
    void destroy();
}