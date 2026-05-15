package com.codestats.framework.codestats.launcher;

import com.codestats.framework.codestats.config.Config;
import com.codestats.framework.codestats.gui.CodeStatsGUI;

public class GuiLauncher implements Launcher {
    @Override
    public void launch(Config config) {
        // 在事件分发线程中启动GUI
        javax.swing.SwingUtilities.invokeLater(() -> {
            new CodeStatsGUI().setVisible(true);
        });
    }
}