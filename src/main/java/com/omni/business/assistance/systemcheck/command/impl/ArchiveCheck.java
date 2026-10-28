package com.omni.business.assistance.systemcheck.command.impl;

import com.omni.business.assistance.systemcheck.command.AbstractSystemCheckCommand;
import com.omni.business.assistance.systemcheck.command.SystemCheckContext;
import com.omni.business.assistance.systemcheck.config.CheckConfig;
import com.omni.business.assistance.systemcheck.model.Finding;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class ArchiveCheck extends AbstractSystemCheckCommand {

    @Override public String getId() { return "archive"; }
    @Override public String getName() { return "压缩包/安装包"; }
    @Override public int getEstimatedSeconds(CheckConfig config) { return 10; }

    @Override
    public List<Map<String, Object>> getConfigFields(CheckConfig config) {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(field("minSizeMB", "最小文件大小(MB)", "number",
                config.getModuleLong("archive", "minSizeMB", 2),
                "小于该大小的文件不报告（避免噪音）"));
        list.add(fieldList("scanDirs", "扫描目录（相对用户主目录）",
                config.getModuleList("archive", "scanDirs", Arrays.asList("Desktop", "Downloads")),
                "只扫这些目录，如 Desktop,Downloads；逗号分隔"));
        list.add(fieldList("archiveExtensions", "压缩包/安装包扩展名",
                config.getModuleList("archive", "archiveExtensions",
                        Arrays.asList(".exe", ".msi", ".zip", ".rar", ".7z", ".iso")),
                "需要扫描的文件扩展名；逗号分隔"));
        list.add(field("openSourcePattern", "开源软件白名单正则", "text",
                config.getModuleString("archive", "openSourcePattern", defaultOpenSourcePattern()),
                "命中此正则的文件不报告；Java 正则"));
        return list;
    }

    @Override
    public List<Finding> execute(SystemCheckContext ctx) throws Exception {
        List<Finding> list = new ArrayList<>();
        List<String> found = new ArrayList<>();
        String home = System.getProperty("user.home", "");
        CheckConfig cfg = ctx.getConfig();

        long minSizeBytes = cfg.getModuleLong("archive", "minSizeMB", 2) * 1024 * 1024;
        List<String> scanDirs = cfg.getModuleList("archive", "scanDirs", Arrays.asList("Desktop", "Downloads"));
        List<String> exts = cfg.getModuleList("archive", "archiveExtensions",
                Arrays.asList(".exe", ".msi", ".zip", ".rar", ".7z", ".iso"));
        String openSource = cfg.getModuleString("archive", "openSourcePattern", defaultOpenSourcePattern());

        for (String sub : scanDirs) {
            if (ctx.isCancelled()) break;
            String dir = ctx.isWin() ? home + "\\" + sub : home + "/" + sub;
            if (!Files.exists(Paths.get(dir))) continue;
            try {
                Files.walk(Paths.get(dir)).filter(Files::isRegularFile).forEach(p -> {
                    if (ctx.isCancelled()) return;
                    String n = p.getFileName().toString().toLowerCase();
                    boolean match = false;
                    for (String ext : exts) {
                        if (n.endsWith(ext)) { match = true; break; }
                    }
                    if (!match) return;
                    if (n.matches(openSource)) return;
                    try {
                        long len = Files.size(p);
                        if (len > minSizeBytes) {
                            found.add(p + " (" + (len / 1024 / 1024) + "MB)");
                        }
                    } catch (Exception ignored) {}
                });
            } catch (Exception ignored) {}
        }

        if (ctx.isCancelled()) {
            list.add(new Finding("压缩包软件", "扫描已取消", "信息", "信息", "用户中止扫描"));
            return list;
        }

        if (found.isEmpty()) {
            list.add(new Finding("压缩包软件", "压缩包/安装包",
                    "合规", "信息", "未检测到明显的软件压缩包/安装镜像"));
        } else {
            for (String f : found) {
                list.add(new Finding("压缩包软件", "压缩包/安装包/绿色版软件",
                        "信息", "中", f + " (请对比桌管软件-应用商城发布版本)"));
            }
        }
        return list;
    }

    private static String defaultOpenSourcePattern() {
        return "(?i).*(git-|node-|nodejs|openjdk|jdk|vscode|visualstudiocode|firefox|7-zip|7zip|winscp|filezilla|putty|python|golang|rust|dbeaver|heidisql|pgadmin|mariadb|postgresql|redis|elasticsearch|docker|kubernetes|anaconda|vlc|notepad\\+\\+|gimp|libreoffice|chromium).*";
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