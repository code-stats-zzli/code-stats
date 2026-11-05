package com.omni.framework.codestats.bootstrap;

import com.omni.framework.codestats.config.Config;
import com.omni.framework.codestats.config.ConfigLoader;
import com.omni.framework.codestats.launcher.ConsoleLauncher;
import com.omni.framework.codestats.launcher.GuiLauncher;
import com.omni.framework.codestats.launcher.Launcher;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

public class CodestatsEngine {
    private static final Logger log = LoggerFactory.getLogger(CodestatsEngine.class);
    private static final String GUI_FLAG = "--gui";

    public static void run(Class<?> source, String[] args) {
        Config config = ConfigLoader.load(args);
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
            log.error("Launch failed", e);
            System.exit(1);
        }
    }

    public static void run(Config config) {
        new ConsoleLauncher().launch(config);
    }
}