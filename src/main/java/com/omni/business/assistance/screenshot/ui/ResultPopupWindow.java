package com.omni.business.assistance.screenshot.ui;

import com.omni.business.assistance.screenshot.model.ScreenshotResult;

import javax.swing.*;
import java.awt.*;

public class ResultPopupWindow extends JWindow {
    private final JTextArea textArea = new JTextArea();
    private final JTextArea answerArea = new JTextArea();

    public ResultPopupWindow() {
        setLayout(new BorderLayout());
        setSize(400, 300);
        setAlwaysOnTop(true);

        JPanel mainPanel = new JPanel(new BorderLayout(8, 8));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        mainPanel.setBackground(new Color(255, 255, 255, 240));

        // 识别文字
        JLabel label1 = new JLabel("📝 识别文字");
        label1.setFont(new Font("Inter", Font.BOLD, 13));
        label1.setForeground(new Color(0, 102, 255));
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        textArea.setBackground(new Color(248, 250, 252));
        textArea.setRows(3);

        // 答案
        JLabel label2 = new JLabel("📖 题库答案");
        label2.setFont(new Font("Inter", Font.BOLD, 13));
        label2.setForeground(new Color(0, 102, 255));
        answerArea.setEditable(false);
        answerArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        answerArea.setBackground(new Color(240, 248, 255));
        answerArea.setRows(4);

        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(label1, BorderLayout.NORTH);
        northPanel.add(new JScrollPane(textArea), BorderLayout.CENTER);

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(label2, BorderLayout.NORTH);
        southPanel.add(new JScrollPane(answerArea), BorderLayout.CENTER);

        mainPanel.add(northPanel, BorderLayout.CENTER);
        mainPanel.add(southPanel, BorderLayout.SOUTH);

        add(mainPanel, BorderLayout.CENTER);
        pack();
    }

    public void showResult(ScreenshotResult result, Point location) {
        textArea.setText(result.getRecognizedText() != null ? result.getRecognizedText() : "（未识别到文字）");
        if (result.isFound()) {
            String answerText = "题目：" + result.getMatchedQuestion() + "\n答案：" + result.getMatchedAnswer();
            answerArea.setText(answerText);
        } else {
            answerArea.setText("😞 题库中未找到匹配题目");
        }
        setLocation(location.x + 20, location.y + 20);
        setVisible(true);
        toFront();
    }

    public void close() {
        setVisible(false);
        dispose();
    }
}