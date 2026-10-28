package com.omni.business.assistance.systemcheck.command;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** 检查命令抽象基类：提供公共的 shell 执行、超时控制 */
public abstract class AbstractSystemCheckCommand implements SystemCheckCommand {

    protected static boolean IS_WIN = System.getProperty("os.name", "")
            .toLowerCase().contains("win");

    /** 执行 shell 命令，带超时和输出大小限制 */
    protected String exec(String cmd) { return execStatic(cmd); }

    public static String execStatic(String cmd) {
        try {
            String[] shell = IS_WIN ? new String[]{"cmd", "/c", cmd}
                    : new String[]{"/bin/sh", "-c", cmd};
            Process p = new ProcessBuilder(shell).redirectErrorStream(true).start();
            Charset cs = IS_WIN ? Charset.forName("GBK") : StandardCharsets.UTF_8;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), cs))) {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[8192];
                int n;
                long start = System.currentTimeMillis();
                while ((n = br.read(buf)) > 0) {
                    sb.append(buf, 0, n);
                    if (System.currentTimeMillis() - start > 15000) break;
                    if (sb.length() > 400000) break;
                }
                p.waitFor(3, TimeUnit.SECONDS);
                return sb.toString();
            }
        } catch (Exception e) {
            return "";
        }
    }

    /** 文件名截断 */
    protected static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : "..." + s.substring(s.length() - max + 3);
    }
}