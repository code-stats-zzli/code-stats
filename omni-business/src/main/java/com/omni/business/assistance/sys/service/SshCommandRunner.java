package com.omni.business.assistance.sys.service;

import com.omni.business.assistance.sys.model.SshConnection;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * SSH / SCP 命令执行器。
 * 只依赖系统内置 ssh / scp 客户端（Linux 默认、Windows 10+ 内置 OpenSSH Client）。
 */
public class SshCommandRunner {
    private static final Logger log = LoggerFactory.getLogger(SshCommandRunner.class);

    public static class Result {
        public int exitCode;
        public String stdout = "";
        public String stderr = "";
        public long durationMs;
        public boolean timeout;
        public String command;
    }

    public Result exec(SshConnection c, String remoteCmd, int timeoutSec) throws Exception {
        List<String> cmd = buildSshCommand(c, remoteCmd);
        return run(cmd, null, timeoutSec);
    }

    public Result scpUpload(SshConnection c, Path local, String remotePath, int timeoutSec) throws Exception {
        List<String> cmd = buildScpBase(c);
        cmd.add(local.toString());
        cmd.add(c.getUsername() + "@" + c.getHost() + ":" + remotePath);
        return run(cmd, null, timeoutSec);
    }

    public Result scpDownload(SshConnection c, String remotePath, Path local, int timeoutSec) throws Exception {
        List<String> cmd = buildScpBase(c);
        cmd.add(c.getUsername() + "@" + c.getHost() + ":" + remotePath);
        cmd.add(local.toString());
        return run(cmd, null, timeoutSec);
    }

    // ==================== 内部 ====================

    private List<String> buildSshCommand(SshConnection c, String remoteCmd) {
        List<String> cmd = new ArrayList<>();
        cmd.add("ssh");
        cmd.add("-o"); cmd.add("StrictHostKeyChecking=no");
        cmd.add("-o"); cmd.add("UserKnownHostsFile=" + (isWindows() ? "NUL" : "/dev/null"));
        cmd.add("-o"); cmd.add("ConnectTimeout=10");
        cmd.add("-p"); cmd.add(String.valueOf(c.getPort()));
        if (c.getKeyPath() != null && !c.getKeyPath().trim().isEmpty()) {
            cmd.add("-i"); cmd.add(c.getKeyPath().trim());
        }
        cmd.add(c.getUsername() + "@" + c.getHost());
        cmd.add(remoteCmd);
        return cmd;
    }

    private List<String> buildScpBase(SshConnection c) {
        List<String> cmd = new ArrayList<>();
        cmd.add("scp");
        cmd.add("-o"); cmd.add("StrictHostKeyChecking=no");
        cmd.add("-o"); cmd.add("UserKnownHostsFile=" + (isWindows() ? "NUL" : "/dev/null"));
        cmd.add("-o"); cmd.add("ConnectTimeout=10");
        cmd.add("-P"); cmd.add(String.valueOf(c.getPort()));
        if (c.getKeyPath() != null && !c.getKeyPath().trim().isEmpty()) {
            cmd.add("-i"); cmd.add(c.getKeyPath().trim());
        }
        return cmd;
    }

    private Result run(List<String> cmd, String stdin, int timeoutSec) throws Exception {
        Result r = new Result();
        r.command = String.join(" ", cmd);
        long t0 = System.currentTimeMillis();
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        Process p = pb.start();

        // 写 stdin 后立即关闭，避免远程命令挂起
        try (OutputStream os = p.getOutputStream()) {
            if (stdin != null) os.write(stdin.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}

        // 并发读 stdout / stderr 防止缓冲区满阻塞
        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        Thread tOut = startReader(p.getInputStream(), outBuf);
        Thread tErr = startReader(p.getErrorStream(), errBuf);

        boolean finished = p.waitFor(Math.max(1, timeoutSec), TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            r.timeout = true;
            r.exitCode = -1;
        } else {
            r.exitCode = p.exitValue();
        }
        try { tOut.join(2000); } catch (InterruptedException ignored) {}
        try { tErr.join(2000); } catch (InterruptedException ignored) {}
        r.stdout = new String(outBuf.toByteArray(), StandardCharsets.UTF_8);
        r.stderr = new String(errBuf.toByteArray(), StandardCharsets.UTF_8);
        r.durationMs = System.currentTimeMillis() - t0;
        if (r.exitCode != 0) {
            log.warn("ssh/scp 命令失败 exitCode={} cmd={} err={}",
                    r.exitCode, r.command, r.stderr);
        }
        return r;
    }

    private Thread startReader(InputStream in, ByteArrayOutputStream buf) {
        Thread t = new Thread(() -> {
            try (InputStream is = in) {
                byte[] tmp = new byte[4096];
                int n;
                while ((n = is.read(tmp)) > 0) buf.write(tmp, 0, n);
            } catch (IOException ignored) {}
        });
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}