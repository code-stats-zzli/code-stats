package com.omni.business.assistance.sys.service.portscan;

import com.omni.business.assistance.sys.model.PortInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Linux 端口采集：
 *   优先 ss -tlnp / ss -ulnp
 *   回退 netstat -tlnp / netstat -ulnp
 * 两种输出格式在解析层统一处理。
 */
public class LinuxPortCollector implements PortCollector {

    private static final Pattern SS_PROC = Pattern.compile("\\(\\(\"([^\"]+)\"");
    private static final Pattern SS_PID  = Pattern.compile("pid=(\\d+)");

    @Override
    public String getOsType() { return "linux"; }

    @Override
    public List<PortInfo> collect(RemoteExec exec) throws Exception {
        List<PortInfo> result = new ArrayList<>();

        // 一条命令拿到 TCP + UDP，内部 ss 优先，失败回退 netstat
        String cmd =
            "(command -v ss >/dev/null 2>&1 && ss -tlnp 2>/dev/null || netstat -tlnp 2>/dev/null); " +
            "echo '===UDP==='; " +
            "(command -v ss >/dev/null 2>&1 && ss -ulnp 2>/dev/null || netstat -ulnp 2>/dev/null)";

        String output = exec.exec(cmd);
        if (output == null || output.trim().isEmpty()) return result;

        String[] sections = output.split("===UDP===", -1);
        if (sections.length >= 1) parseSection(sections[0], "TCP", result);
        if (sections.length >= 2) parseSection(sections[1], "UDP", result);
        return result;
    }

    private void parseSection(String text, String proto, List<PortInfo> result) {
        if (text == null) return;
        for (String line : text.split("\n")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            if (t.startsWith("State") || t.startsWith("Recv-Q")
                || t.startsWith("Active") || t.startsWith("Proto")) continue;

            PortInfo p = parseLine(t, proto);
            if (p != null) result.add(p);
        }
    }

    /**
     * 同时兼容 ss 与 netstat 的单行格式：
     *   ss:      LISTEN 0 128 0.0.0.0:22 0.0.0.0:* users:(("sshd",pid=1234,fd=3))
     *   netstat: tcp   0 0   0.0.0.0:22 0.0.0.0:* LISTEN  1234/sshd
     */
    private PortInfo parseLine(String line, String proto) {
        String[] parts = line.split("\\s+");
        if (parts.length < 4) return null;

        PortInfo p = new PortInfo();
        p.setProtocol(proto);
        String local;
        String processCol;

        String first = parts[0].toLowerCase();
        if ("listen".equals(first) || "unconn".equals(first)) {
            p.setState(parts[0].toUpperCase());
            local = parts[3];
            processCol = parts.length >= 6 ? parts[5] : "";
        } else if (first.startsWith("tcp") || first.startsWith("udp")) {
            local = parts[3];
            p.setState(parts.length > 5 ? parts[5].toUpperCase() : "LISTEN");
            processCol = parts.length >= 7 ? parts[6] : "";
        } else {
            return null;
        }

        parseLocalAddress(local, p);
        parseProcess(processCol, p);
        return p.getPort() > 0 ? p : null;
    }

    private void parseLocalAddress(String local, PortInfo p) {
        if (local == null || local.isEmpty()) return;
        int idx = local.lastIndexOf(':');
        if (idx <= 0 || idx >= local.length() - 1) return;
        String addr = local.substring(0, idx);
        String portStr = local.substring(idx + 1);
        try { p.setPort(Integer.parseInt(portStr)); } catch (Exception ignored) { return; }
        p.setAddress(addr);
    }

    private void parseProcess(String col, PortInfo p) {
        if (col == null || col.isEmpty()) return;
        Matcher mName = SS_PROC.matcher(col);
        if (mName.find()) {
            p.setProcessName(mName.group(1));
            Matcher mPid = SS_PID.matcher(col);
            if (mPid.find()) {
                try { p.setProcessPid(Long.parseLong(mPid.group(1))); } catch (Exception ignored) {}
            }
            return;
        }
        if (col.contains("/")) {
            String[] pp = col.split("/", 2);
            try { p.setProcessPid(Long.parseLong(pp[0].trim())); } catch (Exception ignored) {}
            p.setProcessName(pp[1].trim());
        }
    }
}