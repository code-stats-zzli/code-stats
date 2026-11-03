// 文件: src/main/java/com/omni/framework/tomcat/container/SimplePipeline.java
package com.omni.framework.tomcat.container;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.config.ConfigLoader;
import com.omni.framework.tomcat.connector.Request;
import com.omni.framework.tomcat.connector.Response;

import java.util.ArrayList;
import java.util.List;

public class SimplePipeline implements Pipeline {
    private static final Logger log = LoggerFactory.getLogger(SimplePipeline.class);

    private final List<Valve> valves = new ArrayList<>();
    private Valve basic;
    private final Container container;

    public SimplePipeline(Container container) { this.container = container; }

    @Override public Valve getBasic() { return basic; }
    @Override public void setBasic(Valve valve) { this.basic = valve; }
    @Override public void addValve(Valve valve) { valves.add(valve); }
    @Override public Valve[] getValves() { return valves.toArray(new Valve[0]); }
    @Override public void removeValve(Valve valve) { valves.remove(valve); }

    @Override
    public void invoke(Request request, Response response) throws Exception {
        new ValveChainImpl().invokeNext(request, response);
    }

    private class ValveChainImpl implements Valve.ValveChain {
        private int index = 0;

        @Override
        public void invokeNext(Request request, Response response) throws Exception {
            if (index < valves.size()) {
                valves.get(index++).invoke(request, response, this);
            } else if (basic != null) {
                basic.invoke(request, response, this);
            } else {
                // 默认处理：交给容器或子容器
                if (container instanceof Wrapper) {
                    ((StandardWrapper) container).invokeServlet(request, response);
                } else if (container instanceof Context) {
                    String path = request.getUri();
                    String servletName = ((Context) container).findServletMapping(path);
                    if (servletName != null) {
                        Container wrapper = container.findChild(servletName);
                        if (wrapper != null) wrapper.getPipeline().invoke(request, response);
                        else fallback404(response);
                    } else fallback404(response);
                } else if (container instanceof Host) {
                    String contextPath = request.getContextPath();
                    Container context = container.findChild(contextPath);
                    if (context != null) {
                        context.getPipeline().invoke(request, response);
                    } else {
                        // ★ 新增：Host 层未匹配 contextPath（如访问 http://localhost:28080 或 /other/xxx）
                        // 自动重定向到应用主页面，而不是返回 404
                        if (tryRedirectToHomePage(request, response)) {
                            return;
                        }
                        fallback404(response);
                    }
                } else if (container instanceof Engine) {
                    Container host = container.findChild(((Engine) container).getDefaultHost());
                    if (host != null) host.getPipeline().invoke(request, response);
                    else fallback404(response);
                }
            }
        }

        private void fallback404(Response response) throws Exception {
            response.setStatus(404, "Not Found");
            response.write("<h1>404 - Not Found</h1>");
            response.send();
        }
    }

    /**
     * ★ 新增：Host 层未匹配 contextPath 时的重定向逻辑。
     *
     * <p>场景：请求到达了正确端口（如 http://localhost:28080），
     * 但 URL 上没有应用 contextPath（或 contextPath 写错），例如：</p>
     * <ul>
     *   <li>{@code http://localhost:28080}        → contextPath="",  uri="/"</li>
     *   <li>{@code http://localhost:28080/}       → contextPath="",  uri="/"</li>
     *   <li>{@code http://localhost:28080/omni}   → contextPath="",  uri="/omni"</li>
     *   <li>{@code http://localhost:28080/xxx}    → contextPath="/xxx", uri="/xxx"</li>
     * </ul>
     * <p>以上情况 Host.findChild(contextPath) 都会失败，我们不再返回 404，
     * 而是 302 重定向到应用主页面（如 {@code /omni/demo/index.html}）。</p>
     *
     * <p>配置读取（与 DispatcherServlet 复用同一套配置）：</p>
     * <ul>
     *   <li>{@code server.home.redirect.enabled}            = true / false（默认 true）</li>
     *   <li>{@code server.home.redirect.url}                = /demo/index.html（默认）</li>
     *   <li>{@code server.home.redirect.exclude-prefixes}   = /api,/agent/api（可选，命中保留 404）</li>
     *   <li>{@code context.path}                            = /omni（应用真正的 contextPath）</li>
     * </ul>
     *
     * @return true 表示已重定向并提交响应；false 表示未触发（调用方应继续走 404 分支）
     */
    private boolean tryRedirectToHomePage(Request request, Response response) {
        String uri = request.getUri();
        if (uri == null) {
            uri = "/";
        }

        // ---------- ① 排除前缀（复用同一份配置，避免 API 404 被误跳到首页） ----------
        String excludePrefixes = null;
        try {
            excludePrefixes = ConfigLoader.getProperty(
                    "server.home.redirect.exclude-prefixes", "");
        } catch (Exception ignored) {
            // 配置读取失败时忽略，不做排除
        }
        if (excludePrefixes != null && !excludePrefixes.trim().isEmpty()) {
            for (String prefix : excludePrefixes.split(",")) {
                String p = prefix.trim();
                if (!p.isEmpty() && uri.startsWith(p)) {
                    log.debug("Host 层未匹配 contextPath，但命中排除前缀，保留 404: uri={}, prefix={}",
                            uri, p);
                    return false;
                }
            }
        }

        // ---------- ② 开关 ----------
        boolean enabled = true;
        try {
            enabled = !"false".equalsIgnoreCase(
                    ConfigLoader.getProperty("server.home.redirect.enabled", "true"));
        } catch (Exception ignored) {
        }
        if (!enabled) {
            return false;
        }

        // ---------- ③ 主页面相对地址 ----------
        String redirectUrl = null;
        try {
            redirectUrl = ConfigLoader.getProperty(
                    "server.home.redirect.url", "/demo/index.html");
        } catch (Exception ignored) {
        }
        if (redirectUrl == null || redirectUrl.trim().isEmpty()) {
            return false;
        }
        redirectUrl = redirectUrl.trim();

        // ---------- ④ 应用真正的 contextPath（本层 request.getContextPath() 不可信） ----------
        String appContextPath = null;
        try {
            appContextPath = ConfigLoader.getProperty("context.path", "/omni");
        } catch (Exception ignored) {
        }
        if (appContextPath == null) {
            appContextPath = "";
        }

        // ---------- ⑤ 拼接最终 URL ----------
        String finalUrl;
        if (redirectUrl.startsWith("http://") || redirectUrl.startsWith("https://")) {
            // 完整 URL：原样使用
            finalUrl = redirectUrl;
        } else if (!appContextPath.isEmpty() && redirectUrl.startsWith(appContextPath + "/")) {
            // 已带 contextPath：原样使用
            finalUrl = redirectUrl;
        } else {
            // 相对路径：补 contextPath（浏览器收到相对 Location 会自动补 host/port）
            finalUrl = appContextPath
                    + (redirectUrl.startsWith("/") ? redirectUrl : "/" + redirectUrl);
        }

        // ---------- ⑥ 执行重定向 ----------
        try {
            log.info("🔄 Host 层未匹配 contextPath，自动重定向到主页面: uri={} → {}",
                    uri, finalUrl);
            response.sendRedirect(finalUrl);
            return true;
        } catch (Exception e) {
            log.warn("Host 层重定向失败（将回退为 404）: uri={}, target={}, err={}",
                    uri, finalUrl, e.getMessage());
            return false;
        }
    }
}