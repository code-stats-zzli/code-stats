package com.codestats.framework.tomcat.servlet;

import com.codestats.framework.tomcat.connector.Request;
import com.codestats.framework.tomcat.connector.Response;

public abstract class HttpServlet implements Servlet {
    @Override public void init() throws Exception { }
    @Override public void destroy() { }
    @Override public void service(Request req, Response res) throws Exception {
        if ("GET".equalsIgnoreCase(req.getMethod())) doGet(req, res);
        else if ("POST".equalsIgnoreCase(req.getMethod())) doPost(req, res);
        else res.setStatus(405, "Method Not Allowed");
    }
    protected void doGet(Request req, Response res) throws Exception { res.setStatus(405, "GET Not Supported"); }
    protected void doPost(Request req, Response res) throws Exception { res.setStatus(405, "POST Not Supported"); }
}