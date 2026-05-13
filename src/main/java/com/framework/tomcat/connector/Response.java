// ======== 文件路径: com.framework.tomcat.connector.Response ========
// 在原有类基础上增加 headers 成员和 setHeader 方法，并修改 sendStatic 方法

package com.framework.tomcat.connector;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Response {
    private final OutputStream output;
    private int statusCode = 200;
    private String statusMessage = "OK";
    private final StringBuilder content = new StringBuilder();
    private String contentType = "text/html; charset=utf-8";

    // ★ 新增：存储自定义响应头
    private final Map<String, String> headers = new HashMap<>();

    public Response(OutputStream output) { this.output = output; }

    public void setStatus(int code, String message) { this.statusCode = code; this.statusMessage = message; }
    public void write(String text) { content.append(text); }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public OutputStream getRawOutputStream() { return output; }

    // ★ 新增：设置自定义响应头
    public void setHeader(String name, String value) {
        headers.put(name, value);
    }

    // 原有 send 方法（字符内容）
    public void send() {
        try {
            String body = content.toString();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            PrintWriter writer = new PrintWriter(output, true);
            writer.print("HTTP/1.1 " + statusCode + " " + statusMessage + "\r\n");
            writer.print("Content-Type: " + contentType + "\r\n");
            writer.print("Content-Length: " + bytes.length + "\r\n");
            // 输出自定义头
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                writer.print(entry.getKey() + ": " + entry.getValue() + "\r\n");
            }
            writer.print("\r\n");
            writer.flush();
            output.write(bytes);
            output.flush();
        } catch (IOException e) {
            System.err.println("Response send failed: " + e.getMessage());
        } finally {
            headers.clear(); // 发送后清空，避免影响下次复用
        }
    }

    /**
     * 发送静态文件（二进制流），支持自定义响应头
     */
    public void sendStatic(InputStream inputStream, String contentType, long contentLength) throws IOException {
        this.contentType = contentType;
        this.statusCode = 200;
        this.statusMessage = "OK";

        PrintWriter writer = new PrintWriter(output, true);
        writer.print("HTTP/1.1 200 OK\r\n");
        writer.print("Content-Type: " + contentType + "\r\n");
        writer.print("Content-Length: " + contentLength + "\r\n");
        // 输出自定义头（例如 Content-Disposition）
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