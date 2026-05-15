package com.codestats.framework.codestats.gui;

import com.codestats.framework.codestats.config.Config;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;

public class ConfigPanel extends JPanel {
    private JTextField rootDirField;
    private JTextField extensionsField;
    private JTextField excludePathsField;
    private JCheckBox excludeCommentsCheckBox;
    private JSpinner threadCountSpinner;
    private JComboBox<String> sortByCombo;
    private JCheckBox mergeEnabledCheckBox;
    private JTextField mergeOutputFileField;
    private JButton mergeBrowseButton;
    // ★ 新增：额外路径输入框
    private JTextField extraPathsField;

    public ConfigPanel() {
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        // ---------- Root Directory ----------
        gbc.gridx = 0; gbc.gridy = 0;
        add(new JLabel("Root Directory:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2;
        rootDirField = new JTextField(".");
        add(rootDirField, gbc);
        gbc.gridx = 3; gbc.gridwidth = 1;
        JButton browseButton = new JButton("Browse");
        browseButton.addActionListener(e -> chooseDirectory());
        add(browseButton, gbc);

        // ---------- 其他配置 ----------
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        add(new JLabel("Extensions (comma):"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 3;
        extensionsField = new JTextField("java");
        add(extensionsField, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        add(new JLabel("Exclude paths (comma):"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 3;
        excludePathsField = new JTextField("");
        add(excludePathsField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1;
        add(new JLabel("Exclude comments:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 1;
        excludeCommentsCheckBox = new JCheckBox();
        excludeCommentsCheckBox.setSelected(true);
        add(excludeCommentsCheckBox, gbc);

        gbc.gridx = 0; gbc.gridy = 4;
        add(new JLabel("Thread count:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 1;
        threadCountSpinner = new JSpinner(new SpinnerNumberModel(Runtime.getRuntime().availableProcessors(), 1, 32, 1));
        add(threadCountSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 5;
        add(new JLabel("Sort by:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 1;
        sortByCombo = new JComboBox<>(new String[]{"lines", "name", "language", "complexity"});
        add(sortByCombo, gbc);

        // ---------- 合并输出配置 ----------
        gbc.gridx = 0; gbc.gridy = 6;
        add(new JLabel("Merge files to one:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 1;
        mergeEnabledCheckBox = new JCheckBox();
        mergeEnabledCheckBox.setSelected(true);
        add(mergeEnabledCheckBox, gbc);

        gbc.gridx = 0; gbc.gridy = 7;
        add(new JLabel("Output file:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2;
        mergeOutputFileField = new JTextField();
        add(mergeOutputFileField, gbc);
        gbc.gridx = 3; gbc.gridwidth = 1;
        mergeBrowseButton = new JButton("Browse");
        mergeBrowseButton.addActionListener(e -> chooseMergeOutputFile());
        add(mergeBrowseButton, gbc);

        // ★ 新增：额外路径输入框
        gbc.gridx = 0; gbc.gridy = 8;
        add(new JLabel("Extra paths (comma):"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 3;
        extraPathsField = new JTextField("");
        add(extraPathsField, gbc);
    }

    private void chooseDirectory() {
        JFileChooser chooser = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            rootDirField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void chooseMergeOutputFile() {
        JFileChooser chooser = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setDialogTitle("Select output file for merging");
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            mergeOutputFileField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    public String getRootDir() { return rootDirField.getText(); }
    public String getExtensionsText() { return extensionsField.getText(); }
    public String getExcludePathsText() { return excludePathsField.getText(); }
    public boolean isExcludeComments() { return excludeCommentsCheckBox.isSelected(); }
    public int getThreadCount() { return (Integer) threadCountSpinner.getValue(); }
    public String getSortBy() { return (String) sortByCombo.getSelectedItem(); }
    public boolean isMergeEnabled() { return mergeEnabledCheckBox.isSelected(); }
    public String getMergeOutputFile() { return mergeOutputFileField.getText(); }
    public String getExtraPathsText() { return extraPathsField.getText(); }  // ★ 新增

    public void applyDefaults(Config config) {
        if (config.getRootDir() != null && !config.getRootDir().toString().isEmpty()) {
            rootDirField.setText(config.getRootDir().toString());
        }
        if (config.getDefaultOutputPath() != null && !config.getDefaultOutputPath().toString().isEmpty()) {
            mergeOutputFileField.setText(config.getDefaultOutputPath().toString());
        }
        // ★ 新增：应用默认额外路径
        if (config.getExtraPaths() != null && !config.getExtraPaths().isEmpty()) {
            extraPathsField.setText(String.join(",", config.getExtraPaths()));
        }
    }
}