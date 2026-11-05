package com.omni.framework.codestats.gui;

import com.omni.framework.codestats.config.Config;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

public class CodeStatsGUIPanel extends JPanel {
    private ConfigPanel configPanel;
    private ProgressPanel progressPanel;
    private ResultPanel resultPanel;
    private JButton startButton;

    public CodeStatsGUIPanel() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        configPanel = new ConfigPanel();
        startButton = new JButton("Start Analysis");
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(configPanel, BorderLayout.CENTER);
        topPanel.add(startButton, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        progressPanel = new ProgressPanel();
        resultPanel = new ResultPanel();

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(progressPanel, BorderLayout.NORTH);
        centerPanel.add(resultPanel, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);
    }

    public void setStartAction(ActionListener listener) {
        startButton.addActionListener(listener);
    }

    public boolean isMergeEnabled() { return configPanel.isMergeEnabled(); }
    public String getMergeOutputFile() { return configPanel.getMergeOutputFile(); }
    public String getRootDir() { return configPanel.getRootDir(); }
    public String getExtensionsText() { return configPanel.getExtensionsText(); }
    public String getExcludePathsText() { return configPanel.getExcludePathsText(); }
    public boolean isExcludeComments() { return configPanel.isExcludeComments(); }
    public int getThreadCount() { return configPanel.getThreadCount(); }
    public String getSortBy() { return configPanel.getSortBy(); }
    // ★ 新增
    public String getExtraPathsText() { return configPanel.getExtraPathsText(); }

    public void setStartEnabled(boolean enabled) { startButton.setEnabled(enabled); }
    public void resetProgress() { progressPanel.reset(); }
    public void updateTotalFiles(int total) { progressPanel.setTotalFiles(total); }
    public void updateDirectoryCount(int count) { progressPanel.setDirectoryCount(count); }
    public void updateProgress(int percent, String fileName, String elapsed, String eta) {
        progressPanel.update(percent, fileName, elapsed, eta);
    }
    public void setProgressDone() { progressPanel.markDone(); }
    public FileInfoTableModel getTableModel() { return resultPanel.getTableModel(); }
    public void setSummaryText(String text) { resultPanel.setSummaryText(text); }
    public void clearResults() { resultPanel.clearResults(); }

    public void applyDefaults(Config config) {
        configPanel.applyDefaults(config);
    }
}