package com.omni.business.assistance.systemcheck.command;

import com.omni.business.assistance.systemcheck.config.CheckConfig;

/** 检查命令的执行上下文（持有配置和公共信息） */
public class SystemCheckContext {
    private final CheckConfig config;
    private final boolean isWin;
    private final boolean isRoot;

    public SystemCheckContext(CheckConfig config) {
        this.config = config;
        this.isWin = System.getProperty("os.name", "").toLowerCase().contains("win");
        this.isRoot = detectRoot();
    }

    private boolean detectRoot() {
        if (isWin) {
            return AbstractSystemCheckCommand.execStatic("net session").length() > 0;
        }
        String user = System.getProperty("user.name", "");
        return "root".equals(user) || "0".equals(user);
    }

    public CheckConfig getConfig() { return config; }
    public boolean isWin() { return isWin; }
    public boolean isRoot() { return isRoot; }
}