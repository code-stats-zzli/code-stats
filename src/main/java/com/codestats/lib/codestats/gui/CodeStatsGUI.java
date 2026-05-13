package com.codestats.lib.codestats.gui;

import com.codestats.lib.codestats.config.ConfigLoader;

import javax.swing.*;

public class CodeStatsGUI extends JFrame {
    public CodeStatsGUI() {
        setTitle("Code Statistics Tool - Visual Config");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 800);
        setLocationRelativeTo(null);

        CodeStatsGUIPanel panel = new CodeStatsGUIPanel();
        CodeStatsController controller = new CodeStatsController(panel);
        panel.setStartAction(e -> controller.startAnalysis());

        // ========== 加载配置并应用默认值 ==========
        try {
            // 传入空参数加载 yaml、环境变量，命令行参数通常为空
            com.codestats.lib.codestats.config.Config config = ConfigLoader.load(new String[0]);
            panel.applyDefaults(config);  // 将 rootDir 显示到界面并自动设置合并输出
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        // ========================================

        setContentPane(panel);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new CodeStatsGUI().setVisible(true));
    }
}