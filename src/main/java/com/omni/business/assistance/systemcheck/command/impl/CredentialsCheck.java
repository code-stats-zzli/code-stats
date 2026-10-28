package com.omni.business.assistance.systemcheck.command.impl;

import com.omni.business.assistance.systemcheck.command.AbstractSystemCheckCommand;
import com.omni.business.assistance.systemcheck.command.SystemCheckContext;
import com.omni.business.assistance.systemcheck.config.CheckConfig;
import com.omni.business.assistance.systemcheck.model.Finding;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class CredentialsCheck extends AbstractSystemCheckCommand {

    @Override public String getId() { return "credentials"; }
    @Override public String getName() { return "凭据/软件"; }
    @Override public int getEstimatedSeconds(CheckConfig config) { return 5; }

    @Override
    public List<Map<String, Object>> getConfigFields(CheckConfig config) {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(fieldList("credentialFiles", "凭据文件（相对用户主目录）",
                config.getModuleList("credentials", "credentialFiles", defaultCredFiles()),
                "命中这些文件路径判为高风险；逗号分隔"));
        list.add(fieldList("browserDirsWin", "浏览器目录 (Windows)",
                config.getModuleList("credentials", "browserDirsWin", defaultBrowserWin()),
                "相对用户主目录；逗号分隔"));
        list.add(fieldList("browserDirsLinux", "浏览器目录 (Linux)",
                config.getModuleList("credentials", "browserDirsLinux", defaultBrowserLinux()),
                "相对用户主目录；逗号分隔"));
        list.add(field("commercialPattern", "商业工具正则", "text",
                config.getModuleString("credentials", "commercialPattern", defaultCommercial()),
                "命中此正则的工具判为商业/非开源；Java 正则"));
        return list;
    }

    @Override
    public List<Finding> execute(SystemCheckContext ctx) throws Exception {
        List<Finding> list = new ArrayList<>();
        String home = System.getProperty("user.home", "");
        CheckConfig cfg = ctx.getConfig();

        // 1. 凭据文件
        List<String> credFiles = cfg.getModuleList("credentials", "credentialFiles", defaultCredFiles());
        for (String f : credFiles) {
            if (ctx.isCancelled()) break;
            if (Files.exists(Paths.get(home, f))) {
                list.add(new Finding("明文密码", "凭据文件 " + f, "不合规", "高", "发现 " + Paths.get(home, f)));
            }
        }

        // 2. 浏览器目录
        List<String> bDirs = ctx.isWin()
                ? cfg.getModuleList("credentials", "browserDirsWin", defaultBrowserWin())
                : cfg.getModuleList("credentials", "browserDirsLinux", defaultBrowserLinux());
        for (String b : bDirs) {
            if (ctx.isCancelled()) break;
            String path = home.replace("\\", "/") + "/" + b.replace("\\", "/");
            if (Files.exists(Paths.get(path))) {
                list.add(new Finding("明文密码", "浏览器目录存在", "警告", "中", path));
            }
        }

        // 3. 商业工具
        String commercial = cfg.getModuleString("credentials", "commercialPattern", defaultCommercial());
        boolean foundComm = false;
        if (ctx.isWin()) {
            String uninst = exec("reg query \"HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\" /s 2>nul");
            for (String line : uninst.split("\n")) {
                if (ctx.isCancelled()) break;
                if (line.contains("DisplayName") && line.matches("(?s).*" + commercial + ".*")) {
                    list.add(new Finding("非开源工具", "检测到商业/非开源工具", "警告", "中", line.trim()));
                    foundComm = true;
                }
            }
        } else {
            String pkgs = exec("dpkg -l 2>/dev/null; rpm -qa 2>/dev/null");
            for (String line : pkgs.split("\n")) {
                if (ctx.isCancelled()) break;
                if (!line.trim().isEmpty() && line.matches("(?s).*" + commercial + ".*")) {
                    list.add(new Finding("非开源工具", "检测到商业/非开源工具", "警告", "中", line.trim()));
                    foundComm = true;
                }
            }
        }
        if (!foundComm) {
            list.add(new Finding("非开源工具", "商业/非开源工具", "合规", "信息", "未检测到商业工具"));
        }

        if (ctx.isCancelled()) {
            list.add(0, new Finding("凭据/软件", "扫描已取消", "信息", "信息", "用户中止扫描"));
        }
        return list;
    }

    private static List<String> defaultCredFiles() {
        return Arrays.asList(".git-credentials", ".npmrc", ".m2/settings.xml", ".kube/config",
                ".docker/config.json", ".pip/pip.conf", ".ssh/id_rsa", ".ssh/id_ed25519",
                ".aws/credentials", ".azure/accessTokens.json");
    }
    private static List<String> defaultBrowserWin() {
        return Arrays.asList("AppData/Local/Google/Chrome/User Data",
                "AppData/Local/Microsoft/Edge/User Data",
                "AppData/Roaming/Mozilla/Firefox");
    }
    private static List<String> defaultBrowserLinux() {
        return Arrays.asList(".mozilla/firefox", ".config/google-chrome", "/opt/kingsoft/browser");
    }
    private static String defaultCommercial() {
        return "(?i)Navicat|Xshell|SecureCRT|PL/SQL|DataGrip|dbForge|MobaXterm|Termius|Beyond\\s+Compare|VMware\\s+Workstation|SQLyog|Toad|UltraEdit|WinRAR|WinZip|TeamViewer|IDM";
    }

    private Map<String, Object> field(String key, String label, String type, Object value, String hint) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key); m.put("label", label); m.put("type", type);
        m.put("value", value); m.put("hint", hint);
        return m;
    }
    private Map<String, Object> fieldList(String key, String label, List<String> value, String hint) {
        return field(key, label, "list", value, hint);
    }
}