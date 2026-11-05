package com.omni.business.assistance.systemcheck.command.impl;

import com.omni.business.assistance.systemcheck.command.AbstractSystemCheckCommand;
import com.omni.business.assistance.systemcheck.command.SystemCheckContext;
import com.omni.business.assistance.systemcheck.config.CheckConfig;
import com.omni.business.assistance.systemcheck.model.Finding;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LockScreenCheck extends AbstractSystemCheckCommand {

    @Override public String getId() { return "lock-screen"; }
    @Override public String getName() { return "屏保/锁屏"; }

    @Override
    public int getEstimatedSeconds(CheckConfig config) { return 2; }

    @Override
    public List<Finding> execute(SystemCheckContext ctx) {
        List<Finding> list = new ArrayList<>();
        if (ctx.isWin()) checkWindows(list);
        else checkLinux(list);
        return list;
    }

    private void checkWindows(List<Finding> list) {
        String desk = exec("reg query \"HKCU\\Control Panel\\Desktop\"");
        String active = val(desk, "ScreenSaveActive");
        String timeout = val(desk, "ScreenSaveTimeOut");
        String secure = val(desk, "ScreenSaverIsSecure");

        list.add(new Finding("屏保锁屏", "屏保已启用",
                "1".equals(active) ? "合规" : "不合规",
                "1".equals(active) ? "信息" : "中",
                "ScreenSaveActive=" + active));

        int t = timeout.matches("\\d+") ? Integer.parseInt(timeout) : -1;
        list.add(new Finding("屏保锁屏", "无操作超时(≤300秒)",
                t >= 1 && t <= 300 ? "合规" : (t > 300 ? "警告" : "不合规"),
                t >= 1 && t <= 300 ? "信息" : "中",
                "ScreenSaveTimeOut=" + timeout));

        list.add(new Finding("屏保锁屏", "恢复时需密码",
                "1".equals(secure) ? "合规" : "不合规",
                "1".equals(secure) ? "信息" : "中",
                "ScreenSaverIsSecure=" + secure));
    }

    private void checkLinux(List<Finding> list) {
        String idle = exec("gsettings get org.gnome.desktop.session idle-delay 2>/dev/null").trim();
        String lock = exec("gsettings get org.gnome.desktop.screensaver lock-enabled 2>/dev/null").trim();

        int t = -1;
        Matcher m = Pattern.compile("(\\d+)").matcher(idle);
        if (m.find()) t = Integer.parseInt(m.group(1));

        list.add(new Finding("屏保锁屏", "GNOME空闲超时(≤300秒)",
                t >= 1 && t <= 300 ? "合规" : "不合规",
                t >= 1 && t <= 300 ? "信息" : "中",
                "idle-delay=" + idle));

        list.add(new Finding("屏保锁屏", "锁屏已启用",
                lock.contains("true") ? "合规" : "不合规",
                lock.contains("true") ? "信息" : "中",
                "lock-enabled=" + lock));

        String autoLogin = readFile("/etc/gdm3/custom.conf") + readFile("/etc/gdm/custom.conf");
        boolean al = autoLogin.contains("AutomaticLoginEnable=true");
        list.add(new Finding("屏保锁屏", "自动登录",
                al ? "警告" : "合规", al ? "中" : "信息",
                al ? "检测到自动登录" : "未检测到自动登录"));
    }

    private String val(String out, String key) {
        for (String line : out.split("\n")) {
            if (line.contains(key)) {
                String[] p = line.trim().split("\\s+");
                if (p.length > 0) return p[p.length - 1];
            }
        }
        return "";
    }

    private String readFile(String path) {
        try {
            return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}