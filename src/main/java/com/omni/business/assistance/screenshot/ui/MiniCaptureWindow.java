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
 * - 主窗口尺寸：320x140
 * - 结果浮窗尺寸：320x300，滚动条按需，默认显示顶部
 */
public class MiniCaptureWindow extends JWindow {
    private static final Logger log = LoggerFactory.getLogger(MiniCaptureWindow.class);

    private final ScreenshotService screenshotService;
    private JPanel mainPanel;
    private JPanel titleBar;
    private JTextField searchField;
    private JButton pasteBtn, searchBtn, fullModeBtn, closeBtn;
    private JWindow resultWindow;
    private JTextArea resultArea;
    private JScrollPane resultScroll;
    private JLabel statusLabel;

    private boolean resultVisible = false;

    public MiniCaptureWindow(ScreenshotService service) {
        this.screenshotService = service;
        initUI();
        setupDrag();
        initResultWindow();
        setSize(320, 140);   // 主窗口尺寸 320x140
        setAlwaysOnTop(true);
        setBackground(new Color(0, 0, 0, 100));
        setLocationRelativeTo(null);
        setVisible(false);
        log.info("极简模式截图助手初始化");
    }

    private void initUI() {
        mainPanel = new JPanel(new BorderLayout(2, 2));
        mainPanel.setOpaque(false);
        mainPanel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(0, 102, 255, 200), 2, true),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));

        // ---- 标题栏（可拖拽） ----
        titleBar = new JPanel(new BorderLayout());
        titleBar.setOpaque(false);
        titleBar.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        JLabel dragLabel = new JLabel("⠿ 极简");
        dragLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        dragLabel.setForeground(Color.WHITE);
        dragLabel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        titleBar.add(dragLabel, BorderLayout.WEST);

        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        rightBtns.setOpaque(false);

        fullModeBtn = new JButton("完整");
        fullModeBtn.setFont(new Font("微软雅黑", Font.PLAIN, 10));
        fullModeBtn.setForeground(Color.WHITE);
        fullModeBtn.setBackground(new Color(60, 60, 60, 200));
        fullModeBtn.setBorderPainted(false);
        fullModeBtn.setFocusPainted(false);
        fullModeBtn.setOpaque(true);
        fullModeBtn.addActionListener(e -> switchToFullMode());
        rightBtns.add(fullModeBtn);

        closeBtn = new JButton("✖");
        closeBtn.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        closeBtn.setForeground(Color.WHITE);
        closeBtn.setBackground(new Color(60, 60, 60, 200));
        closeBtn.setBorderPainted(false);
        closeBtn.setFocusPainted(false);
        closeBtn.setOpaque(true);
        closeBtn.addActionListener(e -> setVisible(false));
        rightBtns.add(closeBtn);

        titleBar.add(rightBtns, BorderLayout.EAST);
        mainPanel.add(titleBar, BorderLayout.NORTH);

        // ---- 搜索输入行 ----
        JPanel inputRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
        inputRow.setOpaque(false);

        pasteBtn = new JButton("粘贴");
        pasteBtn.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        pasteBtn.setForeground(Color.WHITE);
        pasteBtn.setBackground(new Color(0, 102, 255, 200));
        pasteBtn.setBorderPainted(false);
        pasteBtn.setFocusPainted(false);
        pasteBtn.setOpaque(true);
        pasteBtn.addActionListener(e -> pasteFromClipboard());
        inputRow.add(pasteBtn);

        searchField = new JTextField(14);
        searchField.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        searchField.setBackground(new Color(255, 255, 255, 220));
        searchField.setForeground(Color.BLACK);
        searchField.addActionListener(e -> doSearch());
        inputRow.add(searchField);

        searchBtn = new JButton("搜索");
        searchBtn.setFont(new Font("微软雅黑", Font.PLAIN, 11));
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
        statusLabel.setFont(new Font("微软雅黑", Font.PLAIN, 10));
        statusLabel.setForeground(Color.WHITE);
        statusLabel.setBackground(new Color(0, 0, 0, 150));
        statusLabel.setOpaque(true);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 6));
        mainPanel.add(statusLabel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private void initResultWindow() {
        resultWindow = new JWindow();
        resultWindow.setAlwaysOnTop(true);
        resultWindow.setBackground(new Color(0, 0, 0, 200));

        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(new LineBorder(new Color(0, 102, 255, 150), 2));

        resultArea = new JTextArea();
        resultArea.setEditable(false);
        resultArea.setLineWrap(true);
        resultArea.setWrapStyleWord(true);
        resultArea.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        resultArea.setBackground(new Color(0, 0, 0, 150));
        resultArea.setForeground(Color.WHITE);
        resultArea.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        resultScroll = new JScrollPane(resultArea);
        resultScroll.setBorder(null);
        resultScroll.setOpaque(false);
        resultScroll.getViewport().setOpaque(false);
        resultScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        panel.add(resultScroll, BorderLayout.CENTER);

        // 鼠标移出自动隐藏
        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                hideResultDelayed();
            }
        });
        resultWindow.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                hideResultDelayed();
            }
        });

        resultWindow.setContentPane(panel);
        resultWindow.setSize(320, 300);   // 结果窗口尺寸 320x300
        resultWindow.setVisible(false);
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
                        Map<String, String> firstRow = rows.get(0);
                        StringBuilder sb = new StringBuilder();
                        sb.append("📌 共匹配 ").append(rows.size()).append(" 条，显示第 1 条：\n\n");
                        for (Map.Entry<String, String> entry : firstRow.entrySet()) {
                            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                        }
                        resultArea.setText(sb.toString());
                        // 滚动到顶部
                        resultArea.setCaretPosition(0);
                        statusLabel.setText("✅ 找到 " + rows.size() + " 条匹配");
                    }
                    showResultWindow();
                } catch (Exception e) {
                    log.error("搜索失败", e);
                    resultArea.setText("❌ 搜索失败: " + e.getMessage());
                    statusLabel.setText("❌ 搜索失败");
                    showResultWindow();
                }
            }
        }.execute();
    }

    private void showResultWindow() {
        if (resultWindow == null) return;
        Point location = getLocation();
        int x = location.x;
        int y = location.y + getHeight() + 2;
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        if (y + resultWindow.getHeight() > screen.height) {
            y = location.y - resultWindow.getHeight() - 2;
        }
        resultWindow.setLocation(x, y);
        resultWindow.setVisible(true);
        resultVisible = true;

        Timer timer = new Timer(10000, e -> {
            if (resultVisible && resultWindow.getMousePosition() == null) {
                hideResultWindow();
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    private void hideResultWindow() {
        if (resultWindow != null) {
            resultWindow.setVisible(false);
        }
        resultVisible = false;
    }

    private void hideResultDelayed() {
        Timer timer = new Timer(300, e -> {
            if (resultVisible && resultWindow != null && resultWindow.getMousePosition() == null) {
                hideResultWindow();
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    private void switchToFullMode() {
        setVisible(false);
        hideResultWindow();
        SwingUtilities.invokeLater(() -> {
            try {
                Class<?> bootstrap = Class.forName("com.omni.business.assistance.screenshot.bootstrap.ScreenshotAssistantBootstrap");
                java.lang.reflect.Method method = bootstrap.getDeclaredMethod("showFullMode");
                method.setAccessible(true);
                method.invoke(null);
            } catch (Exception ex) {
                log.error("切换完整模式失败", ex);
                FloatingCaptureWindow full = new FloatingCaptureWindow(screenshotService);
                full.setVisible(true);
            }
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
                    if (resultVisible && resultWindow != null) {
                        resultWindow.setLocation(newPos.x, newPos.y + getHeight() + 2);
                    }
                }
            }
        };
        titleBar.addMouseListener(dragAdapter);
        titleBar.addMouseMotionListener(dragAdapter);
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (!visible && resultWindow != null) {
            resultWindow.setVisible(false);
            resultVisible = false;
        }
    }

    @Override
    public void dispose() {
        if (resultWindow != null) {
            resultWindow.dispose();
        }
        super.dispose();
    }
}