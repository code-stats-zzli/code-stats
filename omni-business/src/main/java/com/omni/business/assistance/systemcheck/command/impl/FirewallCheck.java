package com.omni.business.assistance.systemcheck.command.impl;

import com.omni.business.assistance.systemcheck.command.AbstractSystemCheckCommand;
import com.omni.business.assistance.systemcheck.command.SystemCheckContext;
import com.omni.business.assistance.systemcheck.config.CheckConfig;
import com.omni.business.assistance.systemcheck.model.Finding;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FirewallCheck extends AbstractSystemCheckCommand {

    @Override public String getId() { return "firewall"; }
    @Override public String getName() { return "防火墙"; }

    @Override
    public int getEstimatedSeconds(CheckConfig config) { return 3; }

    @Override
    public List<Finding> execute(SystemCheckContext ctx) {
        List<Finding> list = new ArrayList<>();
        if (ctx.isWin()) checkWindows(list);
        else checkLinux(list);
        return list;
    }

    private void checkWindows(List<Finding> list) {
        String svc = exec("sc query mpssvc");
        boolean svcRun = svc.contains("RUNNING");
        list.add(new Finding("防火墙", "防火墙服务(MpsSvc)",
                svcRun ? "合规" : "不合规", svcRun ? "信息" : "高",
                svcRun ? "服务运行中" : "服务未运行"));

        String[][] profiles = {
                {"域",   "HKLM\\SYSTEM\\CurrentControlSet\\Services\\SharedAccess\\Parameters\\FirewallPolicy\\DomainProfile"},
                {"专用", "HKLM\\SYSTEM\\CurrentControlSet\\Services\\SharedAccess\\Parameters\\FirewallPolicy\\StandardProfile"},
                {"公用", "HKLM\\SYSTEM\\CurrentControlSet\\Services\\SharedAccess\\Parameters\\FirewallPolicy\\PublicProfile"}
        };
        for (String[] pr : profiles) {
            String out = exec("reg query \"" + pr[1] + "\" /v EnableFirewall").toLowerCase();
            boolean on = out.contains("0x1");
            list.add(new Finding("防火墙", "配置文件[" + pr[0] + "]",
                    on ? "合规" : "不合规", on ? "信息" : "高",
                    "EnableFirewall=" + (on ? "开" : "关")));
        }

        String ns = exec("netsh advfirewall show allprofiles state");
        int onCount = count(ns, "(?i)State\\s+ON");
        int offCount = count(ns, "(?i)State\\s+OFF");
        list.add(new Finding("防火墙", "netsh 各配置状态",
                offCount == 0 ? "合规" : "不合规",
                offCount == 0 ? "信息" : "高",
                "ON=" + onCount + " OFF=" + offCount));

        boolean allow = ns.contains("AllowInbound");
        list.add(new Finding("防火墙", "入站默认策略",
                allow ? "不合规" : "合规", allow ? "高" : "信息",
                allow ? "AllowInbound(入站默认放行)" : "BlockInbound(入站默认阻断)"));
    }

    private void checkLinux(List<Finding> list) {
        String fw = exec("systemctl is-active firewalld 2>/dev/null").trim();
        String ufw = exec("systemctl is-active ufw 2>/dev/null").trim();
        String nft = exec("systemctl is-active nftables 2>/dev/null").trim();
        String ipt = exec("systemctl is-active iptables 2>/dev/null").trim();

        StringBuilder det = new StringBuilder();
        if (!fw.isEmpty()) det.append("firewalld=").append(fw).append(" ");
        if (!ufw.isEmpty()) det.append("ufw=").append(ufw).append(" ");
        if (!nft.isEmpty()) det.append("nftables=").append(nft).append(" ");
        if (!ipt.isEmpty()) det.append("iptables=").append(ipt).append(" ");

        boolean anyActive = fw.equals("active") || ufw.equals("active")
                || nft.equals("active") || ipt.equals("active");
        String iptRules = exec("iptables -L -n 2>/dev/null | wc -l").trim();
        boolean hasRules = iptRules.matches("\\d+") && Integer.parseInt(iptRules) > 5;

        list.add(new Finding("防火墙", "防火墙状态",
                (anyActive || hasRules) ? "合规" : "警告",
                (anyActive || hasRules) ? "信息" : "高",
                (det.length() == 0 ? "未检测到防火墙服务" : det.toString()) + " iptables规则行数=" + iptRules));

        if (fw.equals("active")) {
            String st = exec("firewall-cmd --state 2>/dev/null").trim();
            list.add(new Finding("防火墙", "firewalld 状态",
                    st.equals("running") ? "合规" : "警告", "信息",
                    "firewall-cmd --state=" + st));
        }
    }

    private int count(String s, String regex) {
        Matcher m = Pattern.compile(regex).matcher(s);
        int n = 0;
        while (m.find()) n++;
        return n;
    }
}