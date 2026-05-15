package com.omni.framework.tomcat.servlet;

import com.omni.framework.tomcat.connector.Request;
import com.omni.framework.tomcat.connector.Response;

public class HelloServlet extends HttpServlet {

    protected void doGet(Request req, Response res) throws Exception {
        String name = req.getUri().replace("/", "");
        if (name.isEmpty()) name = "World";
        res.write("<html><body><h1>Hello, " + name + "!</h1></body></html>");
    }
}