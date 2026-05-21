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
        // ★ 包装请求以支持缓存和重复读取 Body
        //req = new CachedBodyHttpServletRequest(req);
        // ★ 打印详细请求信息
        logRequestDetails(req);

        RequestContextHolder.setRequest(req);
        ResponseContextHolder.setResponse(res);
        MultipartResolver multipartResolver = null;
        try {
            String uri = req.getUri();
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
            log.error("processRequest error:", e);
        } finally {
            if (multipartResolver != null) {
                multipartResolver.cleanup();
            }
            RequestContextHolder.clear();
            ResponseContextHolder.clear();
        }
    }

    // ==================== 新增：请求日志打印方法 ====================
    private void logRequestDetails(HttpServletRequest req) {
        log.info(req.getRequestURI());
        if (!log.isDebugEnabled()) {
            return; // 只在 DEBUG 级别打印，避免生产环境过多日志
        }
        StringBuilder sb = new StringBuilder("\n========== HTTP Request ==========\n");

        // 1. 请求行
        sb.append("Method : ").append(req.getMethod()).append("\n");
        sb.append("URI    : ").append(req.getRequestURI()).append("\n");
        String query = req.getQueryString();
        sb.append("Query  : ").append(query != null ? query : "").append("\n");

        // 2. 请求头
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

        // 3. 请求参数
        sb.append("Parameters:\n");
        Map<String, String[]> paramMap = req.getParameterMap();
        if (paramMap != null && !paramMap.isEmpty()) {
            paramMap.forEach((key, values) ->
                    sb.append("  ").append(key).append(": ").append(Arrays.toString(values)).append("\n"));
        } else {
            sb.append("  (none)\n");
        }

        // 4. 请求体
        String body = safeReadBody(req);
        if (body != null && !body.isEmpty()) {
            sb.append("Body:\n").append(body).append("\n");
        }

        sb.append("==================================\n");
        log.debug(sb.toString());
    }

    private String safeReadBody(HttpServletRequest req) {
        // 只对 POST/PUT/PATCH 允许 Body 的方法且非 multipart 时读取
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