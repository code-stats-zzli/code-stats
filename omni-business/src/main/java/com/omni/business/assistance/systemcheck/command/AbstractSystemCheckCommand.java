package com.omni.business.assistance.systemcheck.command;

import com.omni.framework.jstats.infrastructure.CommandExecutor;

/** ★ L1-07：exec 用 CommandExecutor */
public abstract class AbstractSystemCheckCommand implements SystemCheckCommand {

    protected static boolean IS_WIN = System.getProperty("os.name", "").toLowerCase().contains("win");

    protected String exec(String cmd) { return execStatic(cmd); }

    public static String execStatic(String cmd) {
        try {
            String[] shell = IS_WIN ? new String[]{"cmd", "/c", cmd} : new String[]{"/bin/sh", "-c", cmd};
            String out = new CommandExecutor(15).execute(shell);
            if (out != null && out.length() > 400000) out = out.substring(0, 400000);
            return out;
        } catch (Exception e) {
            return "";
        }
    }

    protected static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : "..." + s.substring(s.length() - max + 3);
    }
}