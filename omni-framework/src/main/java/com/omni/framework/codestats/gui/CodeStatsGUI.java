package com.omni.framework.codestats.gui;

import com.omni.framework.codestats.config.Config;
import com.omni.framework.codestats.config.ConfigLoader;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import javax.swing.*;

public class CodeStatsGUI extends JFrame {
    private static final Logger log = LoggerFactory.getLogger(CodeStatsGUI.class);

    public CodeStatsGUI() {
        setTitle("Code Statistics Tool - Visual Config");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 800);
        setLocationRelativeTo(null);

        CodeStatsGUIPanel panel = new CodeStatsGUIPanel();
        CodeStatsController controller = new CodeStatsController(panel);
        panel.setStartAction(e -> controller.startAnalysis());

        try {
            Config config = ConfigLoader.load(new String[0]);
            panel.applyDefaults(config);
        } catch (Exception ex) {
            log.error("加载配置失败", ex);
        }

        setContentPane(panel);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new CodeStatsGUI().setVisible(true));
    }
}