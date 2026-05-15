package com.omni.framework.tomcat.test;

/**
 * 一个示例 Servlet，输出简单的 HTML。
 */
public class HelloServlet implements Servlet {

    @Override
    public void service(Request request, Response response) throws Exception {
        String name = request.getUri().replace("/", "");
        if (name.isEmpty()) name = "World";
        response.write("<html>");
        response.write("<head><title>Hello Servlet</title></head>");
        response.write("<body>");
        response.write("<h1>Hello, " + name + "!</h1>");
        response.write("<p>This is a simple Tomcat demo.</p>");
        response.write("</body>");
        response.write("</html>");
    }
}