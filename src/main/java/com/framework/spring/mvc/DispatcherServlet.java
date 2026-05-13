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
    private static final String STATIC_BASE = "webapps/demo";

    @Override
    public void init() throws Exception {
        System.out.println(">>> DispatcherServlet init: scanning and creating IoC container...");
        applicationContext = new SimpleApplicationContext();
        String packages = ConfigLoader.getProperty("scan.packages",
                "com.demo.*.business");
        applicationContext.scanAndInit(packages);
        List<Object> controllers = applicationContext.getBeansWithAnnotation(Controller.class);
        System.out.println(">>> Controllers found: " + controllers.size());

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
                    System.out.println("Mapped " + fullPath + " -> " + clazz.getSimpleName() + "." + method.getName());
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
                try {
                    // 解析方法参数
                    Object[] args = ParamBinder.resolveParameters(handler.method, req);
                    // 执行方法
                    Object result = handler.method.invoke(handler.controller, args);

                    // ★ 处理返回值：兼容原有 String 类型的 JSON
                    if (handler.isResponseBody) {
                        String outStr;
                        if (result instanceof String) {
                            // 如果方法返回 String，直接输出（不二次 JSON 序列化）
                            outStr = (String) result;
                        } else {
                            // 其他类型自动转 JSON
                            outStr = JsonUtil.toJson(result);
                        }
                        // 如果是 JSON 内容且不是纯字符串，设置正确的 Content-Type
                        if (!outStr.startsWith("{") && !outStr.startsWith("[") && !outStr.trim().startsWith("\"")) {
                            // 可能是纯文本，但依然可以用 text/plain
                            res.setContentType("text/plain; charset=utf-8");
                        } else {
                            res.setContentType("application/json; charset=utf-8");
                        }
                        res.write(outStr);
                    } else {
                        // 非 @ResponseBody：普通视图或字符串输出
                        if (result != null) {
                            res.write(result.toString());
                        } else {
                            res.write("OK");
                        }
                    }
                    res.send();
                } catch (Exception e) {
                    e.printStackTrace();
                    res.setStatus(500, "Internal Server Error");
                    String errorMsg = getRootCauseMessage(e);
                    res.write("<h1>500 - " + errorMsg + "</h1>");
                    res.send();
                }
                return;
            }

            // 静态文件
            if (serveStaticFile(uri, res)) {
                return;
            }

            // 404
            res.setStatus(404, "Not Found");
            res.write("<h1>404 - No handler for " + uri + "</h1>");
            res.write("<hr/>Available mappings:<br/>");
            for (String key : handlerMappings.keySet()) {
                res.write(key + "<br/>");
            }
            res.send();
        } finally {
            RequestContextHolder.clear();
            ResponseContextHolder.clear();
        }
    }

    private boolean serveStaticFile(String uri, Response res) {
        if (uri.contains("..") || uri.contains("//")) return false;
        Path filePath = Paths.get(STATIC_BASE, uri);
        File file = filePath.toFile();
        if (!file.exists() || file.isDirectory()) return false;
        String contentType = getContentType(uri);
        res.setContentType(contentType);
        res.setStatus(200, "OK");
        try (FileInputStream fis = new FileInputStream(file)) {
            res.sendStatic(fis, contentType, file.length());
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private String getContentType(String uri) {
        if (uri.endsWith(".html") || uri.endsWith(".htm")) return "text/html; charset=utf-8";
        if (uri.endsWith(".css")) return "text/css; charset=utf-8";
        if (uri.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (uri.endsWith(".json")) return "application/json; charset=utf-8";
        if (uri.endsWith(".png")) return "image/png";
        if (uri.endsWith(".jpg") || uri.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    private String getRootCauseMessage(Throwable t) {
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String msg = t.getMessage();
        return msg != null ? msg : t.getClass().getSimpleName();
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