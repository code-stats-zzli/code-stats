package com.codestats.framework.tomcat.connector;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Request {
    private final InputStream input;
    private String uri;
    private String method;
    private String contextPath = "";
    private String queryString;
    private String body;   // 新增：缓存请求体

    public Request(InputStream input) {
        this.input = input;
    }

    public void parse() throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        String line = reader.readLine();
        if (line != null) {
            String[] parts = line.split(" ");
            if (parts.length >= 2) {
                method = parts[0];
                String fullUri = parts[1];
                int qIdx = fullUri.indexOf('?');
                if (qIdx >= 0) {
                    queryString = fullUri.substring(qIdx + 1);
                    fullUri = fullUri.substring(0, qIdx);
                }
                int idx = fullUri.indexOf('/', 1);
                if (idx > 0) {
                    contextPath = fullUri.substring(0, idx);
                    uri = fullUri.substring(idx);
                } else {
                    contextPath = "";
                    uri = fullUri;
                }
                if (uri.isEmpty()) uri = "/";
            }
        }
        // 读取 headers 直到空行
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            // 可在此解析具体 header，当前不需要
        }
        // 读取请求体（剩余部分）
        StringBuilder bodyBuilder = new StringBuilder();
        while (reader.ready()) {
            bodyBuilder.append((char) reader.read());
        }
        body = bodyBuilder.toString();
    }

    public String getUri() {
        return uri;
    }

    public String getMethod() {
        return method;
    }

    public String getContextPath() {
        return contextPath;
    }

    public String getQueryString() {
        return queryString;
    }

    public String getBody() {
        return body;
    }
}