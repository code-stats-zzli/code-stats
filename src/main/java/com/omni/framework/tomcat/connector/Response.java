package com.omni.framework.tomcat.connector;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.mvc.MessageConverters;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Response {
    private static final Logger log = LoggerFactory.getLogger(Response.class);
    private final OutputStream output;
    private int statusCode = 200;
    private String statusMessage = "OK";
    private Object responseBody;
    private String contentType = "text/html; charset=utf-8";
    private final Map<String, String> headers = new HashMap<>();
    private boolean committed = false;

    public Response(OutputStream output) { this.output = output; }

    public void setStatus(int code, String message) { this.statusCode = code; this.statusMessage = message; }
    public void write(String text) { this.responseBody = text; }
    public void setResponseBody(Object obj) { this.responseBody = obj; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public OutputStream getRawOutputStream() { return output; }
    public void setHeader(String name, String value) { headers.put(name, value); }

    public void send() throws IOException {
        if (committed) return;
        // 如果尚未设置 Content-Type 但 responseBody 已知，尝试推导
        if (contentType == null && responseBody != null) {
            contentType = "application/json";
        }
        byte[] bodyBytes;
        if (responseBody != null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                MessageConverters.writeToResponse(responseBody, baos, contentType);
                bodyBytes = baos.toByteArray();
            } catch (Exception e) {
                log.error("Message conversion failed", e);
                bodyBytes = responseBody.toString().getBytes(StandardCharsets.UTF_8);
            }
        } else {
            bodyBytes = new byte[0];
        }

        try (PrintWriter writer = new PrintWriter(output, true)) {
            writer.print("HTTP/1.1 " + statusCode + " " + statusMessage + "\r\n");
            writer.print("Content-Type: " + contentType + "\r\n");
            writer.print("Content-Length: " + bodyBytes.length + "\r\n");
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                writer.print(entry.getKey() + ": " + entry.getValue() + "\r\n");
            }
            writer.print("\r\n");
            writer.flush();
            output.write(bodyBytes);
            output.flush();
        }
        committed = true;
    }

    public void sendStatic(InputStream inputStream, String contentType, long contentLength) throws IOException {
        if (committed) return;
        this.contentType = contentType;
        this.statusCode = 200;
        this.statusMessage = "OK";
        try (PrintWriter writer = new PrintWriter(output, true)) {
            writer.print("HTTP/1.1 200 OK\r\n");
            writer.print("Content-Type: " + contentType + "\r\n");
            writer.print("Content-Length: " + contentLength + "\r\n");
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                writer.print(entry.getKey() + ": " + entry.getValue() + "\r\n");
            }
            writer.print("\r\n");
            writer.flush();
            inputStream.transferTo(output);
            output.flush();
        }
        committed = true;
    }

    public String getContentType() {
        return contentType;
    }
}