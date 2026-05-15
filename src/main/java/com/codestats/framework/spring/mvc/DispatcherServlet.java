package com.codestats.framework.spring.mvc;

import com.codestats.framework.spring.annotation.Controller;
import com.codestats.framework.spring.annotation.RequestMapping;
import com.codestats.framework.spring.annotation.ResponseBody;
import com.codestats.framework.spring.config.ConfigLoader;
import com.codestats.framework.spring.ioc.SimpleApplicationContext;
import com.codestats.framework.tomcat.connector.Request;
import com.codestats.framework.tomcat.connector.Response;
import com.codestats.framework.tomcat.servlet.HttpServlet;
import com.codestats.framework.util.JsonUtil;
import com.codestats.framework.log.LoggerFactory;
import com.codestats.framework.log.core.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DispatcherServlet extends HttpServlet {
    private static final Logger log = LoggerFactory.getLogger(DispatcherServlet.class);
    private SimpleApplicationContext applicationContext;
    private final Map<String, Handler> handlerMappings = new HashMap<>();

    @Override
    public void init() throws Exception {
        log.info("DispatcherServlet 初始化开始");
        applicationContext = new SimpleApplicationContext();
        String packages = ConfigLoader.getProperty("scan.packages", "com.codestats.business");
        applicationContext.scanAndInit(packages);
        List<Object> controllers = applicationContext.getBeansWithAnnotation(Controller.class);
        for (Object controller : controllers) {
            Class<?> clazz = controller.getClass();
            String basePath = "";
            if (clazz.isAnnotationPresent(RequestMapping.class)) {
                basePath = clazz.getAnnotation(RequestMapping.class).value();
            }
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(RequestMapping.class)) {
                    String methodPath = method.getAnnotation(RequestMapping.class).value();
                    String fullPath = (basePath + methodPath).replaceAll("//", "/");
                    if (!fullPath.startsWith("/")) fullPath = "/" + fullPath;
                    boolean isResponseBody = method.isAnnotationPresent(ResponseBody.class);
                    handlerMappings.put(fullPath, new Handler(controller, method, isResponseBody));
                    log.debug("Mapped {}", fullPath);
                }
            }
        }
        log.info("DispatcherServlet 初始化完成，共映射 {} 个处理器", handlerMappings.size());
    }

    @Override
    protected void doGet(Request req, Response res) throws Exception {
        processRequest(req, res);
    }

    @Override
    protected void doPost(Request req, Response res) throws Exception {
        processRequest(req, res);
    }

    private void processRequest(Request req, Response res) throws Exception {
        RequestContextHolder.setRequest(req);
        ResponseContextHolder.setResponse(res);
        try {
            String uri = req.getUri();
            log.debug("Request URI: {}", uri);
            Handler handler = handlerMappings.get(uri);
            if (handler != null) {
                Object[] args = ParamBinder.resolveParameters(handler.method, req);
                Object result = handler.method.invoke(handler.controller, args);
                if (handler.isResponseBody) {
                    String outStr = result instanceof String ? (String) result : JsonUtil.toJson(result);
                    res.setContentType(outStr.startsWith("{") ? "application/json" : "text/plain");
                    res.write(outStr);
                } else {
                    if (result != null) res.write(result.toString());
                }
                res.send();
                return;
            }
            if (serveStaticFile(uri, res)) return;
            res.setStatus(404, "Not Found");
            res.write("<h1>404 - Not Found</h1><p>URI: " + uri + "</p>");
            res.send();
        } finally {
            RequestContextHolder.clear();
            ResponseContextHolder.clear();
        }
    }

    private boolean serveStaticFile(String uri, Response res) {
        if (uri == null || uri.contains("..") || uri.contains("//")) return false;
        String cleanUri = uri.startsWith("/") ? uri.substring(1) : uri;
        int firstSlash = cleanUri.indexOf('/');
        if (firstSlash <= 0) {
            log.debug("静态文件无斜杠，跳过: {}", uri);
            return false;
        }
        String webappRoot = cleanUri.substring(0, firstSlash);
        String subPath = cleanUri.substring(firstSlash + 1);
        Path base = Paths.get("webapps", webappRoot);
        Path filePath = base.resolve(subPath).normalize();
        if (!filePath.startsWith(base)) {
            log.warn("静态文件路径越界: {}", filePath);
            return false;
        }
        File file = filePath.toFile();
        if (!file.exists() || file.isDirectory()) {
            log.debug("静态文件不存在: {}", filePath);
            return false;
        }
        String contentType = getContentType(uri);
        res.setContentType(contentType);
        res.setStatus(200, "OK");
        try (FileInputStream fis = new FileInputStream(file)) {
            res.sendStatic(fis, contentType, file.length());
            log.info("提供静态文件: {}", filePath);
            return true;
        } catch (IOException e) {
            log.error("提供静态文件失败: {}", filePath, e);
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

    private static class Handler {
        Object controller;
        Method method;
        boolean isResponseBody;
        Handler(Object controller, Method method, boolean isResponseBody) {
            this.controller = controller;
            this.method = method;
            this.isResponseBody = isResponseBody;
        }
    }
}