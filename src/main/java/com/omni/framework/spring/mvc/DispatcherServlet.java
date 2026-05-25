package com.omni.framework.spring.mvc;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.spring.beans.factory.ConfigurableListableBeanFactory;
import com.omni.framework.spring.context.AbstractApplicationContext;
import com.omni.framework.spring.context.SpringContextHolder;
import com.omni.framework.tomcat.servlet.CachedBodyHttpServletRequest;
import com.omni.framework.tomcat.servlet.HttpServlet;
import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.tomcat.servlet.HttpServletResponse;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.net.SocketException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DispatcherServlet extends HttpServlet {
    private static final Logger log = LoggerFactory.getLogger(DispatcherServlet.class);
    private final List<HandlerMapping> handlerMappings = new ArrayList<>();

    @Override
    public void init() throws Exception {
        log.info("DispatcherServlet 初始化开始");

        AbstractApplicationContext ctx = SpringContextHolder.getApplicationContext();
        ConfigurableListableBeanFactory beanFactory = ctx.getBeanFactory();
        Map<String, Object> controllers = beanFactory.getBeansOfType(Object.class);

        for (Object controller : controllers.values()) {
            Class<?> clazz = controller.getClass();
            String basePath = "";
            if (clazz.isAnnotationPresent(RequestMapping.class)) {
                basePath = clazz.getAnnotation(RequestMapping.class).value();
                if (!basePath.startsWith("/")) basePath = "/" + basePath;
            }

            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(RequestMapping.class)) {
                    String methodPath = method.getAnnotation(RequestMapping.class).value();
                    String fullPath = (basePath + methodPath).replaceAll("//", "/");
                    if (!fullPath.startsWith("/")) fullPath = "/" + fullPath;

                    List<String> varNames = new ArrayList<>();
                    String regex = pathToRegex(fullPath, varNames);
                    boolean isResponseBody = method.isAnnotationPresent(ResponseBody.class);

                    handlerMappings.add(new HandlerMapping(regex, varNames, controller, method, isResponseBody));
                    log.debug("Mapped {} -> {}", fullPath, method);
                }
            }
        }
        log.info("DispatcherServlet 初始化完成，共映射 {} 个处理器", handlerMappings.size());
    }

    private String pathToRegex(String path, List<String> varNames) {
        StringBuilder regex = new StringBuilder("^");
        String[] segments = path.split("/");
        for (String seg : segments) {
            if (seg.isEmpty()) continue;
            if (seg.startsWith("{") && seg.endsWith("}")) {
                varNames.add(seg.substring(1, seg.length() - 1));
                regex.append("/([^/]+)");
            } else {
                regex.append("/").append(seg);
            }
        }
        regex.append("/?$");
        return regex.toString();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws Exception {
        processRequest(req, res);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws Exception {
        processRequest(req, res);
    }

    private void processRequest(HttpServletRequest req, HttpServletResponse res) throws Exception {
        String method = req.getMethod();
        String uri = req.getUri();
        String query = req.getQueryString();
        log.info("收到请求: {} {} {}", method, uri, query != null ? "?" + query : "");

        RequestContextHolder.setRequest(req);
        ResponseContextHolder.setResponse(res);
        MultipartResolver multipartResolver = null;
        try {
            for (HandlerMapping hm : handlerMappings) {
                Pattern pattern = Pattern.compile(hm.regex);
                Matcher matcher = pattern.matcher(uri);
                if (matcher.matches()) {
                    Map<String, String> pathVars = new HashMap<>();
                    for (int i = 0; i < hm.varNames.size(); i++) {
                        pathVars.put(hm.varNames.get(i), matcher.group(i + 1));
                    }
                    Object[] args = ParamBinder.resolveParameters(hm.method, req, res, pathVars);
                    Object result = hm.method.invoke(hm.controller, args);
                    if (hm.isResponseBody) {
                        String outStr;
                        if (result instanceof String) {
                            outStr = (String) result;
                        } else {
                            outStr = toJson(result);
                        }
                        res.setContentType("application/json; charset=utf-8");
                        res.write(outStr);
                    } else {
                        if (result != null) res.write(result.toString());
                    }
                    res.send();
                    return;
                }
            }
            if (serveStaticFile(uri, res)) return;
            res.setStatus(404, "Not Found");
            res.write("<h1>404 - Not Found</h1>");
            res.send();
        } catch (Throwable e) {
            // 专门处理客户端过早断开连接
            if (e instanceof SocketException) {
                String msg = e.getMessage();
                if (msg != null && (msg.contains("软件中止") || msg.contains("Connection reset") || msg.contains("Broken pipe"))) {
                    log.debug("客户端已断开连接，请求详情: {} {}?{}", method, uri, query != null ? query : "");
                    return; // 静默处理，不再向上抛出
                }
            }
            log.error("处理请求失败: {} {}", method, uri, e);
            // 如果响应尚未提交，尝试返回错误页面
            if (!res.isCommitted()) {
                try {
                    res.setStatus(500, "Internal Server Error");
                    res.write("<h1>500 Internal Server Error</h1><pre>" + e.toString() + "</pre>");
                    res.send();
                } catch (Exception ex) {
                    log.error("发送错误响应失败", ex);
                }
            }
        } finally {
            if (multipartResolver != null) {
                multipartResolver.close();
            }
            RequestContextHolder.clear();
            ResponseContextHolder.clear();
        }
    }

    private void logRequestDetails(HttpServletRequest req) {
        log.info(req.getRequestURI());
        if (!log.isDebugEnabled()) {
            return;
        }
        StringBuilder sb = new StringBuilder("\n========== HTTP Request ==========\n");
        sb.append("Method : ").append(req.getMethod()).append("\n");
        sb.append("URI    : ").append(req.getRequestURI()).append("\n");
        String query = req.getQueryString();
        sb.append("Query  : ").append(query != null ? query : "").append("\n");
        sb.append("Headers:\n");
        Enumeration<String> headerNames = req.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                Enumeration<String> values = req.getHeaders(name);
                while (values.hasMoreElements()) {
                    sb.append("  ").append(name).append(": ").append(values.nextElement()).append("\n");
                }
            }
        }
        sb.append("Parameters:\n");
        Map<String, String[]> paramMap = req.getParameterMap();
        if (paramMap != null && !paramMap.isEmpty()) {
            paramMap.forEach((key, values) ->
                    sb.append("  ").append(key).append(": ").append(Arrays.toString(values)).append("\n"));
        } else {
            sb.append("  (none)\n");
        }
        String body = safeReadBody(req);
        if (body != null && !body.isEmpty()) {
            sb.append("Body:\n").append(body).append("\n");
        }
        sb.append("==================================\n");
        log.debug(sb.toString());
    }

    private String safeReadBody(HttpServletRequest req) {
        String method = req.getMethod();
        if (!"POST".equalsIgnoreCase(method) && !"PUT".equalsIgnoreCase(method) && !"PATCH".equalsIgnoreCase(method)) {
            return null;
        }
        String contentType = req.getContentType();
        if (contentType != null && contentType.startsWith("multipart/form-data")) {
            return "[multipart content, not logged]";
        }
        try {
            BufferedReader reader = req.getReader();
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("Failed to read request body for logging", e);
            return "[unreadable]";
        }
    }

    private String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) return "\"" + escapeJson((String) obj) + "\"";
        if (obj instanceof Number || obj instanceof Boolean) return obj.toString();
        if (obj instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            Map<?, ?> map = (Map<?, ?>) obj;
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(escapeJson(entry.getKey().toString())).append("\":")
                        .append(toJson(entry.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof Iterable) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : (Iterable<?>) obj) {
                if (!first) sb.append(",");
                sb.append(toJson(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }
        return "\"" + escapeJson(obj.toString()) + "\"";
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private boolean serveStaticFile(String uri, HttpServletResponse res) {
        if (uri == null || uri.contains("..") || uri.contains("//")) return false;
        String cleanUri = uri.startsWith("/") ? uri.substring(1) : uri;
        int firstSlash = cleanUri.indexOf('/');
        if (firstSlash <= 0) return false;
        String webappRoot = cleanUri.substring(0, firstSlash);
        String subPath = cleanUri.substring(firstSlash + 1);
        Path base = Paths.get("webapps", webappRoot);
        Path filePath = base.resolve(subPath).normalize();
        if (!filePath.startsWith(base)) return false;
        File file = filePath.toFile();
        if (!file.exists() || file.isDirectory()) return false;
        String contentType = getContentType(uri);
        try (FileInputStream fis = new FileInputStream(file)) {
            res.sendStatic(fis, contentType, file.length());
            return true;
        } catch (Exception e) {
            log.error("Static file error", e);
            return false;
        }
    }

    private String getContentType(String uri) {
        if (uri.endsWith(".html")) return "text/html; charset=utf-8";
        if (uri.endsWith(".css")) return "text/css; charset=utf-8";
        if (uri.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (uri.endsWith(".json")) return "application/json; charset=utf-8";
        if (uri.endsWith(".png")) return "image/png";
        if (uri.endsWith(".jpg") || uri.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    private static class HandlerMapping {
        final String regex;
        final List<String> varNames;
        final Object controller;
        final Method method;
        final boolean isResponseBody;

        HandlerMapping(String regex, List<String> varNames, Object controller, Method method, boolean isResponseBody) {
            this.regex = regex;
            this.varNames = varNames;
            this.controller = controller;
            this.method = method;
            this.isResponseBody = isResponseBody;
        }
    }
}