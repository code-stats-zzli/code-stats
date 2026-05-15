package com.omni.framework.tomcat.servlet;

import java.io.OutputStream;
import java.io.PrintWriter;

public interface ServletResponse {
    void setContentType(String type);
    String getContentType();
    void setHeader(String name, String value);
    String getHeader(String name);
    OutputStream getOutputStream();
    PrintWriter getWriter();
    void setStatus(int code, String message);
    void write(String text);
    void setResponseBody(Object obj);
    void send() throws Exception;
}