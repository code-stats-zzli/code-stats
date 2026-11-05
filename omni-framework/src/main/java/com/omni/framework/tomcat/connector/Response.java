package com.omni.framework.tomcat.connector;

import com.omni.framework.spring.mvc.MessageConverters;
import com.omni.framework.tomcat.servlet.HttpServletResponse;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Response implements HttpServletResponse {
    private final OutputStream output;
    private int statusCode = 200;
    private String statusMessage = "OK";
    private Object responseBody;
    private String contentType = "text/html; charset=utf-8";
    private final Map<String, String> headers = new HashMap<>();
    private boolean committed = false;
    private PrintWriter writer;

    public Response(OutputStream output) {
        this.output = output;
    }

    @Override
    public void setContentType(String type) {
        this.contentType = type;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public void setHeader(String name, String value) {
        headers.put(name, value);
    }

    @Override
    public String getHeader(String name) {
        return headers.get(name);
    }

    @Override
    public OutputStream getOutputStream() {
        return output;
    }

    @Override
    public PrintWriter getWriter() {
        if (writer == null) {
            writer = new PrintWriter(output, true);
        }
        return writer;
    }

    @Override
    public void setStatus(int code, String message) {
        this.statusCode = code;
        this.statusMessage = message;
    }

    @Override
    public void write(String text) {
        this.responseBody = text;
    }

    @Override
    public void setResponseBody(Object obj) {
        this.responseBody = obj;
    }

    @Override
    public void send() throws Exception {
        if (committed) return;

        byte[] bodyBytes;
        if (responseBody != null) {
            if (responseBody instanceof String) {
                String str = (String) responseBody;
                String trimmed = str.trim();
                if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                    bodyBytes = str.getBytes(StandardCharsets.UTF_8);
                } else {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    MessageConverters.writeToResponse(responseBody, baos, contentType);
                    bodyBytes = baos.toByteArray();
                }
            } else {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                MessageConverters.writeToResponse(responseBody, baos, contentType);
                bodyBytes = baos.toByteArray();
            }
        } else {
            bodyBytes = new byte[0];
        }

        // 输出响应头：必须使用 PrintWriter，但绝不能关闭它（否则会关闭底层输出流）
        PrintWriter pw = new PrintWriter(output, true);
        pw.print("HTTP/1.1 " + statusCode + " " + statusMessage + "\r\n");
        pw.print("Content-Type: " + contentType + "\r\n");
        pw.print("Content-Length: " + bodyBytes.length + "\r\n");
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            pw.print(entry.getKey() + ": " + entry.getValue() + "\r\n");
        }
        pw.print("\r\n");
        pw.flush();          // 只刷新，不关闭

        // 输出响应体
        output.write(bodyBytes);
        output.flush();

        committed = true;
    }

    @Override
    public void sendRedirect(String location) throws Exception {
        setStatus(302, "Found");
        setHeader("Location", location);
        send();
    }

    public OutputStream getRawOutputStream() {
        return output;
    }

    public void sendStatic(InputStream inputStream, String contentType, long contentLength) throws IOException {
        if (committed) return;
        this.contentType = contentType;
        this.statusCode = 200;
        this.statusMessage = "OK";

        PrintWriter pw = new PrintWriter(output, true);
        pw.print("HTTP/1.1 200 OK\r\n");
        pw.print("Content-Type: " + contentType + "\r\n");
        pw.print("Content-Length: " + contentLength + "\r\n");
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            pw.print(entry.getKey() + ": " + entry.getValue() + "\r\n");
        }
        pw.print("\r\n");
        pw.flush();

        inputStream.transferTo(output);
        output.flush();
        committed = true;
    }

    public boolean isCommitted() {
        return committed;
    }
}