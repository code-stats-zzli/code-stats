package com.omni.framework.tomcat.connector;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.mvc.MultipartResolver;
import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.tomcat.session.HttpSession;
import com.omni.framework.tomcat.session.SessionManager;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Request implements HttpServletRequest {
    private static final Logger log = LoggerFactory.getLogger(Request.class);

    private final BufferedInputStream input;
    private String uri;
    private String method;
    private String contextPath = "";
    private String servletPath = "";
    private String pathInfo = null;
    private String queryString;
    private String body;
    private Map<String, String> parameters = new HashMap<>();
    private Map<String, List<String>> headers = new HashMap<>();
    private String contentType;
    private MultipartResolver multipartResolver;
    private String characterEncoding = "UTF-8";
    private int contentLength = -1;
    private long contentLengthLong = -1;
    private String protocol = "HTTP/1.1";
    private String scheme = "http";
    private String serverName;
    private int serverPort;
    private String remoteAddr;
    private String remoteHost;
    private int remotePort;
    private String localAddr;
    private int localPort;
    private boolean secure = false;
    private Map<String, Object> attributes = new HashMap<>();
    private List<Cookie> cookies;
    private HttpSession session;
    private boolean isMultipart = false;

    public Request(InputStream input) {
        this.input = new BufferedInputStream(input);
    }

    public void setSocket(java.net.Socket socket) {
        if (socket != null) {
            this.remoteAddr = socket.getInetAddress().getHostAddress();
            this.remoteHost = socket.getInetAddress().getHostName();
            this.remotePort = socket.getPort();
            this.localAddr = socket.getLocalAddress().getHostAddress();
            this.localPort = socket.getLocalPort();
        }
    }

    public void setServerInfo(String serverName, int serverPort, String scheme) {
        this.serverName = serverName;
        this.serverPort = serverPort;
        this.scheme = scheme;
        this.secure = "https".equalsIgnoreCase(scheme);
    }

    public void parse() throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
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
                String remaining = fullUri.substring(idx);
                servletPath = remaining;
                pathInfo = null;
                uri = remaining;
            } else {
                contextPath = "";
                servletPath = fullUri;
                pathInfo = null;
                uri = fullUri;
            }
            if (uri.isEmpty()) uri = "/";
            if (parts.length == 3) protocol = parts[2];
        }

        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                String name = line.substring(0, colon).trim().toLowerCase(Locale.ENGLISH);
                String value = line.substring(colon + 1).trim();
                headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
                if ("content-type".equals(name)) {
                    contentType = value;
                    if (contentType != null && contentType.startsWith("multipart/form-data")) {
                        isMultipart = true;
                    }
                } else if ("content-length".equals(name)) {
                    contentLength = Integer.parseInt(value);
                    contentLengthLong = Long.parseLong(value);
                }
            }
        }

        if (!isMultipart) {
            StringBuilder bodyBuilder = new StringBuilder();
            while (reader.ready()) {
                bodyBuilder.append((char) reader.read());
            }
            body = bodyBuilder.toString();
            if (body != null && !body.isEmpty() && contentType != null) {
                if (contentType.startsWith("application/x-www-form-urlencoded")) {
                    parseQueryString(body, parameters);
                }
            }
        } else {
            body = null;
        }

        parseCookies();

        // ====== 请求解析完成日志 ======
        log.debug("请求解析完成: method={}, uri={}, contextPath={}, queryString={}, " +
                        "headersCount={}, contentType={}, contentLength={}, bodyLength={}",
                method, uri, contextPath, queryString,
                headers.size(), contentType, contentLength,
                body != null ? body.length() : 0);
    }

    private void parseQueryString(String query, Map<String, String> target) {
        if (query == null) return;
        for (String pair : query.split("&")) {
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

    private void parseCookies() {
        List<String> cookieHeaders = headers.get("cookie");
        if (cookieHeaders != null && !cookieHeaders.isEmpty()) {
            cookies = new ArrayList<>();
            for (String cookieHeader : cookieHeaders) {
                String[] pairs = cookieHeader.split(";");
                for (String pair : pairs) {
                    String[] kv = pair.trim().split("=", 2);
                    if (kv.length == 2) {
                        cookies.add(new Cookie(kv[0], kv[1]));
                    }
                }
            }
        }
    }

    // ==================== HttpServletRequest 实现 ====================
    @Override public String getMethod() { return method; }
    @Override public String getUri() { return uri; }
    @Override public String getContextPath() { return contextPath; }
    @Override public String getQueryString() { return queryString; }
    @Override public String getBody() { return body; }
    @Override public Map<String, String> getParameters() { return parameters; }
    @Override public String getParameter(String name) { return parameters.get(name); }
    @Override public String getHeader(String name) {
        List<String> values = headers.get(name.toLowerCase(Locale.ENGLISH));
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }
    @Override public String getContentType() { return contentType; }
    @Override public InputStream getInputStream() { return input; }

    @Override
    public MultipartResolver getMultipartResolver() {
        if (multipartResolver == null && isMultipart && contentType != null) {
            try {
                multipartResolver = new MultipartResolver(contentType, input);
            } catch (Exception e) {
                log.error("解析 multipart 请求失败, contentType={}, uri={}", contentType, uri, e);
                multipartResolver = null;
            }
        }
        return multipartResolver;
    }

    @Override public String getRequestURI() { return uri; }
    @Override public StringBuffer getRequestURL() {
        StringBuffer url = new StringBuffer();
        url.append(scheme).append("://").append(serverName);
        if ((scheme.equals("http") && serverPort != 80) || (scheme.equals("https") && serverPort != 443)) {
            url.append(":").append(serverPort);
        }
        url.append(uri);
        return url;
    }
    @Override public String getServletPath() { return servletPath; }
    @Override public String getPathInfo() { return pathInfo; }
    @Override public String getRemoteAddr() { return remoteAddr; }
    @Override public String getRemoteHost() { return remoteHost; }
    @Override public int getRemotePort() { return remotePort; }
    @Override public String getLocalAddr() { return localAddr; }
    @Override public int getLocalPort() { return localPort; }
    @Override public String getServerName() { return serverName; }
    @Override public int getServerPort() { return serverPort; }
    @Override public String getScheme() { return scheme; }
    @Override public String getProtocol() { return protocol; }
    @Override public boolean isSecure() { return secure; }
    @Override public Enumeration<String> getHeaderNames() { return Collections.enumeration(headers.keySet()); }
    @Override public Enumeration<String> getHeaders(String name) {
        List<String> values = headers.get(name.toLowerCase(Locale.ENGLISH));
        return values != null ? Collections.enumeration(values) : Collections.emptyEnumeration();
    }
    @Override public Enumeration<String> getParameterNames() { return Collections.enumeration(parameters.keySet()); }
    @Override public String[] getParameterValues(String name) { return new String[]{parameters.get(name)}; }
    @Override public Map<String, String[]> getParameterMap() {
        Map<String, String[]> map = new HashMap<>();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            map.put(entry.getKey(), new String[]{entry.getValue()});
        }
        return map;
    }
    @Override public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream(), characterEncoding));
    }
    @Override public String getCharacterEncoding() { return characterEncoding; }
    @Override public void setCharacterEncoding(String env) { this.characterEncoding = env; }
    @Override public int getContentLength() { return contentLength; }
    @Override public long getContentLengthLong() { return contentLengthLong; }
    @Override public HttpSession getSession() { return getSession(true); }
    @Override public HttpSession getSession(boolean create) {
        if (session != null) return session;
        String sessionId = null;
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("JSESSIONID".equals(cookie.getName())) {
                    sessionId = cookie.getValue();
                    break;
                }
            }
        }
        if (sessionId != null) session = SessionManager.getSession(sessionId);
        if (session == null && create) session = SessionManager.createSession();
        return session;
    }
    @Override public Cookie[] getCookies() { return cookies == null ? new Cookie[0] : cookies.toArray(new Cookie[0]); }
    @Override public Object getAttribute(String name) { return attributes.get(name); }
    @Override public void setAttribute(String name, Object value) { attributes.put(name, value); }
    @Override public void removeAttribute(String name) { attributes.remove(name); }
    @Override public Enumeration<String> getAttributeNames() { return Collections.enumeration(attributes.keySet()); }

    public static class Cookie {
        private final String name;
        private final String value;
        public Cookie(String name, String value) { this.name = name; this.value = value; }
        public String getName() { return name; }
        public String getValue() { return value; }
    }
}