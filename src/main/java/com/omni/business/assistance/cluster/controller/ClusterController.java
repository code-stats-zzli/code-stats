package com.omni.business.assistance.cluster.controller;

import com.omni.business.assistance.cluster.model.ClusterNode;
import com.omni.business.assistance.cluster.model.DeployRequest;
import com.omni.business.assistance.cluster.model.JdkInfo;
import com.omni.business.assistance.cluster.service.ClusterService;
import com.omni.framework.spring.annotation.*;
import com.omni.framework.spring.mvc.RequestContextHolder;
import com.omni.framework.spring.mvc.ResponseContextHolder;
import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.tomcat.servlet.HttpServletResponse;
import com.omni.framework.util.JsonUtil;
import com.omni.framework.util.NetworkUtils;

import java.io.InputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/cluster/api")
public class ClusterController {

    @Autowired
    private ClusterService service;

    // ==================== 主节点 / 部署包 ====================

    @RequestMapping("/master")
    @ResponseBody
    public String master() {
        return JsonUtil.toJson(ok(service.getMasterInfo()));
    }

    /**
     * ★ 新增：服务器 / 客户端网络信息。
     *
     * <p>返回：</p>
     * <ul>
     *   <li>{@code serverIp}  服务端真实 IPv4</li>
     *   <li>{@code clientIp}  客户端真实 IPv4（兼容反向代理）</li>
     *   <li>{@code hostname}  服务端主机名</li>
     *   <li>{@code masterUrl} 主服务访问 URL</li>
     *   <li>{@code interfaces} 所有候选网卡</li>
     *   <li>{@code candidateIps} 候选 IP（按优先级排序）</li>
     * </ul>
     */
    @RequestMapping("/server-info")
    @ResponseBody
    public String serverInfo() {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            HttpServletRequest req = RequestContextHolder.getRequest();

            String serverIp = service.getMasterHost();
            String clientIp = NetworkUtils.getClientIp(req);
            String hostname;
            try { hostname = InetAddress.getLocalHost().getHostName(); }
            catch (Exception e) { hostname = "unknown"; }

            data.put("serverIp", serverIp);
            data.put("serverPort", service.getMasterPort());
            data.put("clientIp", clientIp);
            data.put("hostname", hostname);
            data.put("masterUrl", service.getMasterUrl());

            List<Map<String, Object>> interfaces = NetworkUtils.listNetworkInterfaces();
            data.put("interfaces", interfaces);

            // 候选 IP：过滤掉回环，按 listNetworkInterfaces 内部排序（同优先级保序）
            List<String> candidateIps = new java.util.ArrayList<>();
            if (interfaces != null) {
                for (Map<String, Object> ni : interfaces) {
                    Boolean loopback = (Boolean) ni.get("loopback");
                    Boolean virtual  = (Boolean) ni.get("virtual");
                    String ip = (String) ni.get("ip");
                    if (Boolean.FALSE.equals(loopback) && Boolean.FALSE.equals(virtual) && ip != null) {
                        candidateIps.add(ip);
                    }
                }
            }
            // 去重
            data.put("candidateIps", new java.util.ArrayList<>(new java.util.LinkedHashSet<>(candidateIps)));

            return JsonUtil.toJson(ok(data));
        } catch (Exception e) {
            return error("获取服务器信息失败: " + e.getMessage());
        }
    }

    @RequestMapping("/packages")
    @ResponseBody
    public String packages() {
        List<Map<String, Object>> list = service.listPackages();
        Map<String, Object> m = ok(null);
        m.put("packages", list);
        return JsonUtil.toJson(m);
    }

    @RequestMapping("/packages/download")
    public void downloadPackage() throws Exception {
        HttpServletResponse resp = ResponseContextHolder.getResponse();
        if (resp == null) return;
        HttpServletRequest req = RequestContextHolder.getRequest();
        String jarName = req.getParameter("jar");
        String token   = req.getParameter("token");
        if (!service.verifyToken(token)) {
            resp.setStatus(403, "Forbidden");
            resp.write(JsonUtil.toJson(Map.of("success", false, "error", "Token 无效")));
            resp.send();
            return;
        }
        Path jar = service.resolvePackage(jarName);
        if (jar == null) {
            resp.setStatus(404, "Not Found");
            resp.send();
            return;
        }
        resp.setHeader("Content-Disposition",
                "attachment; filename=\"" + jar.getFileName().toString() + "\"");
        resp.setContentType("application/java-archive");
        try (InputStream in = Files.newInputStream(jar)) {
            resp.sendStatic(in, "application/java-archive", Files.size(jar));
        }
    }

    // ==================== JDK / 端口 ====================

    @RequestMapping("/check-jdk")
    @ResponseBody
    public String checkJdk(@RequestBody DeployRequest req) {
        try {
            JdkInfo info = service.checkJdk(req);
            Map<String, Object> m = ok(null);
            m.put("jdk", info);
            return JsonUtil.toJson(m);
        } catch (Exception e) {
            return error("检测 JDK 失败: " + e.getMessage());
        }
    }

    @RequestMapping("/probe-port")
    @ResponseBody
    public String probePort(@RequestBody DeployRequest req) {
        try {
            return JsonUtil.toJson(service.probePort(req));
        } catch (Exception e) {
            return error("探测端口失败: " + e.getMessage());
        }
    }

    // ==================== 部署 ====================

    @RequestMapping("/deploy")
    @ResponseBody
    public String deploy(@RequestBody DeployRequest req) {
        try {
            return JsonUtil.toJson(service.deploy(req));
        } catch (Exception e) {
            return error("部署失败: " + e.getMessage());
        }
    }

    @RequestMapping("/stop")
    @ResponseBody
    public String stop(@RequestBody Map<String, Object> body) {
        try {
            String nodeId = body == null ? null : String.valueOf(body.get("nodeId"));
            return JsonUtil.toJson(service.stop(nodeId));
        } catch (Exception e) {
            return error("停止失败: " + e.getMessage());
        }
    }

    // ==================== 节点注册 / 心跳 ====================

    @RequestMapping("/node/register")
    @ResponseBody
    public String register(@RequestBody Map<String, Object> body) {
        if (!verifyToken()) return error("Token 无效");
        ClusterNode node = new ClusterNode();
        node.setNodeId(str(body.get("nodeId")));
        node.setPort(intOf(body.get("port"), 0));
        node.setHost(str(body.get("host")));
        node.setJdkVersion(str(body.get("jdkVersion")));
        node.setJavaPath(str(body.get("javaPath")));
        node.setInstanceId(str(body.get("instanceId")));
        node.setChannel(str(body.getOrDefault("channel", "manual")));
        node.setJarName(str(body.get("jarName")));
        return JsonUtil.toJson(service.register(node));
    }

    @RequestMapping("/node/heartbeat")
    @ResponseBody
    public String heartbeat(@RequestBody Map<String, Object> body) {
        if (!verifyToken()) return error("Token 无效");
        service.heartbeat(str(body.get("nodeId")));
        return JsonUtil.toJson(Map.of("success", true));
    }

    @RequestMapping("/node/offline")
    @ResponseBody
    public String offline(@RequestBody Map<String, Object> body) {
        if (!verifyToken()) return error("Token 无效");
        service.offline(str(body.get("nodeId")));
        return JsonUtil.toJson(Map.of("success", true));
    }

    @RequestMapping("/nodes")
    @ResponseBody
    public String nodes() {
        Map<String, Object> m = ok(null);
        m.put("nodes", service.listNodes());
        return JsonUtil.toJson(m);
    }

    // ==================== 工具 ====================

    private boolean verifyToken() {
        HttpServletRequest req = RequestContextHolder.getRequest();
        if (req == null) return false;
        String t = req.getHeader("X-Cluster-Token");
        return service.verifyToken(t);
    }

    private String str(Object o) { return o == null ? null : String.valueOf(o); }
    private int intOf(Object o, int def) {
        if (o == null) return def;
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
    }
    private Map<String, Object> ok(Object data) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", true);
        if (data != null) m.put("data", data);
        return m;
    }
    private String error(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("error", msg);
        return JsonUtil.toJson(m);
    }
}