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
    private PrintWriter writer;  // 懒加载

    public Response(OutputStream output) {
        this.output = output;
    }

    // ---------- 实现 ServletResponse / HttpServletResponse ----------
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
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                MessageConverters.writeToResponse(responseBody, baos, contentType);
                bodyBytes = baos.toByteArray();
            } catch (Exception e) {
                bodyBytes = responseBody.toString().getBytes(StandardCharsets.UTF_8);
            }
        } else {
            bodyBytes = new byte[0];
        }
        try (PrintWriter pw = new PrintWriter(output, true)) {
            pw.print("HTTP/1.1 " + statusCode + " " + statusMessage + "\r\n");
            pw.print("Content-Type: " + contentType + "\r\n");
            pw.print("Content-Length: " + bodyBytes.length + "\r\n");
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                pw.print(entry.getKey() + ": " + entry.getValue() + "\r\n");
            }
            pw.print("\r\n");
            pw.flush();
            output.write(bodyBytes);
            output.flush();
        }
        committed = true;
    }

    @Override
    public void sendRedirect(String location) throws Exception {
        setStatus(302, "Found");
        setHeader("Location", location);
        send();
    }

    // ---------- 扩展方法（非接口，供内部使用） ----------
    public OutputStream getRawOutputStream() {
        return output;
    }

    public void sendStatic(InputStream inputStream, String contentType, long contentLength) throws Exception {
        if (committed) return;
        this.contentType = contentType;
        this.statusCode = 200;
        this.statusMessage = "OK";
        try (PrintWriter pw = new PrintWriter(output, true)) {
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
        }
        committed = true;
    }

    public boolean isCommitted() {
        return committed;
    }
}