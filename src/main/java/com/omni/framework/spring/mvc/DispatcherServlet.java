package com.omni.framework.spring.mvc;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.spring.beans.factory.ConfigurableListableBeanFactory;
import com.omni.framework.spring.context.AbstractApplicationContext;
import com.omni.framework.spring.context.SpringContextHolder;
import com.omni.framework.tomcat.servlet.HttpServlet;
import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.tomcat.servlet.HttpServletResponse;
import com.omni.framework.web.TraceContextHolder;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.SocketException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DispatcherServlet extends HttpServlet {
    private static final Logger log = LoggerFactory.getLogger(DispatcherServlet.class);
    private final List<HandlerMapping> handlerMappings = new ArrayList<>();

    /**
     * 敏感参数名（不打印明文值）
     */
    private static final Set<String> SENSITIVE_PARAM_NAMES = new HashSet<>(Arrays.asList(
            "password", "passwd", "pwd", "secret", "token",
            "accessToken", "apiKey", "apikey", "key", "credential"
    ));

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
        long startTime = System.currentTimeMillis();
        String method = req.getMethod();
        String uri = req.getUri();
        String query = req.getQueryString();

        // ====== 1. 打印完整请求信息（方法、URI、Query、Headers、Body、参数） ======
        logRequest(req, method, uri, query);

        // ====== 埋点：记录请求开始 ======
        long threadId = Thread.currentThread().getId();
        TraceContextHolder.putContext(threadId, uri);
        // ===============================

        RequestContextHolder.setRequest(req);
        ResponseContextHolder.setResponse(res);
        MultipartResolver multipartResolver = null;
        Throwable requestError = null;

        try {
            for (HandlerMapping hm : handlerMappings) {
                Pattern pattern = Pattern.compile(hm.regex);
                Matcher matcher = pattern.matcher(uri);
                if (matcher.matches()) {
                    Map<String, String> pathVars = new HashMap<>();
                    for (int i = 0; i < hm.varNames.size(); i++) {
                        pathVars.put(hm.varNames.get(i), matcher.group(i + 1));
                    }

                    log.info("匹配处理器: {}.{}", hm.controller.getClass().getSimpleName(), hm.method.getName());
                    if (!pathVars.isEmpty()) {
                        log.info("🛣️ 路径参数: {}", pathVars);
                    }

                    Object[] args = ParamBinder.resolveParameters(hm.method, req, res, pathVars);
                    log.info(" 绑定参数: {}", formatArgs(hm.method, args));

                    Object result = hm.method.invoke(hm.controller, args);

                    if (hm.isResponseBody) {
                        String outStr;
                        if (result instanceof String) {
                            outStr = (String) result;
                        } else {
                            outStr = toJson(result);
                        }
                        log.info(" 响应体预览: {}", preview(outStr));
                        res.setContentType("application/json; charset=utf-8");
                        res.write(outStr);
                    } else {
                        if (result != null) {
                            log.info("📤 视图响应: {}", result);
                            res.write(result.toString());
                        }
                    }
                    res.send();
                    log.info(" 请求处理完成: {} {} 耗时: {}ms",
                            method, uri, System.currentTimeMillis() - startTime);
                    return;
                }
            }

            if (serveStaticFile(uri, res)) {
                log.info(" 静态文件响应: {} 耗时: {}ms", uri, System.currentTimeMillis() - startTime);
                return;
            }

            res.setStatus(404, "Not Found");
            res.write("<h1>404 - Not Found</h1>");
            res.send();
            log.warn("⚠️ 404 Not Found: {} {} 耗时: {}ms",
                    method, uri, System.currentTimeMillis() - startTime);

        } catch (Throwable e) {
            requestError = e;
            // 专门处理客户端过早断开连接
            if (e instanceof SocketException) {
                String msg = e.getMessage();
                if (msg != null && (msg.contains("软件中止") || msg.contains("Connection reset") || msg.contains("Broken pipe"))) {
                    log.debug("客户端已断开连接，请求详情: {} {}?{}", method, uri, query != null ? query : "");
                    return;
                }
            }
            log.error(" 处理请求失败: {} {} 耗时: {}ms", method, uri,
                    System.currentTimeMillis() - startTime, e);
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
            // ====== 埋点：记录请求结束 ======
            String errorMsg = requestError != null ? requestError.getMessage() : null;
            TraceContextHolder.updateContext(threadId, errorMsg, null);
            // ===============================

            if (multipartResolver != null) {
                multipartResolver.close();
            }
            RequestContextHolder.clear();
            ResponseContextHolder.clear();
        }
    }

    /**
     * 打印完整请求信息：方法、URI、Query、Headers、Body、参数、文件上传字段
     */
    private void logRequest(HttpServletRequest req, String method, String uri, String query) {
        log.info("==========  收到请求 ==========");
        log.info("请求方法: {}", method);
        log.info("请求URI: {}", uri);
        log.info("查询字符串: {}", query != null ? query : "(无)");


        // Query / Form 参数
        Map<String, String> params = req.getParameters();
        if (params != null && !params.isEmpty()) {
            Map<String, String> displayParams = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : params.entrySet()) {
                String key = e.getKey();
                if (isSensitive(key)) {
                    displayParams.put(key, "***");
                } else {
                    String value = e.getValue();
                    if (value != null && value.length() > 500) {
                        value = value.substring(0, 500) + "...(截断)";
                    }
                    displayParams.put(key, value);
                }
            }
            log.info("查询参数: {}", displayParams);
        }

        // Body
        String body = req.getBody();
        if (body != null && !body.isEmpty()) {
            String bodyPreview = body.length() > 2000
                    ? body.substring(0, 2000) + "...(截断，总长度" + body.length() + ")"
                    : body;
            log.info("请求Body: {}", bodyPreview);
        }

        // 文件上传字段
        String contentType = req.getContentType();
        if (contentType != null && contentType.startsWith("multipart/form-data")) {
            MultipartResolver resolver = req.getMultipartResolver();
            if (resolver != null) {
                try {
                    Set<String> textNames = resolver.getParameterNames();
                    Set<String> fileNames = resolver.getFileNames();
                    if (!textNames.isEmpty()) {
                        log.info(" 上传文本字段: {}", textNames);
                    }
                    if (!fileNames.isEmpty()) {
                        log.info(" 上传文件字段: {}", fileNames);
                        for (String name : fileNames) {
                            FileItem item = resolver.getFileItem(name);
                            if (item != null) {
                                log.info("   - {}: filename={}, size={} bytes",
                                        name, item.getFileName(), item.getSize());
                            }
                        }
                    }
                } catch (Exception ex) {
                    log.warn("打印 multipart 字段失败: {}", ex.getMessage());
                }
            }
        }
    }

    /**
     * 格式化方法参数，敏感字段脱敏
     */
    private String formatArgs(Method method, Object[] args) {
        if (args == null || args.length == 0) return "[]";
        Parameter[] parameters = method.getParameters();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            String name = parameters.length > i ? parameters[i].getName() : "arg" + i;
            Object val = args[i];

            if (val instanceof HttpServletRequest) {
                sb.append(name).append("=HttpServletRequest");
            } else if (val instanceof HttpServletResponse) {
                sb.append(name).append("=HttpServletResponse");
            } else if (val instanceof FileItem) {
                FileItem fi = (FileItem) val;
                sb.append(name).append("=FileItem{filename=")
                        .append(fi.getFileName()).append(", size=")
                        .append(fi.getSize()).append("}");
            } else if (isSensitive(name)) {
                sb.append(name).append("=***");
            } else if (val instanceof String) {
                String str = (String) val;
                if (str.length() > 300) str = str.substring(0, 300) + "...(截断)";
                sb.append(name).append("=\"").append(str).append("\"");
            } else {
                sb.append(name).append("=").append(val);
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private boolean isSensitive(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        for (String s : SENSITIVE_PARAM_NAMES) {
            if (lower.contains(s)) return true;
        }
        return false;
    }

    private String preview(String text) {
        if (text == null) return "(null)";
        return text.length() > 500 ? text.substring(0, 500) + "...(截断)" : text;
    }

    // ---------- 原有辅助方法（保持不变） ----------
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