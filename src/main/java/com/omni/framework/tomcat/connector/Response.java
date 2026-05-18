package com.omni.framework.tomcat.connector;



import com.omni.framework.spring.mvc.MessageConverters;
import com.omni.framework.tomcat.servlet.HttpServletResponse;
import com.omni.framework.util.FileUtils;

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
            // ★★★ 关键修复：如果 responseBody 已经是 JSON 字符串，直接获取字节，不再经过 MessageConverters
            if (responseBody instanceof String) {
                String str = (String) responseBody;
                String trimmed = str.trim();
                if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                    // 已经是 JSON 格式，直接输出
                    bodyBytes = str.getBytes(StandardCharsets.UTF_8);
                } else {
                    // 普通字符串，也许需要转换成 JSON 字符串（很少见），仍走转换器
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    MessageConverters.writeToResponse(responseBody, baos, contentType);
                    bodyBytes = baos.toByteArray();
                }
            } else {
                // 非字符串对象（Map/List/POJO），走转换器
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                MessageConverters.writeToResponse(responseBody, baos, contentType);
                bodyBytes = baos.toByteArray();
            }
        } else {
            bodyBytes = new byte[0];
        }

        // 以下发送响应头的代码保持不变（使用 PrintWriter 或直接写入）
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

    public void sendStatic(InputStream inputStream, String contentType, long contentLength) throws IOException {
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
            FileUtils.transferTo(inputStream, output);
            output.flush();
        }
        committed = true;
    }

    public boolean isCommitted() {
        return committed;
    }
}