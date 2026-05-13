package com.codestats.lib.codestats.gui;

import javax.swing.*;
import java.awt.*;

public class ResultPanel extends JPanel {
    private JTable resultTable;
    private FileInfoTableModel tableModel;
    private JTextArea summaryArea;

    public ResultPanel() {
        setLayout(new BorderLayout());

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.7);

        tableModel = new FileInfoTableModel();
        resultTable = new JTable(tableModel);
        resultTable.setAutoCreateRowSorter(true);
        JScrollPane tableScroll = new JScrollPane(resultTable);
        splitPane.setTopComponent(tableScroll);

        summaryArea = new JTextArea();
        summaryArea.setEditable(false);
        summaryArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane summaryScroll = new JScrollPane(summaryArea);
        splitPane.setBottomComponent(summaryScroll);

        add(splitPane, BorderLayout.CENTER);
    }

    public FileInfoTableModel getTableModel() {
        return tableModel;
    }

    public void setSummaryText(String text) {
        summaryArea.setText(text);
    }

    public void clearResults() {
        tableModel.clear();
        summaryArea.setText("");
    }
}