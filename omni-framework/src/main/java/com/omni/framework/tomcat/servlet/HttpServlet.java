package com.omni.framework.tomcat.servlet;

import com.omni.framework.tomcat.connector.Request;
import com.omni.framework.tomcat.connector.Response;

public abstract class HttpServlet implements Servlet {
    @Override public void init() throws Exception { }
    @Override public void destroy() { }


    public void service(HttpServletRequest req, HttpServletResponse res) throws Exception {
        if ("GET".equalsIgnoreCase(req.getMethod())) {
            doGet(req, res);
        } else if ("POST".equalsIgnoreCase(req.getMethod())) {
            doPost(req, res);
        } else {
            res.setStatus(405, "Method Not Allowed");
            res.send();
        }
    }

    protected abstract void doGet(HttpServletRequest req, HttpServletResponse res) throws Exception;

    protected abstract void doPost(HttpServletRequest req, HttpServletResponse res) throws Exception;

}