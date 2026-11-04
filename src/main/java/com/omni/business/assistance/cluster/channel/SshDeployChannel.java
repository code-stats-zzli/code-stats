package com.omni.business.assistance.cluster.channel;

import com.omni.business.assistance.cluster.model.ClusterNode;
import com.omni.business.assistance.cluster.model.DeployRequest;
import com.omni.business.assistance.cluster.model.JdkInfo;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * SSH 通道：主服务通过系统 ssh/scp 命令连到目标机执行。
 * 需要目标机开启 SSH（Windows 10/11/Server 2019+ 内置 OpenSSH Server）。
 *
 * 认证方式：
 *   优先使用密钥（sshKeyPath），可走 `ssh -i key`
 *   Linux 主服务若装了 sshpass，可以用密码（可选）
 *   Windows 主服务若无 sshpass，请使用密钥
 */
public class SshDeployChannel implements DeployChannel {
    private static final Logger log = LoggerFactory.getLogger(SshDeployChannel.class);
    private static final int TIMEOUT_SEC = 30;

    @Override public String getName() { return "ssh"; }

    @Override
    public JdkInfo checkJdk(DeployRequest req) throws Exception {
        JdkInfo info = new JdkInfo();
        // 检测 java -version
        String out = ssh(req, "java -version 2>&1; echo '---'; which java 2>/dev/null; echo '---'; echo $JAVA_HOME");
        if (out == null) { info.setError("SSH 连接失败"); return info; }
        String[] parts = out.split("---");
        String ver = LocalDeployChannel.parseVersion(parts[0]);
        String which = parts.length > 1 ? parts[1].trim() : "";
        String jh = parts.length > 2 ? parts[2].trim() : "";
        if (ver != null && !which.isEmpty()) {
            info.getCandidates().add(new JdkInfo.Candidate(which, ver));
        }
        if (ver != null && !jh.isEmpty()) {
            String jp = jh + "/bin/java";
            info.getCandidates().add(new JdkInfo.Candidate(jp, ver));
        }
        // 扫描常见目录
        String listCmd = "ls -d /usr/lib/jvm/*/bin/java 2>/dev/null; "
                + "ls -d /opt/*/bin/java 2>/dev/null";
        String listOut = ssh(req, listCmd);
        if (listOut != null) {
            for (String line : listOut.split("\n")) {
                String p = line.trim();
                if (p.isEmpty() || !p.endsWith("/java")) continue;
                String v = LocalDeployChannel.parseVersion(ssh(req, p + " -version 2>&1"));
                if (v != null) info.getCandidates().add(new JdkInfo.Candidate(p, v));
            }
        }
        if (!info.getCandidates().isEmpty()) {
            info.setCurrentJavaPath(info.getCandidates().get(0).getPath());
            info.setCurrentVersion(info.getCandidates().get(0).getVersion());
        } else {
            info.setError("目标机未找到可用 java");
        }
        return info;
    }

    @Override
    public int probePort(DeployRequest req, int fromPort, int count) throws Exception {
        // 在目标机上用 bash 的 /dev/tcp 快速探测
        for (int p = fromPort; p < fromPort + count; p++) {
            String cmd = "(echo > /dev/tcp/127.0.0.1/" + p + ") >/dev/null 2>&1 "
                    + "&& echo OCCUPIED || echo FREE";
            String out = ssh(req, cmd);
            if (out != null && out.trim().contains("FREE")) return p;
        }
        return -1;
    }

    @Override
    public DeployResult deploy(DeployRequest req, Path localJar, ClusterNode node,
                               String masterUrl, String masterToken) throws Exception {
        DeployResult r = new DeployResult();
        r.logs = new ArrayList<>();

        String remoteJar = req.getDeployDir() + "/" + localJar.getFileName().toString();

        // 1) 创建部署目录
        runSsh(req, "mkdir -p '" + req.getDeployDir() + "'", r.logs);

        // 2) scp 上传
        List<String> scpCmd = new ArrayList<>();
        scpCmd.add("scp");
        scpCmd.add("-o"); scpCmd.add("StrictHostKeyChecking=no");
        scpCmd.add("-P"); scpCmd.add(String.valueOf(req.getSshPort()));
        if (req.getSshKeyPath() != null && !req.getSshKeyPath().isEmpty()) {
            scpCmd.add("-i"); scpCmd.add(req.getSshKeyPath());
        }
        scpCmd.add(localJar.toString());
        scpCmd.add(req.getSshUser() + "@" + req.getHost() + ":" + remoteJar);
        runProcess(scpCmd, r.logs, 120);
        r.logs.add("jar 已上传到 " + req.getHost() + ":" + remoteJar);

        // 3) 启动 Worker
        String javaPath = req.getJavaPath() != null && !req.getJavaPath().isEmpty()
                ? req.getJavaPath() : "java";
        String startCmd = String.format(
                "cd '%s' && nohup %s -jar %s " +
                        "--server.port=%d " +
                        "--cluster.role=worker " +
                        "--cluster.node.id=%s " +
                        "--cluster.master.url=%s " +
                        "--cluster.master.token=%s " +
                        "--screenshot.assistant.enabled=false " +
                        "--spring.profiles.active=worker " +
                        "> worker.log 2>&1 & echo $!",
                req.getDeployDir(), javaPath, remoteJar,
                node.getPort(), node.getNodeId(), masterUrl, masterToken);

        String pidOut = ssh(req, startCmd);
        if (pidOut != null && pidOut.trim().matches("\\d+")) {
            r.pid = Long.parseLong(pidOut.trim());
        }
        r.startCommand = startCmd;
        r.success = true;
        r.logs.add("worker 已启动，pid=" + r.pid);
        return r;
    }

    @Override
    public StopResult stop(ClusterNode node) throws Exception {
        StopResult r = new StopResult();
        if (node.getHost() == null) { r.message = "缺少 host"; return r; }
        // 通过 nodeId 匹配 pkill
        DeployRequest req = new DeployRequest();
        req.setHost(node.getHost());
        req.setSshUser(node.getSshUser());
        req.setSshKeyPath(node.getSshKeyPath());
        req.setSshPort(node.getSshPort());
        String cmd = "pkill -f 'cluster.node.id=" + node.getNodeId() + "' || true";
        ssh(req, cmd);
        r.success = true;
        return r;
    }

    // ============ 工具方法 ============

    private String ssh(DeployRequest req, String remoteCmd) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add("ssh");
        cmd.add("-o"); cmd.add("StrictHostKeyChecking=no");
        cmd.add("-o"); cmd.add("ConnectTimeout=10");
        cmd.add("-p"); cmd.add(String.valueOf(req.getSshPort()));
        if (req.getSshKeyPath() != null && !req.getSshKeyPath().isEmpty()) {
            cmd.add("-i"); cmd.add(req.getSshKeyPath());
        }
        cmd.add(req.getSshUser() + "@" + req.getHost());
        cmd.add(remoteCmd);
        List<String> logs = new ArrayList<>();
        return runProcess(cmd, logs, TIMEOUT_SEC);
    }

    private void runSsh(DeployRequest req, String remoteCmd, List<String> logs) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add("ssh");
        cmd.add("-o"); cmd.add("StrictHostKeyChecking=no");
        cmd.add("-p"); cmd.add(String.valueOf(req.getSshPort()));
        if (req.getSshKeyPath() != null && !req.getSshKeyPath().isEmpty()) {
            cmd.add("-i"); cmd.add(req.getSshKeyPath());
        }
        cmd.add(req.getSshUser() + "@" + req.getHost());
        cmd.add(remoteCmd);
        runProcess(cmd, logs, TIMEOUT_SEC);
    }

    private String runProcess(List<String> cmd, List<String> logs, int timeoutSec) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
                if (logs != null) logs.add(line);
            }
        }
        if (!p.waitFor(timeoutSec, java.util.concurrent.TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IOException("SSH 命令超时: " + String.join(" ", cmd));
        }
        if (p.exitValue() != 0) {
            throw new IOException("SSH 命令失败(exit=" + p.exitValue() + "): "
                    + String.join(" ", cmd) + "\n" + sb);
        }
        return sb.toString();
    }
}