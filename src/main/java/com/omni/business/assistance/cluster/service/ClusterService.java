package com.omni.business.assistance.cluster.service;

import com.omni.business.assistance.cluster.channel.DeployChannel;
import com.omni.business.assistance.cluster.channel.LocalDeployChannel;
import com.omni.business.assistance.cluster.channel.ManualDeployChannel;
import com.omni.business.assistance.cluster.channel.SshDeployChannel;
import com.omni.business.assistance.cluster.model.ClusterNode;
import com.omni.business.assistance.cluster.model.DeployRequest;
import com.omni.business.assistance.cluster.model.JdkInfo;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Service;
import com.omni.framework.spring.config.ConfigLoader;
import com.omni.framework.util.JsonUtil;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 集群服务：节点注册表 + Token 管理 + 部署编排。
 */
@Service
public class ClusterService {
    private static final Logger log = LoggerFactory.getLogger(ClusterService.class);

    private final Map<String, ClusterNode> NODES = new ConcurrentHashMap<>();
    private final Map<String, DeployChannel> CHANNELS = new HashMap<>();
    private final ScheduledExecutorService heartbeatChecker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "cluster-heartbeat-checker");
        t.setDaemon(true);
        return t;
    });

    private final String masterNodeId;
    private final String masterToken;
    private final Path   clusterDir;
    private final Path   deployDir;     // resources/deploy
    private final String masterUrl;     // 外部可访问的主服务 URL
    private final int    masterPort;
    private final String contextPath;

    public ClusterService() {
        this.clusterDir = Paths.get(System.getProperty("user.dir"), ".cluster");
        this.deployDir  = Paths.get(System.getProperty("user.dir"), "resources", "deploy");
        try { Files.createDirectories(clusterDir); Files.createDirectories(deployDir); }
        catch (Exception e) { log.error("创建集群目录失败", e); }

        this.masterNodeId = loadOrCreate(clusterDir.resolve("master.id"),
                () -> UUID.randomUUID().toString().replace("-", ""));
        this.masterToken  = loadOrCreate(clusterDir.resolve("master.token"),
                () -> UUID.randomUUID().toString().replace("-", ""));

        this.masterPort  = Integer.parseInt(ConfigLoader.getProperty("server.port", "28080"));
        this.contextPath = ConfigLoader.getProperty("context.path", "/omni");
        this.masterUrl   = ConfigLoader.getProperty("cluster.master.url",
                "http://127.0.0.1:" + masterPort + contextPath);

        // 注册通道
        CHANNELS.put("local",  new LocalDeployChannel());
        CHANNELS.put("ssh",    new SshDeployChannel());
        CHANNELS.put("manual", new ManualDeployChannel());

        // 主节点入表
        ClusterNode master = new ClusterNode();
        master.setNodeId(masterNodeId);
        master.setRole("MASTER");
        master.setChannel("local");
        master.setState("RUNNING");
        master.setPort(masterPort);
        master.setStartTime(System.currentTimeMillis());
        master.setLastHeartbeat(System.currentTimeMillis());
        master.setJdkVersion(System.getProperty("java.version"));
        master.setJavaPath(System.getProperty("java.home") + "/bin/java");
        master.setMasterUrl(masterUrl);
        NODES.put(masterNodeId, master);

        // 启动心跳检查
        heartbeatChecker.scheduleAtFixedRate(this::checkHeartbeats, 15, 15, TimeUnit.SECONDS);

        log.info("ClusterService 就绪: masterNodeId={}, masterUrl={}, token 长度={}",
                masterNodeId, masterUrl, masterToken.length());
    }

    // ==================== 主节点 / Token ====================

    public ClusterNode getMaster() { return NODES.get(masterNodeId); }
    public String getMasterUrl() { return masterUrl; }
    public String getMasterToken() { return masterToken; }
    public int getMasterPort() { return masterPort; }

    public boolean verifyToken(String token) {
        return token != null && token.equals(masterToken);
    }

    public Map<String, Object> getMasterInfo() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nodeId", masterNodeId);
        m.put("host", masterUrl.replaceAll("^https?://", "").replaceAll("/.*$", ""));
        m.put("port", masterPort);
        m.put("contextPath", contextPath);
        m.put("masterUrl", masterUrl);
        m.put("jdkVersion", System.getProperty("java.version"));
        m.put("osName", System.getProperty("os.name"));
        m.put("osArch", System.getProperty("os.arch"));
        m.put("startTime", getMaster().getStartTime());
        m.put("tokenPreview", masterToken.substring(0, 8) + "****");
        return m;
    }

    // ==================== 部署包 ====================

    public List<Map<String, Object>> listPackages() {
        List<Map<String, Object>> list = new ArrayList<>();
        if (!Files.isDirectory(deployDir)) return list;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(deployDir, "*.jar")) {
            for (Path p : ds) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("jarName", p.getFileName().toString());
                m.put("size", Files.size(p));
                m.put("modified", Files.getLastModifiedTime(p).toMillis());
                m.put("md5", md5(p));
                list.add(m);
            }
        } catch (Exception e) { log.warn("列举部署包失败", e); }
        list.sort((a, b) -> Long.compare((Long) b.get("modified"), (Long) a.get("modified")));
        return list;
    }

    public Path resolvePackage(String jarName) {
        if (jarName == null || jarName.contains("..") || jarName.contains("/")
                || jarName.contains("\\")) return null;
        Path p = deployDir.resolve(jarName).normalize();
        return p.startsWith(deployDir) && Files.isRegularFile(p) ? p : null;
    }

    // ==================== JDK 检测 / 端口探测 ====================

    public JdkInfo checkJdk(DeployRequest req) throws Exception {
        return channel(req.getChannel()).checkJdk(req);
    }

    public Map<String, Object> probePort(DeployRequest req) throws Exception {
        int from = req.getPort() > 0 ? req.getPort() : masterPort + 1;
        int port = channel(req.getChannel()).probePort(req, from, 50);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", port > 0);
        m.put("port", port);
        m.put("message", port > 0
                ? "建议使用端口 " + port
                : "从 " + from + " 向后 50 个端口均被占用");
        return m;
    }

    // ==================== 部署 ====================

    public Map<String, Object> deploy(DeployRequest req) throws Exception {
        Map<String, Object> resp = new LinkedHashMap<>();
        if (req.getJarName() == null || req.getJarName().isEmpty()) {
            resp.put("success", false); resp.put("error", "缺少 jarName"); return resp;
        }
        Path jar = resolvePackage(req.getJarName());
        if (jar == null) {
            resp.put("success", false); resp.put("error", "部署包不存在: " + req.getJarName()); return resp;
        }
        if (req.getPort() <= 0) {
            resp.put("success", false); resp.put("error", "请先探测端口"); return resp;
        }
        if ("local".equals(req.getChannel()) && (req.getDeployDir() == null || req.getDeployDir().isEmpty())) {
            req.setDeployDir(Paths.get(System.getProperty("user.dir"), "cluster", "workers").toString());
        }

        DeployChannel ch = channel(req.getChannel());

        ClusterNode node = new ClusterNode();
        node.setNodeId(UUID.randomUUID().toString().replace("-", ""));
        node.setChannel(req.getChannel());
        node.setRole("WORKER");
        node.setState("STARTING");
        node.setPort(req.getPort());
        node.setHost(req.getHost());
        node.setSshUser(req.getSshUser());
        node.setSshKeyPath(req.getSshKeyPath());
        node.setSshPort(req.getSshPort());
        node.setJarName(req.getJarName());
        node.setJarMd5(md5(jar));
        node.setDeployDir(req.getDeployDir());
        node.setJavaPath(req.getJavaPath());
        node.setMasterUrl(masterUrl);
        node.setStartTime(System.currentTimeMillis());

        DeployChannel.DeployResult result = ch.deploy(req, jar, node, masterUrl, masterToken);
        if (!result.success) {
            node.setState("ERROR");
            node.setErrorMsg(result.message);
            NODES.put(node.getNodeId(), node);
            resp.put("success", false);
            resp.put("error", result.message);
            return resp;
        }

        if (result.pid != null) node.setPid(result.pid);

        if (result.manualOnly) {
            // 手动模式：只返回命令，不登记节点（等 Worker 上线才注册）
            resp.put("success", true);
            resp.put("manualOnly", true);
            resp.put("nodeId", node.getNodeId());
            resp.put("downloadUrl", result.downloadUrl);
            resp.put("startCommand", result.startCommand);
            resp.put("logs", result.logs);
            // 先入表 STARTING，让用户能看到"等待注册"
            NODES.put(node.getNodeId(), node);
        } else {
            NODES.put(node.getNodeId(), node);
            resp.put("success", true);
            resp.put("nodeId", node.getNodeId());
            resp.put("pid", result.pid);
            resp.put("startCommand", result.startCommand);
            resp.put("logs", result.logs);
        }
        return resp;
    }

    public Map<String, Object> stop(String nodeId) throws Exception {
        Map<String, Object> resp = new LinkedHashMap<>();
        ClusterNode node = NODES.get(nodeId);
        if (node == null) { resp.put("success", false); resp.put("error", "节点不存在"); return resp; }
        if ("MASTER".equals(node.getRole())) {
            resp.put("success", false); resp.put("error", "不能停止主节点"); return resp;
        }
        DeployChannel ch = channel(node.getChannel());
        DeployChannel.StopResult r = ch.stop(node);
        node.setState("OFFLINE");
        resp.put("success", r.success);
        if (r.manualOnly) {
            resp.put("manualOnly", true);
            resp.put("stopCommand", r.stopCommand);
        }
        if (r.message != null) resp.put("message", r.message);
        return resp;
    }

    // ==================== 节点注册 / 心跳 ====================

    public Map<String, Object> register(ClusterNode incoming) {
        Map<String, Object> resp = new LinkedHashMap<>();
        if (incoming == null || incoming.getNodeId() == null) {
            resp.put("success", false); resp.put("error", "缺少 nodeId"); return resp;
        }
        ClusterNode existing = NODES.get(incoming.getNodeId());
        if (existing != null) {
            existing.setLastHeartbeat(System.currentTimeMillis());
            existing.setState("RUNNING");
            existing.setPort(incoming.getPort() > 0 ? incoming.getPort() : existing.getPort());
            existing.setJdkVersion(incoming.getJdkVersion());
            existing.setHost(incoming.getHost());
            existing.setInstanceId(incoming.getInstanceId());
            resp.put("success", true);
            resp.put("reRegistered", true);
            return resp;
        }
        incoming.setRole("WORKER");
        incoming.setState("RUNNING");
        incoming.setLastHeartbeat(System.currentTimeMillis());
        if (incoming.getStartTime() == 0) incoming.setStartTime(System.currentTimeMillis());
        NODES.put(incoming.getNodeId(), incoming);
        log.info("worker 注册: nodeId={}, port={}, host={}",
                incoming.getNodeId(), incoming.getPort(), incoming.getHost());
        resp.put("success", true);
        resp.put("masterNodeId", masterNodeId);
        return resp;
    }

    public void heartbeat(String nodeId) {
        ClusterNode node = NODES.get(nodeId);
        if (node != null) {
            node.setLastHeartbeat(System.currentTimeMillis());
            if ("UNKNOWN".equals(node.getState()) || "STARTING".equals(node.getState())) {
                node.setState("RUNNING");
            }
        }
    }

    public void offline(String nodeId) {
        ClusterNode node = NODES.get(nodeId);
        if (node != null) node.setState("OFFLINE");
    }

    public List<Map<String, Object>> listNodes() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ClusterNode n : NODES.values()) list.add(n.toView());
        list.sort((a, b) -> {
            String ra = (String) a.get("role"), rb = (String) b.get("role");
            if (!ra.equals(rb)) return ra.equals("MASTER") ? -1 : 1;
            return Long.compare((Long) a.get("startTime"), (Long) b.get("startTime"));
        });
        return list;
    }

    // ==================== 内部 ====================

    private void checkHeartbeats() {
        long now = System.currentTimeMillis();
        long timeout = 45_000L;
        for (ClusterNode n : NODES.values()) {
            if ("MASTER".equals(n.getRole())) continue;
            if ("OFFLINE".equals(n.getState()) || "ERROR".equals(n.getState())) continue;
            long hb = n.getLastHeartbeat();
            if (hb > 0 && now - hb > timeout) {
                if (!"UNKNOWN".equals(n.getState())) {
                    n.setState("UNKNOWN");
                    log.warn("节点 {} 心跳超时，标记 UNKNOWN", n.getNodeId());
                }
            }
        }
    }

    private DeployChannel channel(String name) {
        DeployChannel ch = CHANNELS.get(name == null ? "manual" : name);
        return ch != null ? ch : CHANNELS.get("manual");
    }

    private static String loadOrCreate(Path p, java.util.function.Supplier<String> sup) {
        try {
            if (Files.exists(p)) return new String(Files.readAllBytes(p), "UTF-8").trim();
            String v = sup.get();
            Files.write(p, v.getBytes("UTF-8"));
            return v;
        } catch (Exception e) { return sup.get(); }
    }

    private static String md5(Path p) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] data = Files.readAllBytes(p);
            byte[] d = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return ""; }
    }
}