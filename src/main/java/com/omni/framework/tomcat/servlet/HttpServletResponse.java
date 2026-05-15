package com.omni.framework.tomcat.servlet;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface HttpServletResponse extends ServletResponse {
    // 重定向
    void sendRedirect(String location) throws Exception;

    // 状态码常量（可选）
    int SC_OK = 200;
    int SC_NOT_FOUND = 404;
    int SC_INTERNAL_SERVER_ERROR = 500;

    void sendStatic(InputStream fis, String contentType, long length) throws IOException;

    OutputStream getRawOutputStream();
    // ... 可继续添加
}