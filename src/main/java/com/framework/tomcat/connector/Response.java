package com.framework.tomcat.connector;

import com.framework.log.LoggerFactory;
import com.framework.log.core.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Response {
    private static final Logger log = LoggerFactory.getLogger(Response.class);
    private final OutputStream output;
    private int statusCode = 200;
    private String statusMessage = "OK";
    private final StringBuilder content = new StringBuilder();
    private String contentType = "text/html; charset=utf-8";
    private final Map<String, String> headers = new HashMap<>();

    public Response(OutputStream output) { this.output = output; }

    public void setStatus(int code, String message) { this.statusCode = code; this.statusMessage = message; }
    public void write(String text) { content.append(text); }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public OutputStream getRawOutputStream() { return output; }
    public void setHeader(String name, String value) { headers.put(name, value); }

    public void send() {
        try {
            String body = content.toString();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            PrintWriter writer = new PrintWriter(output, true);
            writer.print("HTTP/1.1 " + statusCode + " " + statusMessage + "\r\n");
            writer.print("Content-Type: " + contentType + "\r\n");
            writer.print("Content-Length: " + bytes.length + "\r\n");
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                writer.print(entry.getKey() + ": " + entry.getValue() + "\r\n");
            }
            writer.print("\r\n");
            writer.flush();
            output.write(bytes);
            output.flush();
        } catch (IOException e) {
            log.error("Response send failed", e);
        } finally {
            headers.clear();
        }
    }

    public void sendStatic(InputStream inputStream, String contentType, long contentLength) throws IOException {
        this.contentType = contentType;
        this.statusCode = 200;
        this.statusMessage = "OK";

        PrintWriter writer = new PrintWriter(output, true);
        writer.print("HTTP/1.1 200 OK\r\n");
        writer.print("Content-Type: " + contentType + "\r\n");
        writer.print("Content-Length: " + contentLength + "\r\n");
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            writer.print(entry.getKey() + ": " + entry.getValue() + "\r\n");
        }
        writer.print("\r\n");
        writer.flush();

        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
        output.flush();
        headers.clear();
    }
}