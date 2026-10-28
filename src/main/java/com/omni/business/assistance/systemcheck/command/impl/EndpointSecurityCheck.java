package com.omni.business.assistance.systemcheck.command.impl;

import com.omni.business.assistance.systemcheck.command.AbstractSystemCheckCommand;
import com.omni.business.assistance.systemcheck.command.SystemCheckContext;
import com.omni.business.assistance.systemcheck.model.Finding;

import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EndpointSecurityCheck extends AbstractSystemCheckCommand {

    private static final String CROSSNET_PAT = "(?i)TeamViewer|AnyDesk|ToDesk|向日葵|Sunlogin|RustDesk|灰鸽子|pcAnywhere|RealVNC|TightVNC|UltraVNC|frp|ngrok|nps|cpolar|花生壳|ZeroTier|Tailscale|Shadowsocks|v2ray|Clash|Trojan|WireGuard|OpenVPN|SoftEther|AnyConnect|360WiFi|猎豹WiFi";

    @Override public String getId() { return "endpoint-security"; }
    @Override public String getName() { return "跨网/无线/U盘"; }

    @Override
    public List<Finding> execute(SystemCheckContext ctx) {
        List<Finding> list = new ArrayList<>();
        checkCrossNetTools(ctx, list);
        checkWifi(ctx, list);
        checkUsb(ctx, list);
        return list;
    }

    private void checkCrossNetTools(SystemCheckContext ctx, List<Finding> list) {
        LinkedHashSet<String> tools = new LinkedHashSet<>();
        if (ctx.isWin()) {
            String uninst = exec("reg query \"HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\" /s 2>nul");
            for (String line : uninst.split("\n")) {
                if (line.contains("DisplayName") && line.matches("(?s).*" + CROSSNET_PAT + ".*")) {
                    tools.add(line.trim());
                }
            }
            for (String line : exec("tasklist /FO CSV").split("\n")) {
                if (line.matches("(?s).*" + CROSSNET_PAT + ".*")) tools.add("运行进程: " + line.trim());
            }
        } else {
            String cmd = "dpkg -l 2>/dev/null | grep -iE 'teamviewer|anydesk|todesk|frp|ngrok|nps|shadowsocks|v2ray|clash|wireguard|openvpn|zerotier|tailscale'; " +
                    "rpm -qa 2>/dev/null | grep -iE 'teamviewer|anydesk|todesk|frp|ngrok|nps|shadowsocks|v2ray|clash'";
            for (String line : exec(cmd).split("\n")) {
                if (!line.trim().isEmpty()) tools.add(line.trim());
            }
        }
        if (tools.isEmpty()) {
            list.add(new Finding("跨网工具", "跨网/远控/穿透工具检测", "合规", "信息",
                    "未检测到已知跨网/远控/穿透工具"));
        } else {
            list.add(new Finding("跨网工具", "检测到跨网/远控/穿透工具", "不合规", "高",
                    joinCap(tools, 20)));
        }
    }

    private void checkWifi(SystemCheckContext ctx, List<Finding> list) {
        if (ctx.isWin()) {
            String hw = exec("reg query \"HKLM\\SYSTEM\\CurrentControlSet\\Control\\Class\\{4d36e972-e325-11ce-bfc1-08002be10318}\" /s 2>nul");
            StringBuilder wdesc = new StringBuilder();
            for (String line : hw.split("\n")) {
                String t = line.trim();
                if (t.toLowerCase().contains("driverdesc")
                        && t.toLowerCase().matches(".*(wireless|802\\.11|wi-?fi|wlan).*")) {
                    int i = t.indexOf("REG_SZ");
                    String d = i >= 0 ? t.substring(i + 6).trim() : t;
                    if (d.length() > 120) d = d.substring(0, 120);
                    if (wdesc.length() > 0) wdesc.append("; ");
                    wdesc.append(d);
                }
            }
            if (wdesc.length() == 0) {
                list.add(new Finding("无线网卡", "WiFi 网卡检测", "合规", "信息", "未检测到 WiFi 无线网卡"));
            } else {
                list.add(new Finding("无线网卡", "检测到 WiFi 无线网卡", "不合规", "中",
                        "存在 WiFi 无线网卡: " + wdesc));
            }
        } else {
            String w = exec("nmcli -t -f DEVICE,TYPE device 2>/dev/null; iw dev 2>/dev/null | grep -i interface; ls /sys/class/net 2>/dev/null | grep -E '^wl'");
            if (w.trim().isEmpty()) {
                list.add(new Finding("无线网卡", "WiFi 网卡检测", "合规", "信息", "未检测到 WiFi 无线网卡"));
            } else {
                list.add(new Finding("无线网卡", "检测到 WiFi 无线网卡", "不合规", "中",
                        w.trim().replace("\n", "; ")));
            }
        }
    }

    private void checkUsb(SystemCheckContext ctx, List<Finding> list) {
        LinkedHashSet<String> usb = new LinkedHashSet<>();
        if (ctx.isWin()) {
            Matcher m = Pattern.compile("FriendlyName\\s+REG_SZ\\s+(.+)")
                    .matcher(exec("reg query \"HKLM\\SYSTEM\\CurrentControlSet\\Enum\\USBSTOR\" /s 2>nul"));
            while (m.find()) usb.add(m.group(1).trim());
        } else {
            for (String line : exec("lsusb 2>/dev/null | grep -iE 'mass storage|flash|usb disk'").split("\n")) {
                if (!line.trim().isEmpty()) usb.add(line.trim());
            }
            for (String line : exec("ls -1 /media /run/media 2>/dev/null").split("\n")) {
                if (!line.trim().isEmpty()) usb.add("挂载 " + line.trim());
            }
        }
        if (usb.isEmpty()) {
            list.add(new Finding("U盘使用", "USB 存储设备使用记录", "合规", "信息",
                    "未发现 U 盘/USB 存储设备使用记录"));
        } else {
            list.add(new Finding("U盘使用", "检测到 U 盘/USB 存储使用记录", "信息", "中",
                    joinCap(usb, 20)));
        }
    }

    private String joinCap(Collection<String> c, int max) {
        List<String> l = new ArrayList<>(c);
        int n = Math.min(l.size(), max);
        String s = String.join("; ", l.subList(0, n));
        if (l.size() > max) s += "; 等共 " + l.size() + " 项";
        return s.length() > 800 ? s.substring(0, 800) + "..." : s;
    }
}