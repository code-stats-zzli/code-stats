package com.omni.framework.tomcat.test;

/**
 * Servlet 接口，模拟 javax.servlet.Servlet。
 */
public interface Servlet {
    void service(Request request, Response response) throws Exception;
}