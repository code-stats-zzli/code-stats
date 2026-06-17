package com.omni.business.assistance.screenshot.ui;

import com.omni.business.assistance.screenshot.service.ScreenshotService;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.Map;

/**
 * 极简模式截图助手浮窗
 * - 默认显示：粘贴按钮 + 输入框 + 搜索按钮 + 正常模式切换 + 关闭按钮
 * - 搜索触发结果显示面板（面板自动出现，鼠标移出自动消失）
 */
public class MiniCaptureWindow extends JWindow {
    private static final Logger log = LoggerFactory.getLogger(MiniCaptureWindow.class);

    private final ScreenshotService screenshotService;
    private JPanel mainPanel;
    private JPanel titleBar;
    private JTextField searchField;
    private JButton pasteBtn, searchBtn, normalModeBtn, closeBtn;
    private JPanel resultPanel;
    private JTextArea resultArea;
    private JLabel statusLabel;

    private boolean resultVisible = false;

    public MiniCaptureWindow(ScreenshotService service) {
        this.screenshotService = service;
        initUI();
        setupDrag();
        setSize(400, 160);
        setAlwaysOnTop(true);
        setBackground(new Color(0, 0, 0, 100));
        setLocationRelativeTo(null);
        setVisible(true);
        log.info("极简模式截图助手启动");
    }

    private void initUI() {
        mainPanel = new JPanel(new BorderLayout(4, 4));
        mainPanel.setOpaque(false);
        mainPanel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(0, 102, 255, 200), 2, true),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)
        ));

        // ---- 标题栏（可拖拽） ----
        titleBar = new JPanel(new BorderLayout());
        titleBar.setOpaque(false);
        titleBar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        JLabel dragLabel = new JLabel("⠿ 极简助手");
        dragLabel.setFont(new Font("Inter", Font.PLAIN, 14));
        dragLabel.setForeground(Color.WHITE);
        dragLabel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        titleBar.add(dragLabel, BorderLayout.WEST);

        // 正常模式按钮和关闭按钮
        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightBtns.setOpaque(false);

        normalModeBtn = new JButton("完整模式");
        normalModeBtn.setFont(new Font("Inter", Font.PLAIN, 11));
        normalModeBtn.setForeground(Color.WHITE);
        normalModeBtn.setBackground(new Color(60, 60, 60, 200));
        normalModeBtn.setBorderPainted(false);
        normalModeBtn.setFocusPainted(false);
        normalModeBtn.setOpaque(true);
        normalModeBtn.addActionListener(e -> switchToFullMode());
        rightBtns.add(normalModeBtn);

        closeBtn = new JButton("✖");
        closeBtn.setFont(new Font("Inter", Font.PLAIN, 14));
        closeBtn.setForeground(Color.WHITE);
        closeBtn.setBackground(new Color(60, 60, 60, 200));
        closeBtn.setBorderPainted(false);
        closeBtn.setFocusPainted(false);
        closeBtn.setOpaque(true);
        closeBtn.addActionListener(e -> dispose());
        rightBtns.add(closeBtn);

        titleBar.add(rightBtns, BorderLayout.EAST);
        mainPanel.add(titleBar, BorderLayout.NORTH);

        // ---- 搜索输入行 ----
        JPanel inputRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 2));
        inputRow.setOpaque(false);

        pasteBtn = new JButton("📋 粘贴");
        pasteBtn.setFont(new Font("Inter", Font.PLAIN, 12));
        pasteBtn.setForeground(Color.WHITE);
        pasteBtn.setBackground(new Color(0, 102, 255, 200));
        pasteBtn.setBorderPainted(false);
        pasteBtn.setFocusPainted(false);
        pasteBtn.setOpaque(true);
        pasteBtn.addActionListener(e -> pasteFromClipboard());
        inputRow.add(pasteBtn);

        searchField = new JTextField(16);
        searchField.setFont(new Font("Monospaced", Font.PLAIN, 13));
        searchField.setBackground(new Color(255, 255, 255, 220));
        searchField.setForeground(Color.BLACK);
        searchField.addActionListener(e -> doSearch());
        inputRow.add(searchField);

        searchBtn = new JButton("🔍 搜索");
        searchBtn.setFont(new Font("Inter", Font.PLAIN, 12));
        searchBtn.setForeground(Color.WHITE);
        searchBtn.setBackground(new Color(0, 102, 255, 200));
        searchBtn.setBorderPainted(false);
        searchBtn.setFocusPainted(false);
        searchBtn.setOpaque(true);
        searchBtn.addActionListener(e -> doSearch());
        inputRow.add(searchBtn);

        mainPanel.add(inputRow, BorderLayout.CENTER);

        // ---- 状态标签 ----
        statusLabel = new JLabel("就绪");
        statusLabel.setFont(new Font("Inter", Font.PLAIN, 11));
        statusLabel.setForeground(Color.WHITE);
        statusLabel.setBackground(new Color(0, 0, 0, 150));
        statusLabel.setOpaque(true);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        mainPanel.add(statusLabel, BorderLayout.SOUTH);

        // ---- 结果面板（独立于主面板，浮在下方） ----
        resultPanel = new JPanel(new BorderLayout());
        resultPanel.setBackground(new Color(0, 0, 0, 200));
        resultPanel.setBorder(new LineBorder(new Color(0, 102, 255, 150), 2));
        resultPanel.setVisible(false);

        resultArea = new JTextArea();
        resultArea.setEditable(false);
        resultArea.setLineWrap(true);
        resultArea.setWrapStyleWord(true);
        resultArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        resultArea.setBackground(new Color(0, 0, 0, 150));
        resultArea.setForeground(Color.WHITE);
        resultArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane scroll = new JScrollPane(resultArea);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        resultPanel.add(scroll, BorderLayout.CENTER);

        // 鼠标移出结果面板时自动隐藏（延迟）
        resultPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                hideResultDelayed();
            }
        });

        setContentPane(mainPanel);

        // 添加全局鼠标监听，当鼠标移出整个窗口时，如果结果面板显示则延迟隐藏
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                Point p = e.getPoint();
                if (p.x < 0 || p.y < 0 || p.x > getWidth() || p.y > getHeight()) {
                    hideResultDelayed();
                }
            }
        });
    }

    private void pasteFromClipboard() {
        try {
            String text = Toolkit.getDefaultToolkit().getSystemClipboard()
                    .getData(java.awt.datatransfer.DataFlavor.stringFlavor)
                    .toString();
            if (text != null && !text.isEmpty()) {
                searchField.setText(text);
                doSearch();
            } else {
                statusLabel.setText("⚠️ 剪贴板为空");
            }
        } catch (Exception e) {
            log.warn("读取剪贴板失败", e);
            statusLabel.setText("⚠️ 无法读取剪贴板");
        }
    }

    private void doSearch() {
        String keyword = searchField.getText().trim();
        if (keyword.isEmpty()) {
            statusLabel.setText("⚠️ 请输入关键词");
            return;
        }
        statusLabel.setText("⏳ 搜索中...");
        new SwingWorker<List<Map<String, String>>, Void>() {
            @Override
            protected List<Map<String, String>> doInBackground() throws Exception {
                return screenshotService.searchByText(keyword);
            }

            @Override
            protected void done() {
                try {
                    List<Map<String, String>> rows = get();
                    if (rows == null || rows.isEmpty()) {
                        resultArea.setText("😞 未找到匹配的题目");
                        statusLabel.setText("😞 无匹配");
                    } else {
                        StringBuilder sb = new StringBuilder();
                        if (!rows.isEmpty()) {
                            Map<String, String> first = rows.get(0);
                            for (String col : first.keySet()) {
                                sb.append("【").append(col).append("】");
                            }
                            sb.append("\n");
                            sb.append("=".repeat(60)).append("\n");
                        }
                        for (Map<String, String> row : rows) {
                            for (Map.Entry<String, String> entry : row.entrySet()) {
                                sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                            }
                            sb.append("---\n");
                        }
                        resultArea.setText(sb.toString());
                        statusLabel.setText("✅ 找到 " + rows.size() + " 条匹配");
                    }
                    showResultPanel();
                } catch (Exception e) {
                    log.error("搜索失败", e);
                    resultArea.setText("❌ 搜索失败: " + e.getMessage());
                    statusLabel.setText("❌ 搜索失败");
                    showResultPanel();
                }
            }
        }.execute();
    }

    private void showResultPanel() {
        // 将结果面板显示在主窗口下方
        Point location = getLocation();
        resultPanel.setSize(getWidth(), 200);
        resultPanel.setLocation(location.x, location.y + getHeight() + 2);
        // 确保不超出屏幕
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        if (resultPanel.getY() + resultPanel.getHeight() > screen.height) {
            resultPanel.setLocation(location.x, location.y - resultPanel.getHeight() - 2);
        }
        resultPanel.setVisible(true);
        resultVisible = true;
        // 10秒后自动隐藏（如果鼠标未移入）
        Timer timer = new Timer(10000, e -> {
            // ★ 使用 getMousePosition() 判断鼠标是否在结果面板上
            if (resultVisible && resultPanel.getMousePosition() == null) {
                hideResultPanel();
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    private void hideResultPanel() {
        resultPanel.setVisible(false);
        resultVisible = false;
    }

    private void hideResultDelayed() {
        Timer timer = new Timer(300, e -> {
            // ★ 使用 getMousePosition() 判断鼠标是否在结果面板上
            if (resultVisible && resultPanel.getMousePosition() == null) {
                hideResultPanel();
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    private void switchToFullMode() {
        // 关闭极简模式，打开完整模式
        dispose();
        SwingUtilities.invokeLater(() -> {
            // 完整模式浮窗
            FloatingCaptureWindow fullWindow = new FloatingCaptureWindow(screenshotService);
            fullWindow.setVisible(true);
        });
    }

    private void setupDrag() {
        MouseAdapter dragAdapter = new MouseAdapter() {
            Point start = null;
            @Override
            public void mousePressed(MouseEvent e) {
                start = e.getPoint();
            }
            @Override
            public void mouseDragged(MouseEvent e) {
                if (start != null) {
                    Point newPos = getLocation();
                    newPos.translate(e.getX() - start.x, e.getY() - start.y);
                    setLocation(newPos);
                    if (resultVisible) {
                        resultPanel.setLocation(newPos.x, newPos.y + getHeight() + 2);
                    }
                }
            }
        };
        titleBar.addMouseListener(dragAdapter);
        titleBar.addMouseMotionListener(dragAdapter);
    }

    @Override
    public void dispose() {
        if (resultPanel != null) resultPanel.setVisible(false);
        super.dispose();
    }
}