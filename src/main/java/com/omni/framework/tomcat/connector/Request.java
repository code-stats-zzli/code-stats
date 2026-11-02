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

    /** 请求头最大长度，避免恶意超长头打爆内存 */
    private static final int MAX_HEADER_SIZE = 64 * 1024;

    private final BufferedInputStream input;
    private String uri;
    private String method;
    private String contextPath = "";
    private String servletPath = "";
    private String pathInfo = null;
    private String queryString;
    private String body;
    /** ★ 新增：multipart 请求的原始 body 字节，避免 input 被预读后丢失 */
    private byte[] rawBody;

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
        // 容量 8KB，避免默认 buffer 太小
        this.input = (input instanceof BufferedInputStream)
                ? (BufferedInputStream) input
                : new BufferedInputStream(input, 8192);
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

    /**
     * ★ 核心修复：逐字节读取请求头，直到 \r\n\r\n，
     *   绝不使用 BufferedReader 预读（否则会吞掉 multipart body）。
     */
    public void parse() throws Exception {
        // ==================== 1) 读请求头（直到 \r\n\r\n） ====================
        ByteArrayOutputStream headerBuf = new ByteArrayOutputStream(1024);
        int state = 0;               // 0:空 1:已\r 2:已\r\n 3:已\r\n\r
        int totalHeader = 0;
        while (true) {
            int b = input.read();
            if (b < 0) break;                       // 连接直接关闭
            headerBuf.write(b);
            totalHeader++;
            if (totalHeader > MAX_HEADER_SIZE) {
                throw new IOException("Request header too large (> " + MAX_HEADER_SIZE + "B)");
            }
            if (state == 0 && b == '\r') state = 1;
            else if (state == 1 && b == '\n') state = 2;
            else if (state == 2 && b == '\r') state = 3;
            else if (state == 3 && b == '\n') break; // 遇到 \r\n\r\n
            else state = 0;
        }
        if (headerBuf.size() == 0) return;

        String headerText = headerBuf.toString(StandardCharsets.ISO_8859_1.name());
        String[] headerLines = headerText.split("\r\n", -1);

        // ==================== 2) 解析请求行 ====================
        if (headerLines.length >= 1 && !headerLines[0].isEmpty()) {
            String requestLine = headerLines[0];
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
                if (uri == null || uri.isEmpty()) uri = "/";
                if (parts.length == 3) protocol = parts[2];
            }
        }

        // ==================== 3) 解析头部 ====================
        for (int i = 1; i < headerLines.length; i++) {
            String line = headerLines[i];
            if (line.isEmpty()) break;
            int colon = line.indexOf(':');
            if (colon > 0) {
                String name = line.substring(0, colon).trim().toLowerCase(Locale.ENGLISH);
                String value = line.substring(colon + 1).trim();
                headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
                if ("content-type".equals(name)) {
                    contentType = value;
                    if (contentType != null
                            && contentType.toLowerCase(Locale.ENGLISH).startsWith("multipart/form-data")) {
                        isMultipart = true;
                    }
                } else if ("content-length".equals(name)) {
                    try {
                        contentLength = Integer.parseInt(value);
                        contentLengthLong = Long.parseLong(value);
                    } catch (NumberFormatException ignored) { }
                }
            }
        }

        // ==================== 4) 读取 body（按 content-length 精确读，不多不少） ====================
        if (isMultipart) {
            // ★ multipart：原始字节保存，交给 MultipartResolver 解析
            rawBody = (contentLength > 0) ? input.readNBytes(contentLength) : new byte[0];
            body = null;
        } else {
            if (contentLength > 0) {
                byte[] buf = input.readNBytes(contentLength);
                body = new String(buf, StandardCharsets.UTF_8);
            } else {
                body = "";
            }
            if (body != null && !body.isEmpty() && contentType != null
                    && contentType.toLowerCase(Locale.ENGLISH)
                    .startsWith("application/x-www-form-urlencoded")) {
                parseQueryString(body, parameters);
            }
        }

        parseCookies();

        log.debug("请求解析完成: method={}, uri={}, contextPath={}, queryString={}, " +
                        "headersCount={}, contentType={}, contentLength={}, isMultipart={}, bodyLength={}",
                method, uri, contextPath, queryString,
                headers.size(), contentType, contentLength, isMultipart,
                body != null ? body.length() : (rawBody != null ? rawBody.length : 0));
    }

    private void parseQueryString(String query, Map<String, String> target) {
        if (query == null) return;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                try {
                    String key = URLDecoder.decode(pair.substring(0, eq), "UTF-8");
                    String val = URLDecoder.decode(pair.substring(eq + 1), "UTF-8");
                    target.put(key, val);
                } catch (Exception ignored) { }
            } else if (!pair.isEmpty()) {
                try {
                    target.put(URLDecoder.decode(pair, "UTF-8"), "");
                } catch (Exception ignored) { }
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

    /**
     * ★ 关键修复：用 rawBody 构造 MultipartResolver，
     *   不依赖 input 流（避免被 BufferedReader 提前消费）
     */
    @Override
    public MultipartResolver getMultipartResolver() {
        if (multipartResolver == null && isMultipart && contentType != null && rawBody != null) {
            try {
                multipartResolver = new MultipartResolver(
                        contentType, new ByteArrayInputStream(rawBody));
                log.info("Multipart 解析完成，rawBody={}B, fileFields={}, textFields={}",
                        rawBody.length,
                        multipartResolver.getFileNames(),
                        multipartResolver.getParameterNames());
            } catch (Exception e) {
                log.error("解析 multipart 请求失败, contentType={}, uri={}",
                        contentType, uri, e);
                multipartResolver = null;
            }
        }
        return multipartResolver;
    }

    @Override public String getRequestURI() { return uri; }
    @Override public StringBuffer getRequestURL() {
        StringBuffer url = new StringBuffer();
        url.append(scheme).append("://").append(serverName);
        if ((scheme.equals("http") && serverPort != 80)
                || (scheme.equals("https") && serverPort != 443)) {
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