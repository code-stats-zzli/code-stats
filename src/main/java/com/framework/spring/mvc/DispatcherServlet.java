package com.framework.spring.mvc;

import com.framework.spring.annotation.Controller;
import com.framework.spring.annotation.RequestMapping;
import com.framework.spring.annotation.ResponseBody;
import com.framework.spring.config.ConfigLoader;
import com.framework.spring.ioc.SimpleApplicationContext;
import com.framework.tomcat.connector.Request;
import com.framework.tomcat.connector.Response;
import com.framework.tomcat.servlet.HttpServlet;
import com.framework.util.JsonUtil;

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

    private SimpleApplicationContext applicationContext;
    private final Map<String, Handler> handlerMappings = new HashMap<>();

    @Override
    public void init() throws Exception {
        System.out.println(">>> DispatcherServlet init");
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
                    System.out.println("Mapped " + fullPath);
                }
            }
        }
        System.out.println(">>> DispatcherServlet ready, mapped " + handlerMappings.size() + " handlers");
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
            System.out.println(">>> Request URI: " + uri);
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
            // 静态文件服务（支持 webapps 下任意子目录）
            if (serveStaticFile(uri, res)) return;
            // 404
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
        // uri 示例: /face/index.html  (已去除 context path)
        String cleanUri = uri.startsWith("/") ? uri.substring(1) : uri;
        int firstSlash = cleanUri.indexOf('/');
        if (firstSlash <= 0) {
            System.out.println("Static file: no slash, skip");
            return false;
        }
        String webappRoot = cleanUri.substring(0, firstSlash);      // "face"
        String subPath = cleanUri.substring(firstSlash + 1);        // "index.html"
        Path base = Paths.get("webapps", webappRoot);
        Path filePath = base.resolve(subPath).normalize();
        // 安全检查
        if (!filePath.startsWith(base)) {
            System.out.println("Static file: path traversal detected");
            return false;
        }
        File file = filePath.toFile();
        if (!file.exists() || file.isDirectory()) {
            System.out.println("Static file not found: " + filePath);
            return false;
        }
        String contentType = getContentType(uri);
        res.setContentType(contentType);
        res.setStatus(200, "OK");
        try (FileInputStream fis = new FileInputStream(file)) {
            res.sendStatic(fis, contentType, file.length());
            System.out.println("Served static file: " + filePath);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
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