package com.omni.business.assistance.sys.service.portscan;

import com.omni.business.assistance.sys.model.PortInfo;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Windows 端口采集：
 *   用 PowerShell 的 Get-NetTCPConnection / Get-NetUDPEndpoint 获取监听列表，
 *   顺便关联 Get-Process 拿进程名。
 *
 * 命令使用 EncodedCommand（UTF-16LE base64）传递，
 * 避免 SSH → cmd.exe → PowerShell 多层引号转义的坑。
 */
public class WindowsPortCollector implements PortCollector {

    private static final String PS_SCRIPT =
        "Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | ForEach-Object { " +
        "  $p = Get-Process -Id $_.OwningProcess -ErrorAction SilentlyContinue; " +
        "  'TCP|' + $_.LocalAddress + '|' + $_.LocalPort + '|' + $_.OwningProcess + '|' + $(if ($p) { $p.ProcessName } else { '' }) " +
        "}; " +
        "Get-NetUDPEndpoint -ErrorAction SilentlyContinue | ForEach-Object { " +
        "  $p = Get-Process -Id $_.OwningProcess -ErrorAction SilentlyContinue; " +
        "  'UDP|' + $_.LocalAddress + '|' + $_.LocalPort + '|' + $_.OwningProcess + '|' + $(if ($p) { $p.ProcessName } else { '' }) " +
        "}";

    @Override
    public String getOsType() { return "windows"; }

    @Override
    public List<PortInfo> collect(RemoteExec exec) throws Exception {
        String encoded = Base64.getEncoder()
                .encodeToString(PS_SCRIPT.getBytes(StandardCharsets.UTF_16LE));
        String cmd = "powershell -NoProfile -EncodedCommand " + encoded;

        String output = exec.exec(cmd);
        List<PortInfo> result = new ArrayList<>();
        if (output == null || output.trim().isEmpty()) return result;

        for (String line : output.split("\n")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            String[] parts = t.split("\\|", -1);
            if (parts.length < 5) continue;
            try {
                PortInfo p = new PortInfo();
                p.setProtocol(parts[0].trim());
                p.setAddress(parts[1].trim());
                p.setPort(Integer.parseInt(parts[2].trim()));
                p.setState("LISTEN");
                String pidStr = parts[3].trim();
                if (!pidStr.isEmpty()) {
                    try { p.setProcessPid(Long.parseLong(pidStr)); } catch (Exception ignored) {}
                }
                String procName = parts[4].trim();
                if (!procName.isEmpty()) p.setProcessName(procName);
                if (p.getPort() > 0) result.add(p);
            } catch (Exception ignored) {
                // 跳过无法解析的行
            }
        }
        return result;
    }
}