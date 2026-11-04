package com.omni.business.assistance.cluster;

import com.omni.framework.util.JsonUtil;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Worker 端自举：启动时向主服务注册 + 心跳。
 * 独立于 Spring，用 HttpURLConnection + JsonUtil，无额外依赖。
 */
public final class WorkerBootstrap {

    private WorkerBootstrap() {}

    private static volatile boolean running = true;
    private static final String INSTANCE_ID = UUID.randomUUID().toString().replace("-", "");

    public static void start(String[] args) {
        String masterUrl = getArg(args, "--cluster.master.url");
        String token     = getArg(args, "--cluster.master.token");
        String nodeId    = getArg(args, "--cluster.node.id");
        String port      = getArg(args, "--server.port");

        if (masterUrl == null || token == null || nodeId == null) {
            System.err.println("[WorkerBootstrap] 缺少集群参数，跳过注册");
            return;
        }
        System.out.println("[WorkerBootstrap] worker 启动，准备注册到 " + masterUrl + " nodeId=" + nodeId);

        Thread t = new Thread(() -> {
            registerWithRetry(masterUrl, token, nodeId, port);
            heartbeatLoop(masterUrl, token, nodeId);
        }, "cluster-worker-heartbeat");
        t.setDaemon(true);
        t.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running = false;
            try {
                postJson(masterUrl + "/cluster/api/node/offline", token,
                        "{\"nodeId\":\"" + nodeId + "\"}", 3000);
                System.out.println("[WorkerBootstrap] worker 已下线");
            } catch (Exception ignored) {}
        }, "cluster-worker-offline"));
    }

    public static boolean isWorker(String[] args) {
        if (args == null) return false;
        for (String a : args) if ("--cluster.role=worker".equals(a)) return true;
        return false;
    }

    private static void registerWithRetry(String masterUrl, String token,
                                          String nodeId, String port) {
        for (int i = 0; i < 30 && running; i++) {
            try {
                Map<String, Object> body = new HashMap<>();
                body.put("nodeId", nodeId);
                body.put("port", parseInt(port, 0));
                body.put("host", safeHost());
                body.put("jdkVersion", System.getProperty("java.version"));
                body.put("javaPath", System.getProperty("java.home") + "/bin/java");
                body.put("instanceId", INSTANCE_ID);
                body.put("channel", System.getProperty("cluster.channel", "manual"));
                String resp = postJson(masterUrl + "/cluster/api/node/register", token,
                        JsonUtil.toJson(body), 5000);
                System.out.println("[WorkerBootstrap] 注册成功: " + resp);
                return;
            } catch (Exception e) {
                System.err.println("[WorkerBootstrap] 注册失败(" + (i + 1) + "/30): " + e.getMessage());
                sleep(2000);
            }
        }
    }

    private static void heartbeatLoop(String masterUrl, String token, String nodeId) {
        while (running) {
            try {
                postJson(masterUrl + "/cluster/api/node/heartbeat", token,
                        "{\"nodeId\":\"" + nodeId + "\"}", 5000);
            } catch (Exception ignored) {}
            sleep(5000);
        }
    }

    private static String postJson(String url, String token, String body, int timeoutMs) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        c.setRequestProperty("X-Cluster-Token", token);
        c.setConnectTimeout(3000);
        c.setReadTimeout(timeoutMs);
        c.setDoOutput(true);
        try (OutputStream os = c.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        int code = c.getResponseCode();
        StringBuilder sb = new StringBuilder();
        try (InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream()) {
            if (is != null) {
                byte[] buf = new byte[4096];
                int n; while ((n = is.read(buf)) > 0) sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            }
        }
        if (code >= 400) throw new RuntimeException("HTTP " + code + ": " + sb);
        return sb.toString();
    }

    private static String getArg(String[] args, String key) {
        if (args == null) return null;
        String prefix = key + "=";
        for (String a : args) if (a.startsWith(prefix)) return a.substring(prefix.length());
        return null;
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private static String safeHost() {
        try { return java.net.InetAddress.getLocalHost().getHostAddress(); }
        catch (Exception e) { return "127.0.0.1"; }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}