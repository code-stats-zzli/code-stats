package com.omni.business.assistance.systemcheck.command.impl;

import com.omni.business.assistance.systemcheck.command.AbstractSystemCheckCommand;
import com.omni.business.assistance.systemcheck.command.SystemCheckContext;
import com.omni.business.assistance.systemcheck.model.Finding;
import java.util.*;

public class RdpCheck extends AbstractSystemCheckCommand {

    @Override public String getId() { return "rdp"; }
    @Override public String getName() { return "远程桌面"; }

    @Override
    public List<Finding> execute(SystemCheckContext ctx) {
        List<Finding> list = new ArrayList<>();
        boolean rdpListen = exec(ctx.isWin() ? "netstat -ano" : "ss -tlnp").contains(":3389");

        if (ctx.isWin()) {
            String deny = exec("reg query \"HKLM\\SYSTEM\\CurrentControlSet\\Control\\Terminal Server\" /v fDenyTSConnections").toLowerCase();
            boolean closed = deny.contains("0x1") || !rdpListen;
            list.add(new Finding("远程桌面", "远程桌面(RDP) 开启状态",
                    closed ? "合规" : "不合规", closed ? "信息" : "高",
                    "fDenyTSConnections=" + deny.contains("0x0") + " 端口3389监听=" + rdpListen));
        } else {
            String xrdp = exec("systemctl is-active xrdp 2>/dev/null").trim();
            String vnc = exec("ss -tlnp 2>/dev/null").contains(":5900") ? "监听" : "未监听";
            boolean closed = !xrdp.equals("active") && !vnc.equals("监听");
            list.add(new Finding("远程桌面", "远程桌面(xrdp/VNC) 开启状态",
                    closed ? "合规" : "不合规", closed ? "信息" : "高",
                    "xrdp=" + xrdp + " VNC5900=" + vnc + " 3389监听=" + rdpListen));
        }
        return list;
    }
}