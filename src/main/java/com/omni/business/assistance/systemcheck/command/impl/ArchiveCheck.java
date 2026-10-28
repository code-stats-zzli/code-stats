package com.omni.business.assistance.systemcheck.command.impl;

import com.omni.business.assistance.systemcheck.command.AbstractSystemCheckCommand;
import com.omni.business.assistance.systemcheck.command.SystemCheckContext;
import com.omni.business.assistance.systemcheck.model.Finding;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class ArchiveCheck extends AbstractSystemCheckCommand {

    @Override public String getId() { return "archive"; }
    @Override public String getName() { return "压缩包/安装包"; }

    @Override
    public List<Finding> execute(SystemCheckContext ctx) {
        List<Finding> list = new ArrayList<>();
        List<String> found = new ArrayList<>();
        String home = System.getProperty("user.home", "");
        String openSource = "(?i).*(git-|node-|nodejs|openjdk|jdk|vscode|visualstudiocode|firefox|7-zip|7zip|winscp|filezilla|putty|python|golang|rust|dbeaver|heidisql|pgadmin|mariadb|postgresql|redis|elasticsearch|docker|kubernetes|anaconda|vlc|notepad\\+\\+|gimp|libreoffice|chromium).*";

        List<String> scanDirs = new ArrayList<>();
        scanDirs.add(ctx.isWin() ? home + "\\Desktop" : home + "/Desktop");
        scanDirs.add(ctx.isWin() ? home + "\\Downloads" : home + "/Downloads");
        for (String d : scanDirs) {
            if (!Files.exists(Paths.get(d))) continue;
            try {
                Files.walk(Paths.get(d)).filter(Files::isRegularFile).forEach(p -> {
                    String n = p.getFileName().toString().toLowerCase();
                    if (!(n.endsWith(".exe") || n.endsWith(".msi") || n.endsWith(".zip")
                            || n.endsWith(".rar") || n.endsWith(".7z") || n.endsWith(".iso"))) return;
                    if (n.matches(openSource)) return;
                    try {
                        long len = Files.size(p);
                        if (len > 2L * 1024 * 1024) found.add(p + " (" + (len / 1024 / 1024) + "MB)");
                    } catch (Exception ignored) {}
                });
            } catch (Exception ignored) {}
        }

        if (found.isEmpty()) {
            list.add(new Finding("压缩包软件", "压缩包/安装包",
                    "合规", "信息", "未检测到明显的软件压缩包/安装镜像"));
        } else {
            for (String f : found) {
                list.add(new Finding("压缩包软件", "压缩包/安装包/绿色版软件",
                        "信息", "中",
                        f + " (请对比桌管软件-应用商城发布版本,及时更新至最新版本)"));
            }
        }
        return list;
    }
}