package com.omni.business.assistance.systemcheck.command.impl;

import com.omni.business.assistance.systemcheck.command.AbstractSystemCheckCommand;
import com.omni.business.assistance.systemcheck.command.SystemCheckContext;
import com.omni.business.assistance.systemcheck.config.CheckConfig;
import com.omni.business.assistance.systemcheck.model.Finding;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EndpointSecurityCheck extends AbstractSystemCheckCommand {

    @Override public String getId() { return "endpoint-security"; }
    @Override public String getName() { return "跨网/无线/U盘"; }
    @Override public int getEstimatedSeconds(CheckConfig config) { return 5; }

    @Override
    public List<Map<String, Object>> getConfigFields(CheckConfig config) {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(field("crossNetPattern", "跨网/远控/穿透工具正则", "text",
                config.getModuleString("endpoint-security", "crossNetPattern", defaultCrossNet()),
                "命中此正则的工具/进程判为违规；Java 正则"));
        list.add(field("wifiPattern", "WiFi 网卡识别正则", "text",
                config.getModuleString("endpoint-security", "wifiPattern", "(?i)wireless|802\\.11|wi-?fi|wlan"),
                "用于识别 WiFi 无线网卡；Java 正则"));
        return list;
    }

    @Override
    public List<Finding> execute(SystemCheckContext ctx) throws Exception {
        List<Finding> list = new ArrayList<>();
        String crossNetPattern = ctx.getConfig().getModuleString("endpoint-security",
                "crossNetPattern", defaultCrossNet());
        String wifiPattern = ctx.getConfig().getModuleString("endpoint-security",
                "wifiPattern", "(?i)wireless|802\\.11|wi-?fi|wlan");

        checkCrossNetTools(ctx, list, crossNetPattern);
        checkWifi(ctx, list, wifiPattern);
        checkUsb(ctx, list);

        if (ctx.isCancelled()) {
            list.add(0, new Finding("跨网/无线/U盘", "扫描已取消", "信息", "信息", "用户中止扫描"));
        }
        return list;
    }

    private void checkCrossNetTools(SystemCheckContext ctx, List<Finding> list, String pattern) {
        LinkedHashSet<String> tools = new LinkedHashSet<>();
        if (ctx.isWin()) {
            String uninst = exec("reg query \"HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\" /s 2>nul");
            for (String line : uninst.split("\n")) {
                if (line.contains("DisplayName") && line.matches("(?s).*" + pattern + ".*")) {
                    tools.add(line.trim());
                }
            }
            for (String line : exec("tasklist /FO CSV").split("\n")) {
                if (line.matches("(?s).*" + pattern + ".*")) tools.add("运行进程: " + line.trim());
            }
        } else {
            for (String line : exec("ps -eo comm 2>/dev/null; dpkg -l 2>/dev/null; rpm -qa 2>/dev/null").split("\n")) {
                if (!line.trim().isEmpty() && line.matches("(?s).*" + pattern + ".*")) {
                    tools.add(line.trim());
                }
            }
        }
        if (tools.isEmpty()) {
            list.add(new Finding("跨网工具", "跨网/远控/穿透工具检测", "合规", "信息",
                    "未检测到已知跨网/远控/穿透工具"));
        } else {
            list.add(new Finding("跨网工具", "检测到跨网/远控/穿透工具", "不合规", "高", joinCap(tools, 20)));
        }
    }

    private void checkWifi(SystemCheckContext ctx, List<Finding> list, String wifiPattern) {
        if (ctx.isWin()) {
            String hw = exec("reg query \"HKLM\\SYSTEM\\CurrentControlSet\\Control\\Class\\{4d36e972-e325-11ce-bfc1-08002be10318}\" /s 2>nul");
            StringBuilder wdesc = new StringBuilder();
            for (String line : hw.split("\n")) {
                String t = line.trim();
                if (t.toLowerCase().contains("driverdesc") && t.toLowerCase().matches(".*" + wifiPattern + ".*")) {
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
            list.add(new Finding("U盘使用", "检测到 U 盘/USB 存储使用记录", "信息", "中", joinCap(usb, 20)));
        }
    }

    private String joinCap(Collection<String> c, int max) {
        List<String> l = new ArrayList<>(c);
        int n = Math.min(l.size(), max);
        String s = String.join("; ", l.subList(0, n));
        if (l.size() > max) s += "; 等共 " + l.size() + " 项";
        return s.length() > 800 ? s.substring(0, 800) + "..." : s;
    }

    private static String defaultCrossNet() {
        return "(?i)TeamViewer|AnyDesk|ToDesk|向日葵|Sunlogin|RustDesk|灰鸽子|pcAnywhere|RealVNC|TightVNC|UltraVNC|frp|ngrok|nps|cpolar|花生壳|nat123|ZeroTier|Tailscale|Shadowsocks|ShadowRocket|v2ray|Clash|Trojan|WireGuard|OpenVPN|SoftEther|AnyConnect|360WiFi|猎豹WiFi|共享精灵|Connectify|MyPublicWiFi|蓝灯|Lantern|ExpressVPN|Surge|Quantumult|Proxifier|SocksCap|FreeCap|Proxyee";
    }

    private Map<String, Object> field(String key, String label, String type, Object value, String hint) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key); m.put("label", label); m.put("type", type);
        m.put("value", value); m.put("hint", hint);
        return m;
    }
}