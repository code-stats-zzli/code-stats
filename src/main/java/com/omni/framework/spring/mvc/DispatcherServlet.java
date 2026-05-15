package com.omni.framework.spring.mvc;

import com.omni.framework.spring.annotation.*;
import com.omni.framework.spring.config.ConfigLoader;
import com.omni.framework.spring.ioc.SimpleApplicationContext;
import com.omni.framework.tomcat.connector.Request;
import com.omni.framework.tomcat.connector.Response;
import com.omni.framework.tomcat.servlet.HttpServlet;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DispatcherServlet extends HttpServlet {
    private static final Logger log = LoggerFactory.getLogger(DispatcherServlet.class);
    private SimpleApplicationContext applicationContext;
    private final List<HandlerMapping> handlerMappings = new ArrayList<>();

    @Override
    public void init() throws Exception {
        applicationContext = new SimpleApplicationContext();
        String packages = ConfigLoader.getProperty("scan.packages", "com.omni.business");
        applicationContext.scanAndInit(packages);
        List<Object> controllers = applicationContext.getBeansWithAnnotation(Controller.class);
        for (Object controller : controllers) {
            Class<?> clazz = controller.getClass();
            String basePath = "";
            if (clazz.isAnnotationPresent(RequestMapping.class)) {
                basePath = clazz.getAnnotation(RequestMapping.class).value();
            }
            boolean isRest = clazz.isAnnotationPresent(RestController.class);
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(RequestMapping.class)) {
                    String methodPath = method.getAnnotation(RequestMapping.class).value();
                    String fullPath = (basePath + methodPath).replaceAll("//", "/");
                    if (!fullPath.startsWith("/")) fullPath = "/" + fullPath;
                    boolean isResponseBody = isRest || method.isAnnotationPresent(ResponseBody.class);
                    // 将路径模式转换为正则，提取路径变量
                    String regex = fullPath.replaceAll("\\{([^}]+)\\}", "([^/]+)");
                    List<String> varNames = new ArrayList<>();
                    Matcher m = Pattern.compile("\\{([^}]+)\\}").matcher(fullPath);
                    while (m.find()) varNames.add(m.group(1));
                    handlerMappings.add(new HandlerMapping(regex, varNames, controller, method, isResponseBody));
                    log.debug("Mapped {} -> {}", fullPath, method);
                }
            }
        }
    }

    @Override
    protected void doGet(Request req, Response res) throws Exception { processRequest(req, res); }
    @Override
    protected void doPost(Request req, Response res) throws Exception { processRequest(req, res); }

    private void processRequest(Request req, Response res) throws Exception {
        RequestContextHolder.setRequest(req);
        ResponseContextHolder.setResponse(res);
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
                        res.setResponseBody(result);
                        if (res.getContentType() == null) res.setContentType("application/json");
                    } else {
                        if (result != null) res.write(result.toString());
                    }
                    res.send();
                    return;
                }
            }
            // 静态文件服务
            if (serveStaticFile(uri, res)) return;
            res.setStatus(404, "Not Found");
            res.write("<h1>404 - Not Found</h1>");
            res.send();
        } finally {
            RequestContextHolder.clear();
            ResponseContextHolder.clear();
        }
    }

    private boolean serveStaticFile(String uri, Response res) {
        // 原有静态文件逻辑保持不变
        if (uri == null || uri.contains("..") || uri.contains("//")) return false;
        String cleanUri = uri.startsWith("/") ? uri.substring(1) : uri;
        int firstSlash = cleanUri.indexOf('/');
        if (firstSlash <= 0) return false;
        String webappRoot = cleanUri.substring(0, firstSlash);
        String subPath = cleanUri.substring(firstSlash + 1);
        java.nio.file.Path base = java.nio.file.Paths.get("webapps", webappRoot);
        java.nio.file.Path filePath = base.resolve(subPath).normalize();
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