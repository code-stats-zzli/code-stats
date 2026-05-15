package com.omni.framework.tomcat.servlet;

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

    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws Exception {
        res.setStatus(405, "GET Not Supported");
        res.send();
    }

    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws Exception {
        res.setStatus(405, "POST Not Supported");
        res.send();
    }
}