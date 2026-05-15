package com.omni.framework.tomcat.servlet;

import com.omni.framework.tomcat.connector.Request;
import com.omni.framework.tomcat.connector.Response;

public interface Servlet {
    void init() throws Exception;
    void service(Request req, Response res) throws Exception;
    void destroy();
}