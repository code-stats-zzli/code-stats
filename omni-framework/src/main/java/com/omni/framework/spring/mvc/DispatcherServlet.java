package com.omni.framework.spring.mvc;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.beans.factory.ConfigurableListableBeanFactory;
import com.omni.framework.spring.config.ConfigLoader;
import com.omni.framework.spring.context.AbstractApplicationContext;
import com.omni.framework.spring.context.SpringContextHolder;
import com.omni.framework.spring.mvc.argument.ArgumentResolverComposite;
import com.omni.framework.spring.mvc.argument.ArgumentResolverFactory;
import com.omni.framework.spring.mvc.exception.DefaultHandlerExceptionResolver;
import com.omni.framework.spring.mvc.handler.DefaultHandlerMethodInvoker;
import com.omni.framework.spring.mvc.mapping.RequestMappingHandlerMapping;
import com.omni.framework.spring.mvc.returnvalue.ModelAndViewReturnValueHandler;
import com.omni.framework.spring.mvc.returnvalue.ResponseBodyReturnValueHandler;
import com.omni.framework.spring.mvc.returnvalue.ReturnValueHandlerComposite;
import com.omni.framework.spring.mvc.returnvalue.VoidReturnValueHandler;
import com.omni.framework.spring.mvc.support.HandlerExecutionChain;
import com.omni.framework.spring.mvc.support.HandlerMethod;
import com.omni.framework.tomcat.servlet.HttpServlet;
import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.tomcat.servlet.HttpServletResponse;
import com.omni.framework.tomcat.util.AccessLogFilter;
import com.omni.framework.web.TraceContextHolder;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.SocketException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Spring MVC 前端控制器（自研 DispatcherServlet）。
 *
 * <p>本次修复：静态资源 {@code webapps/{app}/} 的根目录支持配置、支持多个候选、
 * 支持 {@code ${user.dir}} / {@code ${user.home}} / {@code ${java.io.tmpdir}} 变量，
 * 彻底解决"项目多了一层 {@code omni-bootstrap/} 后静态文件 404"问题。</p>
 *
 * <p>配置项（application.properties）：</p>
 * <pre>
 *   # 静态资源 webapps 父目录候选列表（逗号分隔，按顺序探测，命中即用）
 *   # 每个条目指向"包含 demo/ agent/ files/ 等 webapp 子目录的那个 webapps 目录"
 *   # 支持相对路径（相对 user.dir）与绝对路径；支持 ${user.dir} 等变量
 *   # 留空/不配置：使用内置默认候选
 *   webapps.roots=${user.dir}/webapps,${user.dir}/omni-bootstrap/webapps
 * </pre>
 */
public class DispatcherServlet extends HttpServlet {
    private static final Logger log = LoggerFactory.getLogger(DispatcherServlet.class);

    private RequestMappingHandlerMapping handlerMapping;
    private DefaultHandlerMethodInvoker handlerMethodInvoker;

    private static final Set<String> SENSITIVE_PARAM_NAMES = new HashSet<>(Arrays.asList(
            "password", "passwd", "pwd", "secret", "token",
            "accessToken", "apiKey", "apikey", "key", "credential"
    ));

    /** 静态资源候选根目录缓存（配置项 webapps.roots 的解析结果，弱缓存 5 秒） */
    private volatile List<Path> cachedWebappParentRoots;
    private volatile long cachedWebappRootsAt = 0L;
    private static final long WEBAPP_ROOTS_CACHE_MS = 5000L;

    @Override
    public void init() throws Exception {
        log.info("DispatcherServlet 初始化开始");

        AbstractApplicationContext ctx = SpringContextHolder.getApplicationContext();
        ConfigurableListableBeanFactory beanFactory = ctx.getBeanFactory();

        this.handlerMapping = new RequestMappingHandlerMapping();
        this.handlerMapping.init(beanFactory);

        ArgumentResolverComposite argumentResolverComposite = ArgumentResolverFactory.createDefault();

        ReturnValueHandlerComposite returnValueHandlerComposite = new ReturnValueHandlerComposite()
                .add(new ResponseBodyReturnValueHandler())
                .add(new ModelAndViewReturnValueHandler())
                .add(new VoidReturnValueHandler());

        this.handlerMethodInvoker = new DefaultHandlerMethodInvoker(
                argumentResolverComposite,
                returnValueHandlerComposite,
                new DefaultHandlerExceptionResolver()
        );

        // 启动时打印一次静态资源候选根，便于排查
        List<Path> roots = resolveWebappParentRoots(true);
        if (roots.isEmpty()) {
            log.warn("⚠ 静态资源根目录候选为空（webapps.roots 配置解析后无有效目录），静态文件访问将返回 404");
        } else {
            log.info("静态资源根目录候选：");
            for (int i = 0; i < roots.size(); i++) {
                log.info("  [{}] {}", i + 1, roots.get(i));
            }
        }

        log.info("DispatcherServlet 初始化完成");
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

        boolean accessLog = AccessLogFilter.shouldLog(uri);

        if (accessLog) {
            logRequest(req, method, uri, query);
        }

        long threadId = Thread.currentThread().getId();
        TraceContextHolder.putContext(threadId, uri);

        RequestContextHolder.setRequest(req);
        ResponseContextHolder.setResponse(res);
        MultipartResolver multipartResolver = null;
        Throwable requestError = null;

        try {
            HandlerExecutionChain chain = handlerMapping.getHandler(uri);
            if (chain != null) {
                HandlerMethod handlerMethod = chain.getHandlerMethod();
                Map<String, String> pathVars = chain.getPathVariables();

                if (accessLog) {
                    log.info("匹配处理器: {}.{}",
                            handlerMethod.getController().getClass().getSimpleName(),
                            handlerMethod.getMethod().getName());
                    if (!pathVars.isEmpty()) {
                        log.info(" 路径参数: {}", pathVars);
                    }
                }

                handlerMethodInvoker.invoke(handlerMethod, req, res, pathVars);

                if (!res.isCommitted()) {
                    res.send();
                }

                if (accessLog) {
                    log.info(" 请求处理完成: {} {} 耗时: {}ms",
                            method, uri, System.currentTimeMillis() - startTime);
                }
                return;
            }

            if (serveStaticFile(uri, res)) {
                if (accessLog) {
                    log.info(" 静态文件响应: {} 耗时: {}ms",
                            uri, System.currentTimeMillis() - startTime);
                }
                return;
            }

            if (tryRedirectToHomePage(req, res)) {
                if (accessLog) {
                    log.info(" 重定向到主页面: {} → {} 耗时: {}ms",
                            method, uri, System.currentTimeMillis() - startTime);
                }
                return;
            }

            res.setStatus(404, "Not Found");
            res.write("<h1>404 - Not Found</h1>");
            res.send();
            log.warn("⚠️ 404 Not Found: {} {} 耗时: {}ms",
                    method, uri, System.currentTimeMillis() - startTime);

        } catch (Throwable e) {
            requestError = e;
            if (e instanceof SocketException) {
                String msg = e.getMessage();
                if (msg != null && (msg.contains("软件中止")
                        || msg.contains("Connection reset")
                        || msg.contains("Broken pipe"))) {
                    log.debug("客户端已断开连接，请求详情: {} {}?{}",
                            method, uri, query != null ? query : "");
                    return;
                }
            }

            log.error(" 处理请求失败: {} {} 耗时: {}ms",
                    method, uri, System.currentTimeMillis() - startTime, e);

            if (!res.isCommitted()) {
                try {
                    res.setStatus(500, "Internal Server Error");
                    res.write("<h1>500 Internal Server Error</h1><pre>" + e + "</pre>");
                    res.send();
                } catch (Exception ex) {
                    log.error("发送错误响应失败", ex);
                }
            }
        } finally {
            String errorMsg = requestError != null ? requestError.getMessage() : null;
            TraceContextHolder.updateContext(threadId, errorMsg, null);

            if (multipartResolver != null) {
                multipartResolver.close();
            }
            RequestContextHolder.clear();
            ResponseContextHolder.clear();
        }
    }

    private boolean tryRedirectToHomePage(HttpServletRequest req, HttpServletResponse res) {
        String uri = req.getUri();
        if (uri == null) {
            uri = "/";
        }
        boolean isDirectoryAccess =
                uri.isEmpty()
                        || "/".equals(uri)
                        || uri.endsWith("/");
        if (!isDirectoryAccess) {
            return false;
        }

        String excludePrefixes = ConfigLoader.getProperty(
                "server.home.redirect.exclude-prefixes", "");
        if (excludePrefixes != null && !excludePrefixes.trim().isEmpty()) {
            for (String prefix : excludePrefixes.split(",")) {
                String p = prefix.trim();
                if (!p.isEmpty() && uri.startsWith(p)) {
                    log.debug("目录访问命中排除前缀，跳过重定向: uri={}, prefix={}", uri, p);
                    return false;
                }
            }
        }

        boolean enabled = !"false".equalsIgnoreCase(
                ConfigLoader.getProperty("server.home.redirect.enabled", "true"));
        if (!enabled) {
            return false;
        }

        String redirectUrl = ConfigLoader.getProperty(
                "server.home.redirect.url", "/demo/index.html");
        if (redirectUrl == null || redirectUrl.trim().isEmpty()) {
            return false;
        }
        redirectUrl = redirectUrl.trim();

        String contextPath = req.getContextPath();
        if (contextPath == null) {
            contextPath = "";
        }

        String finalUrl;
        if (redirectUrl.startsWith("http://") || redirectUrl.startsWith("https://")) {
            finalUrl = redirectUrl;
        } else if (!contextPath.isEmpty() && redirectUrl.startsWith(contextPath + "/")) {
            finalUrl = redirectUrl;
        } else {
            finalUrl = contextPath + (redirectUrl.startsWith("/") ? redirectUrl : "/" + redirectUrl);
        }

        try {
            log.info("🔄 目录访问自动重定向: {} → {}", uri, finalUrl);
            res.sendRedirect(finalUrl);
            return true;
        } catch (Exception e) {
            log.warn("重定向到主页面失败（将回退为 404）: uri={}, target={}, err={}",
                    uri, finalUrl, e.getMessage());
            return false;
        }
    }

    private void logRequest(HttpServletRequest req, String method, String uri, String query) {
        log.info("==========  收到请求 ==========");
        log.info("请求方法: {}", method);
        log.info("请求URI: {}", uri);
        log.info("查询字符串: {}", query != null ? query : "(无)");

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

        String body = req.getBody();
        if (body != null && !body.isEmpty()) {
            String bodyPreview = body.length() > 2000
                    ? body.substring(0, 2000) + "...(截断，总长度" + body.length() + ")"
                    : body;
            log.info("请求Body: {}", bodyPreview);
        }

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
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    // ==================== 静态资源 ====================

    /**
     * 静态文件服务：
     *   URI 第一段是 webapp 名（如 demo / agent），后续是子路径。
     *   依次在 {@link #resolveWebappParentRoots(boolean)} 返回的候选根下
     *   查找 {@code <candidate>/<webappRoot>/<subPath>}，命中即返回。
     */
    private boolean serveStaticFile(String uri, HttpServletResponse res) {
        if (uri == null || uri.contains("..") || uri.contains("//")) return false;
        String cleanUri = uri.startsWith("/") ? uri.substring(1) : uri;
        int firstSlash = cleanUri.indexOf('/');
        if (firstSlash <= 0) return false;
        String webappRoot = cleanUri.substring(0, firstSlash);
        String subPath = cleanUri.substring(firstSlash + 1);
        // 输入防御：webappRoot 不允许含路径分隔符
        if (webappRoot.contains("\\") || webappRoot.isEmpty()) return false;

        List<Path> parentRoots = resolveWebappParentRoots(false);
        if (parentRoots.isEmpty()) {
            log.debug("[static] webapps 候选根为空，uri={}", uri);
            return false;
        }

        for (Path parentRoot : parentRoots) {
            Path base = parentRoot.resolve(webappRoot).normalize();
            // 二次防御：确保 base 在 parentRoot 下
            if (!base.startsWith(parentRoot)) continue;
            Path filePath = base.resolve(subPath).normalize();
            if (!filePath.startsWith(base)) continue;
            File file = filePath.toFile();
            if (!file.exists() || file.isDirectory()) continue;
            String contentType = getContentType(uri);
            try (FileInputStream fis = new FileInputStream(file)) {
                res.sendStatic(fis, contentType, file.length());
                log.debug("[static] hit: {}", filePath);
                return true;
            } catch (Exception e) {
                log.error("Static file error: {}", filePath, e);
            }
        }
        log.debug("[static] miss: uri={}, tried roots={}", uri, parentRoots);
        return false;
    }

    /**
     * 解析静态资源 webapps 父目录候选列表。
     *
     * <p>来源：</p>
     * <ol>
     *   <li>配置 {@code webapps.roots}（逗号分隔），支持 {@code ${user.dir}} 等变量；</li>
     *   <li>未配置时使用默认候选：{@code user.dir/webapps}、{@code user.dir/omni-bootstrap/webapps}、
     *       父目录下同名目录；</li>
     *   <li>jar 位置派生的候选（追加在末尾，仅当 jar 是 fat jar 时有用）。</li>
     * </ol>
     *
     * @param refresh true 强制刷新缓存（用于启动日志打印）
     */
    private List<Path> resolveWebappParentRoots(boolean refresh) {
        long now = System.currentTimeMillis();
        if (!refresh && cachedWebappParentRoots != null
                && (now - cachedWebappRootsAt) < WEBAPP_ROOTS_CACHE_MS) {
            return cachedWebappParentRoots;
        }
        synchronized (this) {
            if (!refresh && cachedWebappParentRoots != null
                    && (now - cachedWebappRootsAt) < WEBAPP_ROOTS_CACHE_MS) {
                return cachedWebappParentRoots;
            }
            List<Path> roots = new ArrayList<>();
            try {
                String cfg = ConfigLoader.getProperty("webapps.roots", "");
                if (cfg != null && !cfg.trim().isEmpty()) {
                    for (String s : cfg.split(",")) {
                        String t = s.trim();
                        if (t.isEmpty()) continue;
                        t = expandVars(t);
                        try {
                            Path p = Paths.get(t);
                            if (!p.isAbsolute()) {
                                p = Paths.get(System.getProperty("user.dir", ".")).resolve(p);
                            }
                            addRootDir(roots, p);
                        } catch (Exception ignored) {
                        }
                    }
                } else {
                    // 未配置：默认候选
                    Path userDir = Paths.get(System.getProperty("user.dir", "."))
                            .toAbsolutePath().normalize();
                    addRootDir(roots, userDir.resolve("webapps"));
                    addRootDir(roots, userDir.resolve("omni-bootstrap").resolve("webapps"));
                    Path parent = userDir.getParent();
                    if (parent != null) {
                        addRootDir(roots, parent.resolve("webapps"));
                        addRootDir(roots, parent.resolve("omni-bootstrap").resolve("webapps"));
                    }
                }
            } catch (Exception e) {
                log.warn("解析 webapps.roots 失败: {}", e.getMessage());
            }

            // jar 位置候选（补充，不覆盖）
            try {
                URL url = DispatcherServlet.class.getProtectionDomain().getCodeSource().getLocation();
                if (url != null) {
                    Path codeSource = Paths.get(url.toURI());
                    Path jarDir = Files.isDirectory(codeSource) ? codeSource : codeSource.getParent();
                    if (jarDir != null) {
                        addRootDir(roots, jarDir.resolve("webapps"));
                        addRootDir(roots, jarDir.resolve("omni-bootstrap").resolve("webapps"));
                        Path jarParent = jarDir.getParent();
                        if (jarParent != null) {
                            addRootDir(roots, jarParent.resolve("webapps"));
                            addRootDir(roots, jarParent.resolve("omni-bootstrap").resolve("webapps"));
                        }
                    }
                }
            } catch (Exception ignored) {
            }

            cachedWebappParentRoots = Collections.unmodifiableList(roots);
            cachedWebappRootsAt = System.currentTimeMillis();
            return cachedWebappParentRoots;
        }
    }

    private void addRootDir(List<Path> list, Path p) {
        try {
            Path n = p.toAbsolutePath().normalize();
            if (Files.isDirectory(n) && !list.contains(n)) {
                list.add(n);
            }
        } catch (Exception ignored) {
        }
    }

    private String expandVars(String s) {
        if (s == null) return null;
        return s.replace("${user.dir}", System.getProperty("user.dir", "."))
                .replace("${user.home}", System.getProperty("user.home", "."))
                .replace("${java.io.tmpdir}", System.getProperty("java.io.tmpdir", "."));
    }

    private String getContentType(String uri) {
        if (uri.endsWith(".html")) return "text/html; charset=utf-8";
        if (uri.endsWith(".css")) return "text/css; charset=utf-8";
        if (uri.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (uri.endsWith(".json")) return "application/json; charset=utf-8";
        if (uri.endsWith(".png")) return "image/png";
        if (uri.endsWith(".jpg") || uri.endsWith(".jpeg")) return "image/jpeg";
        if (uri.endsWith(".svg")) return "image/svg+xml";
        if (uri.endsWith(".ico")) return "image/x-icon";
        if (uri.endsWith(".woff")) return "font/woff";
        if (uri.endsWith(".woff2")) return "font/woff2";
        if (uri.endsWith(".ttf")) return "font/ttf";
        return "application/octet-stream";
    }
}