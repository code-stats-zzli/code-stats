package com.codestats.lib.codestats.bootstrap;

import com.codestats.lib.codestats.config.Config;
import com.codestats.lib.codestats.config.ConfigLoader;
import com.codestats.lib.codestats.launcher.ConsoleLauncher;
import com.codestats.lib.codestats.launcher.GuiLauncher;
import com.codestats.lib.codestats.launcher.Launcher;

public class CodestatsEngine {

    private static final String GUI_FLAG = "--gui";

    public static void run(Class<?> source, String[] args) {
        Config config = ConfigLoader.load(args);
        // 解析args，判断是否包含 --gui 标志（注意：ConfigLoader不处理该标志，需单独判断）
        boolean guiMode = true;
        for (String arg : args) {
            if (GUI_FLAG.equals(arg)) {
                guiMode = false;
                break;
            }
        }
        Launcher launcher = guiMode ? new GuiLauncher() : new ConsoleLauncher();
        try {
            launcher.launch(config);
        } catch (Exception e) {
            System.err.println("Launch failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    // 兼容直接传入Config对象的调用
    public static void run(Config config) {
        // 无参数时默认使用控制台模式
        new ConsoleLauncher().launch(config);
    }
}