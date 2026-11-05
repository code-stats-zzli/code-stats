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
import com.omni.framework.util.NetworkUtils;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 集群服务：节点注册表 + Token 管理 + 部署编排。
 *
 * <p><b>masterId / masterToken 来源（★ 本次改动）</b></p>
 * <ol>
 *   <li>从 {@code application.properties} 读取：</li>
 *   <li>{@code cluster.master.id} → masterNodeId</li>
 *   <li>{@code cluster.master.token} → masterToken</li>
 *   <li>未配置时启动期临时生成随机值（仅内存，重启即变），并打印 WARN 提示</li>
 * </ol>
 * <p>不再向 {@code .cluster/} 目录落盘任何文件。</p>
 *
 * <p><b>masterUrl 解析顺序</b></p>
 * <ol>
 *   <li>显式配置 {@code cluster.master.url} → 直接使用</li>
 *   <li>未配置 → 使用 {@link NetworkUtils#getLocalIpv4()} 探测本机真实 IPv4 拼装</li>
 * </ol>
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
    private final Path   deployDir;
    /** 主服务访问 URL（如 http://192.168.1.10:28080/omni） */
    private final String masterUrl;
    /** 主服务纯 host（不含端口，例如 192.168.1.10） */
    private final String masterHost;
    private final int    masterPort;
    private final String contextPath;

    public ClusterService() {
        // ---------- 1. 部署目录 ----------
        this.deployDir = Paths.get(System.getProperty("user.dir"), "resources", "deploy");
        try { Files.createDirectories(deployDir); }
        catch (Exception e) { log.error("创建部署目录失败", e); }

        // ---------- 2. masterNodeId：从配置读取，不再落盘 ----------
        this.masterNodeId = resolveMasterId();

        // ---------- 3. masterToken：从配置读取，不再落盘 ----------
        this.masterToken = resolveMasterToken();

        // ---------- 4. 端口 / contextPath ----------
        this.masterPort  = Integer.parseInt(ConfigLoader.getProperty("server.port", "28080"));
        this.contextPath = ConfigLoader.getProperty("context.path", "/omni");

        // ---------- 5. masterUrl：配置优先，否则探测本机真实 IPv4 ----------
        String configured = ConfigLoader.getProperty("cluster.master.url", "");
        if (configured != null && !configured.trim().isEmpty()) {
            this.masterUrl  = configured.trim();
            this.masterHost = extractHost(this.masterUrl);
            log.info("使用配置的 cluster.master.url = {}", masterUrl);
        } else {
            this.masterHost = NetworkUtils.getLocalIpv4();
            this.masterUrl  = "http://" + masterHost + ":" + masterPort + contextPath;
            log.info("未配置 cluster.master.url，自动探测本机 IP = {}，masterUrl = {}",
                    masterHost, masterUrl);
        }

        // ---------- 6. 注册通道 ----------
        CHANNELS.put("local",  new LocalDeployChannel());
        CHANNELS.put("ssh",    new SshDeployChannel());
        CHANNELS.put("manual", new ManualDeployChannel());

        // ---------- 7. 主节点入表 ----------
        ClusterNode master = new ClusterNode();
        master.setNodeId(masterNodeId);
        master.setRole("MASTER");
        master.setChannel("local");
        master.setState("RUNNING");
        master.setHost(masterHost);
        master.setPort(masterPort);
        master.setStartTime(System.currentTimeMillis());
        master.setLastHeartbeat(System.currentTimeMillis());
        master.setJdkVersion(System.getProperty("java.version"));
        master.setJavaPath(System.getProperty("java.home") + "/bin/java");
        master.setMasterUrl(masterUrl);
        NODES.put(masterNodeId, master);

        // ---------- 8. 启动心跳检查 ----------
        heartbeatChecker.scheduleAtFixedRate(this::checkHeartbeats, 15, 15, TimeUnit.SECONDS);

        log.info("ClusterService 就绪: masterNodeId={}, masterUrl={}, token 长度={}",
                masterNodeId, masterUrl, masterToken.length());
    }

    // ==================== masterId / masterToken 解析 ====================

    private static String resolveMasterId() {
        String v = ConfigLoader.getProperty("cluster.master.id", "");
        if (v != null && !v.trim().isEmpty()) {
            String id = v.trim();
            log.info("cluster.master.id = {}（来自配置）", id);
            return id;
        }
        String tmp = UUID.randomUUID().toString().replace("-", "");
        log.warn("════════════════════════════════════════════════════════════");
        log.warn("  ⚠ 未配置 cluster.master.id，临时生成内存 ID = {}", tmp);
        log.warn("    重启后该 ID 会变化，之前注册的 Worker 会变成 UNKNOWN。");
        log.warn("    强烈建议在 application.properties 中配置固定值，例如：");
        log.warn("      cluster.master.id=master-001");
        log.warn("════════════════════════════════════════════════════════════");
        return tmp;
    }

    private static String resolveMasterToken() {
        String v = ConfigLoader.getProperty("cluster.master.token", "");
        if (v != null && !v.trim().isEmpty()) {
            String token = v.trim();
            log.info("cluster.master.token 已配置（长度={}，预览={}****）",
                    token.length(), token.substring(0, Math.min(8, token.length())));
            return token;
        }
        String tmp = UUID.randomUUID().toString().replace("-", "");
        log.warn("════════════════════════════════════════════════════════════");
        log.warn("  ⚠ 未配置 cluster.master.token，临时生成内存 token（预览={}****）",
                tmp.substring(0, 8));
        log.warn("    重启后该 token 会变化，之前部署的 Worker 将无法通过 Token 校验。");
        log.warn("    强烈建议在 application.properties 中配置固定值，例如：");
        log.warn("      cluster.master.token=change-me-please-use-a-long-random-string");
        log.warn("    并保证 Worker 启动参数一致：");
        log.warn("      --cluster.master.token=<与上面一致>");
        log.warn("════════════════════════════════════════════════════════════");
        return tmp;
    }

    // ==================== 主节点 / Token ====================

    public ClusterNode getMaster() { return NODES.get(masterNodeId); }
    public String getMasterUrl() { return masterUrl; }
    public String getMasterHost() { return masterHost; }
    public String getMasterToken() { return masterToken; }
    public int getMasterPort() { return masterPort; }

    public boolean verifyToken(String token) {
        return token != null && token.equals(masterToken);
    }

    /**
     * 主节点信息（前端卡片使用）。
     * <p>注意：返回的 tokenPreview 仅为展示用，完整 token 不向浏览器暴露，
     * 手动部署时的 downloadUrl / startCommand 已在后端内嵌完整 token。</p>
     */
    public Map<String, Object> getMasterInfo() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nodeId", masterNodeId);
        m.put("host", masterHost);              // ★ 纯 host，不含端口
        m.put("port", masterPort);
        m.put("contextPath", contextPath);
        m.put("masterUrl", masterUrl);
        m.put("jdkVersion", System.getProperty("java.version"));
        m.put("osName", System.getProperty("os.name"));
        m.put("osArch", System.getProperty("os.arch"));
        m.put("startTime", getMaster().getStartTime());
        m.put("tokenPreview", masterToken.substring(0, Math.min(8, masterToken.length())) + "****");
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
            resp.put("success", true);
            resp.put("manualOnly", true);
            resp.put("nodeId", node.getNodeId());
            resp.put("downloadUrl", result.downloadUrl);
            resp.put("startCommand", result.startCommand);
            resp.put("logs", result.logs);
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
        log.info("worker 注册: nodeId={}, host={}, port={}",
                incoming.getNodeId(), incoming.getHost(), incoming.getPort());
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

    /** 从 URL 提取纯 host（不含 scheme / 端口 / path） */
    private static String extractHost(String url) {
        if (url == null || url.isEmpty()) return "127.0.0.1";
        String s = url.replaceFirst("^https?://", "");
        int slash = s.indexOf('/');
        if (slash >= 0) s = s.substring(0, slash);
        int colon = s.lastIndexOf(':');
        if (colon > 0) s = s.substring(0, colon);
        return s;
    }
}