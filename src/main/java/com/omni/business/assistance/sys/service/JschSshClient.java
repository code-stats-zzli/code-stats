package com.omni.business.assistance.sys.service;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.omni.business.assistance.sys.model.SshConnection;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * 基于 JSch 的 SSH 客户端。
 *
 * <p>本类不负责保存密码，由 {@link SshConnectionService} 从
 * {@link SshCredentialCache} 取出后 set 进对象再传入。</p>
 */
public class JschSshClient {
    private static final Logger log = LoggerFactory.getLogger(JschSshClient.class);

    public static class Result {
        public int exitCode;
        public String stdout = "";
        public String stderr = "";
        public long durationMs;
        public boolean timeout;
        public String command;
    }

    public static Session openSession(SshConnection c) throws JSchException {
        if (c == null) throw new JSchException("连接配置为空");
        boolean hasKey = c.getKeyPath() != null && !c.getKeyPath().trim().isEmpty();
        boolean hasPwd = c.getPassword() != null && !c.getPassword().isEmpty();
        if (!hasKey && !hasPwd) {
            throw new JSchException("未配置密码或私钥，无法认证。请至少填写「密码」或「私钥路径」其中之一。");
        }

        JSch jsch = new JSch();
        if (hasKey) {
            try {
                jsch.addIdentity(c.getKeyPath().trim());
            } catch (JSchException e) {
                if (!hasPwd) throw e;
                log.warn("加载私钥失败，将回退到密码认证: {}", e.getMessage());
            }
        }

        Session session = jsch.getSession(c.getUsername(), c.getHost(), c.getPort());
        if (hasPwd) session.setPassword(c.getPassword());

        Properties cfg = new Properties();
        cfg.put("StrictHostKeyChecking", "no");
        cfg.put("PreferredAuthentications",
                hasKey && hasPwd ? "publickey,password"
                        : (hasKey ? "publickey" : "password"));
        session.setConfig(cfg);
        session.setTimeout(20000);
        session.connect(20000);
        return session;
    }

    public static Result exec(SshConnection c, String remoteCmd, int timeoutSec) throws Exception {
        long t0 = System.currentTimeMillis();
        Session session = null;
        ChannelExec channel = null;
        try {
            session = openSession(c);
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(remoteCmd);
            channel.setInputStream(null);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            channel.setOutputStream(out);
            channel.setErrStream(err);

            channel.connect(Math.max(1000, timeoutSec * 1000));

            long deadline = t0 + timeoutSec * 1000L;
            while (!channel.isClosed()) {
                if (System.currentTimeMillis() > deadline) {
                    channel.disconnect();
                    Result r = new Result();
                    r.timeout = true;
                    r.exitCode = -1;
                    r.stdout = out.toString(StandardCharsets.UTF_8.name());
                    r.stderr = err.toString(StandardCharsets.UTF_8.name());
                    r.durationMs = System.currentTimeMillis() - t0;
                    r.command = remoteCmd;
                    log.warn("JSch exec 超时: cmd={}, 耗时={}ms", remoteCmd, r.durationMs);
                    return r;
                }
                Thread.sleep(50);
            }

            Result r = new Result();
            r.exitCode = channel.getExitStatus();
            r.stdout = out.toString(StandardCharsets.UTF_8.name());
            r.stderr = err.toString(StandardCharsets.UTF_8.name());
            r.durationMs = System.currentTimeMillis() - t0;
            r.command = remoteCmd;
            if (r.exitCode != 0) {
                log.warn("JSch exec 失败 exitCode={}, cmd={}, stderr={}",
                        r.exitCode, remoteCmd, r.stderr);
            }
            return r;
        } finally {
            if (channel != null) channel.disconnect();
            if (session != null) session.disconnect();
        }
    }

    public static void upload(SshConnection c, byte[] data, String remotePath, int timeoutSec) throws Exception {
        Session session = null;
        ChannelSftp sftp = null;
        try {
            session = openSession(c);
            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(Math.max(1000, timeoutSec * 1000));
            try (InputStream in = new ByteArrayInputStream(data == null ? new byte[0] : data)) {
                sftp.put(in, remotePath);
            }
        } finally {
            if (sftp != null) sftp.disconnect();
            if (session != null) session.disconnect();
        }
    }

    public static byte[] download(SshConnection c, String remotePath, int timeoutSec) throws Exception {
        Session session = null;
        ChannelSftp sftp = null;
        try {
            session = openSession(c);
            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(Math.max(1000, timeoutSec * 1000));
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            sftp.get(remotePath, bos);
            return bos.toByteArray();
        } finally {
            if (sftp != null) sftp.disconnect();
            if (session != null) session.disconnect();
        }
    }

    /**
     * 两步法探测远端 OS 类型。修复版：不吞异常、不默认 windows。
     */
    public static String detectOsType(SshConnection c, int timeoutSec) throws Exception {
        if (c == null) throw new RuntimeException("连接配置为空");

        Result r = exec(c, "uname -s 2>/dev/null", timeoutSec);
        if (r.timeout) {
            throw new RuntimeException("探测 OS 超时：uname -s 执行超过 " + timeoutSec + " 秒");
        }
        if (r.exitCode == 0 && r.stdout != null) {
            String os = r.stdout.trim().toLowerCase();
            if (os.contains("linux") || os.contains("darwin")) return "linux";
        }

        Result r2 = exec(c, "ver", Math.min(timeoutSec, 10));
        if (r2.timeout) {
            throw new RuntimeException("探测 OS 超时：ver 执行超过 10 秒");
        }
        if (r2.exitCode == 0 && r2.stdout != null
                && r2.stdout.toLowerCase().contains("windows")) {
            return "windows";
        }

        throw new RuntimeException(
                "无法探测远端 OS 类型。"
                        + " uname exitCode=" + r.exitCode
                        + ", uname stdout=" + safe(r.stdout)
                        + ", uname stderr=" + safe(r.stderr)
                        + ", ver exitCode=" + r2.exitCode
                        + ", ver stdout=" + safe(r2.stdout)
                        + ", ver stderr=" + safe(r2.stderr));
    }

    /**
     * 测试连接：认证 + exec 通道 + OS 探测，三者都成功才算成功。
     */
    public static String testConnection(SshConnection c, int timeoutSec) throws Exception {
        if (c == null) throw new RuntimeException("连接配置为空");

        boolean hasKey = c.getKeyPath() != null && !c.getKeyPath().trim().isEmpty();
        boolean hasPwd = c.getPassword() != null && !c.getPassword().isEmpty();
        if (!hasKey && !hasPwd) {
            throw new RuntimeException(
                    "未配置密码或私钥，无法认证。请至少填写「密码」或「私钥路径」其中之一。");
        }

        Result echo = exec(c, "echo __SSH_OK__", Math.min(timeoutSec, 10));
        if (echo.timeout) {
            throw new RuntimeException("SSH 命令执行超时：echo 超过 " + Math.min(timeoutSec, 10) + " 秒");
        }
        if (echo.exitCode != 0 || echo.stdout == null || !echo.stdout.contains("__SSH_OK__")) {
            throw new RuntimeException(
                    "SSH 命令执行失败，exitCode=" + echo.exitCode
                            + ", stdout=" + safe(echo.stdout)
                            + ", stderr=" + safe(echo.stderr));
        }

        return detectOsType(c, timeoutSec);
    }

    private static String safe(String s) {
        if (s == null) return "";
        s = s.trim();
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }
}