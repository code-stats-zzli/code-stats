package com.omni.framework.tomcat.servlet;

public interface Servlet {
    void init() throws Exception;
    void service(HttpServletRequest req, HttpServletResponse res) throws Exception;
    void destroy();
}