package com.omni.business.assistance.cluster.channel;

import com.omni.business.assistance.cluster.model.ClusterNode;
import com.omni.business.assistance.cluster.model.DeployRequest;
import com.omni.business.assistance.cluster.model.JdkInfo;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.io.*;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 主服务本机通道：直接用 ProcessBuilder。
 * 适用：目标机 == 主服务所在机器。
 */
public class LocalDeployChannel implements DeployChannel {
    private static final Logger log = LoggerFactory.getLogger(LocalDeployChannel.class);

    @Override public String getName() { return "local"; }

    @Override
    public JdkInfo checkJdk(DeployRequest req) {
        JdkInfo info = new JdkInfo();
        // 1) JAVA_HOME
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isEmpty()) {
            String p = javaHome + File.separator + "bin" + File.separator
                    + (isWindows() ? "java.exe" : "java");
            String v = readJavaVersion(p);
            if (v != null) info.getCandidates().add(new JdkInfo.Candidate(p, v));
        }
        // 2) PATH 里的 java
        String pathJava = findInPath();
        if (pathJava != null) {
            String v = readJavaVersion(pathJava);
            if (v != null) {
                boolean dup = info.getCandidates().stream()
                        .anyMatch(c -> c.getPath().equals(pathJava));
                if (!dup) info.getCandidates().add(new JdkInfo.Candidate(pathJava, v));
            }
        }
        // 3) 常见安装目录扫描
        scanCommonDirs(info);
        // 4) 当前 JVM 自身
        String self = System.getProperty("java.home") + File.separator + "bin"
                + File.separator + (isWindows() ? "java.exe" : "java");
        String selfV = readJavaVersion(self);
        if (selfV != null) {
            boolean dup = info.getCandidates().stream().anyMatch(c -> c.getPath().equals(self));
            if (!dup) info.getCandidates().add(new JdkInfo.Candidate(self, selfV));
        }
        // 默认选中第一个
        if (!info.getCandidates().isEmpty()) {
            info.setCurrentJavaPath(info.getCandidates().get(0).getPath());
            info.setCurrentVersion(info.getCandidates().get(0).getVersion());
        } else {
            info.setError("未在 PATH / JAVA_HOME / 常见目录中找到 java");
        }
        return info;
    }

    private void scanCommonDirs(JdkInfo info) {
        List<Path> roots = new ArrayList<>();
        if (isWindows()) {
            roots.add(Paths.get("C:/Program Files/Java"));
            roots.add(Paths.get("C:/Program Files/Eclipse Adoptium"));
            roots.add(Paths.get("C:/Program Files/Microsoft"));
        } else {
            roots.add(Paths.get("/usr/lib/jvm"));
            roots.add(Paths.get("/Library/Java/JavaVirtualMachines"));
            String sdkman = System.getProperty("user.home") + "/.sdkman/candidates/java";
            roots.add(Paths.get(sdkman));
        }
        for (Path r : roots) {
            if (!Files.isDirectory(r)) continue;
            try {
                Files.list(r).forEach(p -> {
                    Path java = p.resolve("bin").resolve(isWindows() ? "java.exe" : "java");
                    if (!Files.isRegularFile(java)) {
                        // macOS 结构：xxx/Contents/Home/bin/java
                        Path alt = p.resolve("Contents/Home/bin/java");
                        if (Files.isRegularFile(alt)) java = alt;
                        else return;
                    }
                    String v = readJavaVersion(java.toString());
                    if (v != null) info.getCandidates().add(new JdkInfo.Candidate(java.toString(), v));
                });
            } catch (Exception ignored) {}
        }
    }

    private String findInPath() {
        String path = System.getenv("PATH");
        if (path == null) return null;
        String exe = isWindows() ? "java.exe" : "java";
        for (String dir : path.split(File.pathSeparator)) {
            Path p = Paths.get(dir, exe);
            if (Files.isRegularFile(p)) return p.toString();
        }
        return null;
    }

    private String readJavaVersion(String javaExe) {
        try {
            ProcessBuilder pb = new ProcessBuilder(javaExe, "-version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line; while ((line = r.readLine()) != null) sb.append(line).append('\n');
            }
            p.waitFor();
            return parseVersion(sb.toString());
        } catch (Exception e) { return null; }
    }

    public static String parseVersion(String s) {
        if (s == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("version\\s+\"?(\\d+)(?:\\.(\\d+))?").matcher(s);
        if (!m.find()) return null;
        int major = Integer.parseInt(m.group(1));
        if (major == 1 && m.group(2) != null) major = Integer.parseInt(m.group(2));
        return String.valueOf(major);
    }

    @Override
    public int probePort(DeployRequest req, int fromPort, int count) {
        for (int p = fromPort; p < fromPort + count; p++) {
            try (ServerSocket ss = new ServerSocket()) {
                ss.setReuseAddress(false);
                ss.bind(new java.net.InetSocketAddress(p));
                return p;
            } catch (IOException ignore) {}
        }
        return -1;
    }

    @Override
    public DeployResult deploy(DeployRequest req, Path localJar, ClusterNode node,
                               String masterUrl, String masterToken) throws Exception {
        DeployResult r = new DeployResult();
        r.logs = new ArrayList<>();
        Path deployDir = Paths.get(req.getDeployDir()).toAbsolutePath().normalize();
        Files.createDirectories(deployDir);

        // 1) 拷贝 jar
        Path targetJar = deployDir.resolve(localJar.getFileName().toString());
        Files.copy(localJar, targetJar, StandardCopyOption.REPLACE_EXISTING);
        r.logs.add("jar 已拷贝到 " + targetJar);

        // 2) 构造命令
        String javaPath = req.getJavaPath() != null && !req.getJavaPath().isEmpty()
                ? req.getJavaPath() : "java";
        List<String> cmd = new ArrayList<>();
        cmd.add(javaPath);
        cmd.add("-jar");
        cmd.add(targetJar.toString());
        cmd.add("--server.port=" + node.getPort());
        cmd.add("--cluster.role=worker");
        cmd.add("--cluster.node.id=" + node.getNodeId());
        cmd.add("--cluster.master.url=" + masterUrl);
        cmd.add("--cluster.master.token=" + masterToken);
        cmd.add("--screenshot.assistant.enabled=false");
        cmd.add("--spring.profiles.active=worker");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(deployDir.toFile());
        pb.redirectErrorStream(true);
        Path logFile = deployDir.resolve("worker.log");
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
        Process p = pb.start();
        r.pid = p.pid();
        r.logs.add("worker 进程已启动，pid=" + p.pid());
        r.startCommand = String.join(" ", cmd);
        r.success = true;
        return r;
    }

    @Override
    public StopResult stop(ClusterNode node) {
        StopResult r = new StopResult();
        if (node.getPid() == null) {
            r.message = "无 pid，无法停止";
            return r;
        }
        try {
            ProcessHandle.of(node.getPid()).ifPresent(ph -> {
                ph.descendants().forEach(ProcessHandle::destroy);
                ph.destroy();
            });
            r.success = true;
        } catch (Exception e) {
            r.message = e.getMessage();
        }
        return r;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}