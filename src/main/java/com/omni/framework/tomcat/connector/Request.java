package com.omni.framework.tomcat.connector;

import com.omni.framework.spring.mvc.MultipartResolver;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Request {
    private final InputStream input;
    private String uri;
    private String method;
    private String contextPath = "";
    private String queryString;
    private String body;
    private Map<String, String> parameters = new HashMap<>();       // 查询参数 + 表单参数
    private Map<String, String> headers = new HashMap<>();
    private String contentType;
    private MultipartResolver multipartResolver;

    public Request(InputStream input) {
        this.input = input;
    }

    public void parse() throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        String requestLine = reader.readLine();
        if (requestLine == null) return;
        String[] parts = requestLine.split(" ");
        if (parts.length >= 2) {
            method = parts[0];
            String fullUri = parts[1];
            int qIdx = fullUri.indexOf('?');
            if (qIdx >= 0) {
                queryString = fullUri.substring(qIdx + 1);
                fullUri = fullUri.substring(0, qIdx);
                parseQueryString(queryString, parameters);
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

        // 读取 headers
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                String name = line.substring(0, colon).trim().toLowerCase(Locale.ENGLISH);
                String value = line.substring(colon + 1).trim();
                headers.put(name, value);
                if ("content-type".equals(name)) {
                    contentType = value;
                }
            }
        }

        // 读取 body
        StringBuilder bodyBuilder = new StringBuilder();
        while (reader.ready()) {
            bodyBuilder.append((char) reader.read());
        }
        body = bodyBuilder.toString();

        // 根据 Content-Type 解析 body 参数
        if (body != null && !body.isEmpty() && contentType != null) {
            if (contentType.startsWith("application/x-www-form-urlencoded")) {
                parseQueryString(body, parameters);
            } else if (contentType.startsWith("multipart/form-data")) {
                // 重新构造 InputStream 用于 multipart 解析（因为已经读取完，需要从 body 重建）
                InputStream multipartStream = new java.io.ByteArrayInputStream(body.getBytes(StandardCharsets.ISO_8859_1));
                multipartResolver = new MultipartResolver(contentType, multipartStream);
                // 将文本字段合并到 parameters
                parameters.putAll(multipartResolver.getTextFields());
            }
            // 其他类型保留原始 body 供 @RequestBody 使用
        }
    }

    private void parseQueryString(String query, Map<String, String> target) {
        if (query == null) return;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int eq = pair.indexOf("=");
            if (eq > 0) {
                try {
                    String key = URLDecoder.decode(pair.substring(0, eq), "UTF-8");
                    String val = URLDecoder.decode(pair.substring(eq + 1), "UTF-8");
                    target.put(key, val);
                } catch (Exception ignored) {}
            } else if (!pair.isEmpty()) {
                try {
                    target.put(URLDecoder.decode(pair, "UTF-8"), "");
                } catch (Exception ignored) {}
            }
        }
    }

    public String getUri() { return uri; }
    public String getMethod() { return method; }
    public String getContextPath() { return contextPath; }
    public String getQueryString() { return queryString; }
    public String getBody() { return body; }
    public Map<String, String> getParameters() { return parameters; }
    public String getHeader(String name) { return headers.get(name.toLowerCase(Locale.ENGLISH)); }
    public String getContentType() { return contentType; }
    public MultipartResolver getMultipartResolver() { return multipartResolver; }
}