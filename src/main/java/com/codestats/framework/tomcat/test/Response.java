package com.codestats.framework.tomcat.test;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 封装 HTTP 响应，支持写入内容并发送。
 */
public class Response {

    private final OutputStream output;
    private int statusCode = 200;
    private String statusMessage = "OK";
    private StringBuilder content = new StringBuilder();

    public Response(OutputStream output) {
        this.output = output;
    }

    public void setStatus(int code, String message) {
        this.statusCode = code;
        this.statusMessage = message;
    }

    public void write(String text) {
        content.append(text);
    }

    public void send() throws IOException {
        String contentStr = content.toString();
        byte[] contentBytes = contentStr.getBytes(StandardCharsets.UTF_8);

        PrintWriter writer = new PrintWriter(output, true);
        // 响应行
        writer.print("HTTP/1.1 " + statusCode + " " + statusMessage + "\r\n");
        // 响应头
        writer.print("Content-Type: text/html; charset=utf-8\r\n");
        writer.print("Content-Length: " + contentBytes.length + "\r\n");
        writer.print("\r\n");
        writer.flush();

        // 响应体
        output.write(contentBytes);
        output.flush();
    }
}