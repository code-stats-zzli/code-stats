package com.omni.business.assistance.systemcheck.command.impl;

import com.omni.business.assistance.systemcheck.command.AbstractSystemCheckCommand;
import com.omni.business.assistance.systemcheck.command.SystemCheckContext;
import com.omni.business.assistance.systemcheck.model.Finding;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PortCheck extends AbstractSystemCheckCommand {

    @Override public String getId() { return "port"; }
    @Override public String getName() { return "高危端口"; }

    @Override
    public List<Finding> execute(SystemCheckContext ctx) {
        List<Finding> list = new ArrayList<>();
        Map<String, PortInfo> listen = getListeningPorts(ctx.isWin());
        List<String[]> blockRules = getBlockRules(ctx.isWin());

        // 全部监听端口（信息）
        List<String> all = new ArrayList<>();
        for (PortInfo p : listen.values()) {
            all.add(p.proto + " " + p.addr + ":" + p.port
                    + (p.proc.isEmpty() ? "" : " -> " + p.proc));
        }
        Collections.sort(all);
        list.add(new Finding("高危端口", "监听端口范围(全部)", "信息", "信息",
                "共 " + all.size() + " 个监听端口: " + String.join("; ", all)));

        // 高危端口比对
        for (String port : ctx.getConfig().getRiskPorts()) {
            PortInfo tcp = listen.get(port + "/TCP");
            PortInfo udp = listen.get(port + "/UDP");
            if (tcp == null && udp == null) continue;

            StringBuilder d = new StringBuilder();
            boolean blocked = true;
            if (tcp != null) {
                d.append("TCP ").append(tcp.addr).append(":").append(port)
                        .append("->").append(tcp.proc);
                if (!covers(blockRules, "TCP", port)) blocked = false;
            }
            if (udp != null) {
                if (d.length() > 0) d.append("; ");
                d.append("UDP ").append(udp.addr).append(":").append(port)
                        .append("->").append(udp.proc);
                if (!covers(blockRules, "UDP", port)) blocked = false;
            }
            if (!blocked) {
                boolean req = ctx.getConfig().getRiskReqPorts().contains(port);
                list.add(new Finding("高危端口", "端口 " + port + " 监听(未入站阻断)",
                        "不合规", req ? "高" : "中", d.toString()));
            }
        }
        return list;
    }

    private Map<String, PortInfo> getListeningPorts(boolean isWin) {
        Map<String, PortInfo> map = new LinkedHashMap<>();
        if (isWin) {
            Map<String, String> procs = new HashMap<>();
            for (String line : exec("tasklist /FO CSV").split("\n")) {
                String[] c = line.replace("\"", "").split(",");
                if (c.length >= 2 && c[1].matches("\\d+")) procs.put(c[1], c[0]);
            }
            for (String line : exec("netstat -ano").split("\n")) {
                String t = line.trim();
                if (t.startsWith("TCP") && t.contains("LISTENING")) {
                    String[] p = t.split("\\s+");
                    if (p.length >= 5) {
                        String local = p[1];
                        map.put(port(local) + "/TCP",
                                new PortInfo("TCP", addr(local), port(local), procs.getOrDefault(p[4], p[4])));
                    }
                } else if (t.startsWith("UDP")) {
                    String[] p = t.split("\\s+");
                    if (p.length >= 4) {
                        String local = p[1];
                        map.put(port(local) + "/UDP",
                                new PortInfo("UDP", addr(local), port(local), procs.getOrDefault(p[3], p[3])));
                    }
                }
            }
        } else {
            parseSs(exec("ss -tlnp 2>/dev/null"), "TCP", map);
            parseSs(exec("ss -ulnp 2>/dev/null"), "UDP", map);
            if (map.isEmpty()) {
                parseSs(exec("netstat -tlnp 2>/dev/null"), "TCP", map);
                parseSs(exec("netstat -ulnp 2>/dev/null"), "UDP", map);
            }
        }
        return map;
    }

    private void parseSs(String out, String proto, Map<String, PortInfo> map) {
        for (String line : out.split("\n")) {
            String[] p = line.trim().split("\\s+");
            if (p.length >= 4) {
                String local = p[3];
                map.put(port(local) + "/" + proto,
                        new PortInfo(proto, addr(local), port(local), ""));
            }
        }
    }

    private List<String[]> getBlockRules(boolean isWin) {
        List<String[]> rules = new ArrayList<>();
        if (isWin) {
            String reg = exec("reg query \"HKLM\\SYSTEM\\CurrentControlSet\\Services\\SharedAccess\\Parameters\\FirewallPolicy\\FirewallRules\" /s");
            for (String line : reg.split("\n")) {
                int idx = line.indexOf("REG_SZ");
                if (idx < 0) continue;
                String v = line.substring(idx + 6).trim();
                if (v.contains("Dir=In") && v.contains("Active=TRUE") && v.contains("Action=Block")) {
                    String lport = extract(v, "LPort=");
                    String proto = extract(v, "Protocol=");
                    String[] protos = proto.equals("6") ? new String[]{"TCP"}
                            : proto.equals("17") ? new String[]{"UDP"}
                              : new String[]{"TCP", "UDP"};
                    for (String pn : protos) rules.add(new String[]{pn, lport});
                }
            }
        } else {
            String ipt = exec("iptables-save 2>/dev/null; firewall-cmd --list-all 2>/dev/null");
            for (String line : ipt.split("\n")) {
                if (line.contains("--dport") && (line.contains("DROP") || line.contains("REJECT"))) {
                    Matcher m = Pattern.compile("--dport\\s+([0-9]+)(?::([0-9]+))?").matcher(line);
                    if (m.find()) {
                        String lp = m.group(2) != null ? m.group(1) + "-" + m.group(2) : m.group(1);
                        rules.add(new String[]{"TCP", lp});
                        rules.add(new String[]{"UDP", lp});
                    }
                }
            }
        }
        return rules;
    }

    private String extract(String s, String key) {
        int i = s.indexOf(key);
        if (i < 0) return "";
        int j = s.indexOf('|', i);
        return j > i ? s.substring(i + key.length(), j) : s.substring(i + key.length());
    }

    private boolean covers(List<String[]> rules, String proto, String port) {
        int p = Integer.parseInt(port);
        for (String[] r : rules) {
            if (!r[0].equals(proto)) continue;
            String lp = r[1].trim();
            if (lp.isEmpty() || lp.equalsIgnoreCase("Any")) return true;
            for (String item : lp.split(",")) {
                item = item.trim();
                Matcher m = Pattern.compile("^(\\d+)[-:](\\d+)$").matcher(item);
                if (m.matches()) {
                    if (p >= Integer.parseInt(m.group(1)) && p <= Integer.parseInt(m.group(2))) return true;
                } else if (item.matches("\\d+")) {
                    if (p == Integer.parseInt(item)) return true;
                }
            }
        }
        return false;
    }

    private static String addr(String local) {
        int i = local.lastIndexOf(':');
        return i > 0 ? local.substring(0, i) : local;
    }
    private static String port(String local) {
        int i = local.lastIndexOf(':');
        return i >= 0 && i < local.length() - 1 ? local.substring(i + 1) : "";
    }

    private static class PortInfo {
        String proto, addr, port, proc;
        PortInfo(String a, String b, String c, String d) { proto = a; addr = b; port = c; proc = d; }
    }
}