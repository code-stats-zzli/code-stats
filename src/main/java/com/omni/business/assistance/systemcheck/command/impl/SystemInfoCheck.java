package com.omni.business.assistance.systemcheck.command.impl;

import com.omni.business.assistance.systemcheck.command.AbstractSystemCheckCommand;
import com.omni.business.assistance.systemcheck.command.SystemCheckContext;
import com.omni.business.assistance.systemcheck.model.Finding;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

public class SystemInfoCheck extends AbstractSystemCheckCommand {

    @Override public String getId() { return "system-info"; }
    @Override public String getName() { return "系统信息"; }

    @Override
    public List<Finding> execute(SystemCheckContext ctx) {
        List<Finding> list = new ArrayList<>();
        String hostname = exec("hostname").trim();
        if (hostname.isEmpty()) hostname = "未知";

        String osName = ctx.isWin()
                ? System.getProperty("os.name", "Windows") + " " + System.getProperty("os.version", "")
                : linuxOsName();

        StringBuilder detail = new StringBuilder();
        detail.append("主机=").append(hostname)
                .append(" 系统=").append(osName)
                .append(" 架构=").append(System.getProperty("os.arch", ""))
                .append(" 用户=").append(System.getProperty("user.name", ""))
                .append(" root/管理员=").append(ctx.isRoot() ? "是" : "否")
                .append(" 检查时间=").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));

        list.add(new Finding("系统信息", "主机环境", "信息", "信息", detail.toString()));
        return list;
    }

    private String linuxOsName() {
        try {
            if (!Files.exists(Paths.get("/etc/os-release"))) return "Linux";
            List<String> lines = Files.readAllLines(Paths.get("/etc/os-release"));
            String name = "";
            for (String l : lines) if (l.startsWith("PRETTY_NAME="))
                name = l.replace("PRETTY_NAME=", "").replace("\"", "").trim();
            return name.isEmpty() ? "Linux" : name;
        } catch (Exception e) { return "Linux"; }
    }
}