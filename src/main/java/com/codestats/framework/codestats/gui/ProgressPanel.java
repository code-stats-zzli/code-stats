package com.codestats.framework.codestats.gui;

import javax.swing.*;
import java.awt.*;

public class ProgressPanel extends JPanel {
    private JProgressBar progressBar;
    private JLabel totalFilesLabel;
    private JLabel currentFileLabel;
    private JLabel elapsedTimeLabel;
    private JLabel estimatedTimeLabel;
    private JLabel dirCountLabel;

    public ProgressPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Progress"));

        JPanel infoPanel = new JPanel(new GridLayout(2, 3, 10, 5));
        totalFilesLabel = new JLabel("Files: 0");
        currentFileLabel = new JLabel("Current: -");
        elapsedTimeLabel = new JLabel("Elapsed: 00:00");
        estimatedTimeLabel = new JLabel("ETA: --:--");
        dirCountLabel = new JLabel("Directories: 0");

        infoPanel.add(totalFilesLabel);
        infoPanel.add(currentFileLabel);
        infoPanel.add(elapsedTimeLabel);
        infoPanel.add(estimatedTimeLabel);
        infoPanel.add(dirCountLabel);
        infoPanel.add(new JLabel()); // 占位

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("0%");

        add(infoPanel, BorderLayout.NORTH);
        add(progressBar, BorderLayout.SOUTH);
    }

    public void reset() {
        progressBar.setValue(0);
        progressBar.setString("0%");
        totalFilesLabel.setText("Files: 0");
        currentFileLabel.setText("Current: -");
        elapsedTimeLabel.setText("Elapsed: 00:00");
        estimatedTimeLabel.setText("ETA: --:--");
        dirCountLabel.setText("Directories: 0");
    }

    public void setTotalFiles(int total) {
        totalFilesLabel.setText("Files: " + total);
    }

    public void setDirectoryCount(int count) {
        dirCountLabel.setText("Directories: " + count);
    }

    public void update(int percent, String currentFileName, String elapsed, String eta) {
        progressBar.setValue(percent);
        progressBar.setString(percent + "%");
        currentFileLabel.setText("Current: " + currentFileName);
        elapsedTimeLabel.setText("Elapsed: " + elapsed);
        estimatedTimeLabel.setText("ETA: " + eta);
    }

    public void markDone() {
        progressBar.setValue(100);
        progressBar.setString("100% - Done");
        currentFileLabel.setText("Current: -");
        estimatedTimeLabel.setText("ETA: 00:00");
    }
}