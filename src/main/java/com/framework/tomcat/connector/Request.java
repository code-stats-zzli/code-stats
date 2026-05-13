package com.framework.tomcat.connector;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Request {
    private final InputStream input;
    private String uri;
    private String method;
    private String contextPath = "";
    private String queryString;  // 新增

    public Request(InputStream input) { this.input = input; }

    public void parse() throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        String line = reader.readLine();
        if (line != null) {
            String[] parts = line.split(" ");
            if (parts.length >= 2) {
                method = parts[0];
                String fullUri = parts[1];
                // 分离 URI 和查询字符串
                int qIdx = fullUri.indexOf('?');
                if (qIdx >= 0) {
                    queryString = fullUri.substring(qIdx + 1);
                    fullUri = fullUri.substring(0, qIdx);
                }
                // 分离 contextPath 和 servletPath
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
        while ((line = reader.readLine()) != null && !line.isEmpty()) {}
    }

    public String getUri() { return uri; }
    public String getMethod() { return method; }
    public String getContextPath() { return contextPath; }
    public String getQueryString() { return queryString; }  // 新增
}