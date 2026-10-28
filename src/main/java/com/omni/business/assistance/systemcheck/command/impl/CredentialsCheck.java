package com.omni.business.assistance.systemcheck.command.impl;

import com.omni.business.assistance.systemcheck.command.AbstractSystemCheckCommand;
import com.omni.business.assistance.systemcheck.command.SystemCheckContext;
import com.omni.business.assistance.systemcheck.model.Finding;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class CredentialsCheck extends AbstractSystemCheckCommand {

    @Override public String getId() { return "credentials"; }
    @Override public String getName() { return "凭据/软件"; }

    @Override
    public List<Finding> execute(SystemCheckContext ctx) {
        List<Finding> list = new ArrayList<>();
        String home = System.getProperty("user.home", "");

        // 1. 常见凭据文件
        String[] creds = {".git-credentials", ".npmrc", ".m2/settings.xml",
                ".kube/config", ".docker/config.json", ".pip/pip.conf",
                ".ssh/id_rsa", ".ssh/id_ed25519"};
        for (String f : creds) {
            if (Files.exists(Paths.get(home, f))) {
                list.add(new Finding("明文密码", "凭据文件 " + f,
                        "不合规", "高", "发现 " + Paths.get(home, f)));
            }
        }

        // 2. 浏览器留存
        List<String> browsers = new ArrayList<>();
        if (ctx.isWin()) {
            browsers.add(home + "\\AppData\\Local\\Google\\Chrome\\User Data");
            browsers.add(home + "\\AppData\\Local\\Microsoft\\Edge\\User Data");
        } else {
            browsers.add(home + "/.mozilla/firefox");
            browsers.add("/opt/kingsoft/browser");
        }
        for (String b : browsers) {
            if (Files.exists(Paths.get(b))) {
                list.add(new Finding("明文密码", "浏览器目录存在",
                        "警告", "中", b));
            }
        }

        // 3. 商业工具
        boolean foundComm = false;
        if (ctx.isWin()) {
            String uninst = exec("reg query \"HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\" /s 2>nul");
            String commercial = "(?i)Navicat|Xshell|SecureCRT|PL/SQL|DataGrip|dbForge|MobaXterm|Termius|Beyond\\s+Compare|VMware\\s+Workstation|SQLyog|Toad";
            for (String line : uninst.split("\n")) {
                if (line.contains("DisplayName") && line.matches("(?s).*" + commercial + ".*")) {
                    list.add(new Finding("非开源工具", "检测到商业/非开源工具",
                            "警告", "中", line.trim()));
                    foundComm = true;
                }
            }
        } else {
            String pkgs = exec("dpkg -l 2>/dev/null | grep -iE 'navicat|xshell|securecrt|datagrip|mobaxterm'; " +
                    "rpm -qa 2>/dev/null | grep -iE 'navicat|xshell|securecrt|datagrip'");
            for (String line : pkgs.split("\n")) {
                if (!line.trim().isEmpty()) {
                    list.add(new Finding("非开源工具", "检测到商业/非开源工具",
                            "警告", "中", line.trim()));
                    foundComm = true;
                }
            }
        }
        if (!foundComm) {
            list.add(new Finding("非开源工具", "商业/非开源工具",
                    "合规", "信息", "未检测到 Navicat/Xshell 等商业工具"));
        }
        return list;
    }
}