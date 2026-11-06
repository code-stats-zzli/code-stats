package com.omni.business.assistance.cluster.controller;

import com.omni.business.assistance.cluster.model.ClusterNode;
import com.omni.business.assistance.cluster.model.DeployRequest;
import com.omni.business.assistance.cluster.model.JdkInfo;
import com.omni.business.assistance.cluster.service.ClusterService;
import com.omni.business.common.util.BaseController;
import com.omni.business.common.util.ResponseHelper;
import com.omni.common.util.StringUtils;
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

/** ★ L1-01 + L1-05 + L2-01 */
@Controller
@RequestMapping("/cluster/api")
public class ClusterController extends BaseController {

    @Autowired
    private ClusterService service;

    @RequestMapping("/master")
    @ResponseBody
    public String master() { return ok(service.getMasterInfo()); }

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

            List<String> candidateIps = new java.util.ArrayList<>();
            if (interfaces != null) {
                for (Map<String, Object> ni : interfaces) {
                    Boolean loopback = (Boolean) ni.get("loopback");
                    Boolean virtual = (Boolean) ni.get("virtual");
                    String ip = (String) ni.get("ip");
                    if (Boolean.FALSE.equals(loopback) && Boolean.FALSE.equals(virtual) && ip != null) candidateIps.add(ip);
                }
            }
            data.put("candidateIps", new java.util.ArrayList<>(new java.util.LinkedHashSet<>(candidateIps)));
            return ok(data);
        } catch (Exception e) {
            return error("获取服务器信息失败: " + e.getMessage());
        }
    }

    @RequestMapping("/packages")
    @ResponseBody
    public String packages() {
        List<Map<String, Object>> list = service.listPackages();
        return okOne("packages", list);
    }

    @RequestMapping("/packages/download")
    public void downloadPackage() throws Exception {
        HttpServletResponse resp = ResponseContextHolder.getResponse();
        if (resp == null) return;
        HttpServletRequest req = RequestContextHolder.getRequest();
        String jarName = req.getParameter("jar");
        String token = req.getParameter("token");
        if (!service.verifyToken(token)) { ResponseHelper.writeError(resp, 403, "Token 无效"); return; }
        Path jar = service.resolvePackage(jarName);
        if (jar == null) { ResponseHelper.writeError(resp, 404, "包不存在"); return; }
        ResponseHelper.writeAttachment(resp, jar, jar.getFileName().toString(), "application/java-archive");
    }

    @RequestMapping("/check-jdk")
    @ResponseBody
    public String checkJdk(@RequestBody DeployRequest req) {
        try {
            JdkInfo info = service.checkJdk(req);
            return okOne("jdk", info);
        } catch (Exception e) { return error("检测 JDK 失败: " + e.getMessage()); }
    }

    @RequestMapping("/probe-port")
    @ResponseBody
    public String probePort(@RequestBody DeployRequest req) {
        try { return JsonUtil.toJson(service.probePort(req)); }
        catch (Exception e) { return error("探测端口失败: " + e.getMessage()); }
    }

    @RequestMapping("/deploy")
    @ResponseBody
    public String deploy(@RequestBody DeployRequest req) {
        try { return JsonUtil.toJson(service.deploy(req)); }
        catch (Exception e) { return error("部署失败: " + e.getMessage()); }
    }

    @RequestMapping("/stop")
    @ResponseBody
    public String stop(@RequestBody Map<String, Object> body) {
        try {
            String nodeId = body == null ? null : StringUtils.safe(body.get("nodeId"));
            return JsonUtil.toJson(service.stop(nodeId));
        } catch (Exception e) { return error("停止失败: " + e.getMessage()); }
    }

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
        return ok();
    }

    @RequestMapping("/node/offline")
    @ResponseBody
    public String offline(@RequestBody Map<String, Object> body) {
        if (!verifyToken()) return error("Token 无效");
        service.offline(str(body.get("nodeId")));
        return ok();
    }

    @RequestMapping("/nodes")
    @ResponseBody
    public String nodes() { return okOne("nodes", service.listNodes()); }

    private boolean verifyToken() {
        HttpServletRequest req = RequestContextHolder.getRequest();
        if (req == null) return false;
        return service.verifyToken(req.getHeader("X-Cluster-Token"));
    }

    private String str(Object o) { return StringUtils.safe(o); }
    private int intOf(Object o, int def) {
        if (o == null) return def;
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
    }
}