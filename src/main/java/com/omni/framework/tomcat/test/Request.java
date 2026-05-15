package com.omni.framework.tomcat.test;

import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

/**
 * 封装 HTTP 请求，解析请求行和头部（本 Demo 只简单解析 URI）。
 */
public class Request {

    private final InputStream input;
    private String uri;
    private String method;

    public Request(InputStream input) {
        this.input = input;
    }

    public void parse() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        String requestLine = reader.readLine();
        if (requestLine != null && !requestLine.isEmpty()) {
            String[] parts = requestLine.split(" ");
            if (parts.length >= 2) {
                method = parts[0];
                uri = parts[1];
            }
        }
        // 忽略剩余的 headers 和 body（简化处理）
        while (reader.readLine() != null) {
            // 空行表示 headers 结束，实际应用中应继续解析直到空行
            String line = reader.readLine();
            if (line == null || line.isEmpty()) break;
        }
    }

    public String getUri() {
        return uri;
    }

    public String getMethod() {
        return method;
    }
}