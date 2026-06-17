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
 * - 默认窗口缩小（240x60），只显示“完整”和“关闭”两个按钮
 * - 鼠标悬浮时窗口扩展（320x140），显示完整控件
 * - 窗口始终保持右下角对齐（边距 30/50）
 * - 结果窗口宽度与主窗口一致，紧贴主窗口**上方**
 * - 鼠标移出主窗口（且主窗口处于展开状态）且未进入结果窗口时，结果窗口立即消失，主窗口恢复缩小
 */
public class MiniCaptureWindow extends JWindow {
    private static final Logger log = LoggerFactory.getLogger(MiniCaptureWindow.class);

    private static final int MARGIN_RIGHT = 30;
    private static final int MARGIN_BOTTOM = 50;

    private final ScreenshotService screenshotService;
    private JPanel mainPanel;
    private JPanel titleBar;
    private JTextField searchField;
    private JButton pasteBtn, searchBtn, fullModeBtn, closeBtn;
    private JWindow resultWindow;
    private JTextArea resultArea;
    private JScrollPane resultScroll;
    private JLabel statusLabel;

    private JPanel inputRow;
    private boolean resultVisible = false;

    private Timer exitTimer;
    private boolean mouseInsideMain = false;

    private static final int DEFAULT_WIDTH = 320;
    private static final int DEFAULT_HEIGHT = 140;
    private static final int MINI_WIDTH = 240;
    private static final int MINI_HEIGHT = 60;

    public MiniCaptureWindow(ScreenshotService service) {
        this.screenshotService = service;
        initUI();
        initResultWindow();
        setupDrag();
        setupHoverBehavior();

        setSize(MINI_WIDTH, MINI_HEIGHT);
        updateLocation();
        setAlwaysOnTop(true);
        setBackground(new Color(0, 0, 0, 100));
        setVisible(false);
        log.info("极简模式初始化完成，结果窗口显示在上方");
    }

    private void initUI() {
        mainPanel = new JPanel(new BorderLayout(2, 2));
        mainPanel.setOpaque(false);
        mainPanel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(0, 102, 255, 200), 2, true),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));

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

        inputRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
        inputRow.setOpaque(false);
        inputRow.setVisible(false);

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

        statusLabel = new JLabel("就绪");
        statusLabel.setFont(new Font("微软雅黑", Font.PLAIN, 10));
        statusLabel.setForeground(Color.WHITE);
        statusLabel.setBackground(new Color(0, 0, 0, 150));
        statusLabel.setOpaque(true);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 6));
        statusLabel.setVisible(false);
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

        resultWindow.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (exitTimer != null) {
                    exitTimer.stop();
                    exitTimer = null;
                }
                if (!resultWindow.isVisible()) {
                    resultWindow.setVisible(true);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (resultWindow != null) {
                    resultWindow.setVisible(false);
                }
                if (!mouseInsideMain && inputRow.isVisible()) {
                    showFullModeUI(false);
                }
            }
        });

        resultWindow.setContentPane(panel);
        resultWindow.setSize(320, 300);
        resultWindow.setVisible(false);
    }

    // ==================== 位置管理 ====================

    private void updateLocation() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int w = getWidth();
        int h = getHeight();
        int x = screen.width - w - MARGIN_RIGHT;
        int y = screen.height - h - MARGIN_BOTTOM;
        setLocation(x, y);
        if (resultVisible && resultWindow != null) {
            updateResultWindowLocation();
        }
    }

    /**
     * 将结果窗口定位到主窗口的正上方，宽度与主窗口一致
     */
    private void updateResultWindowLocation() {
        if (resultWindow == null) return;
        Point loc = getLocation();
        int w = getWidth();
        int h = getHeight();
        resultWindow.setSize(w, resultWindow.getHeight());
        // ★ 显示在主窗口上方，紧贴主窗口上边界
        resultWindow.setLocation(loc.x, loc.y - resultWindow.getHeight() - 2);
    }

    @Override
    public void setSize(int width, int height) {
        super.setSize(width, height);
        SwingUtilities.invokeLater(this::updateLocation);
    }

    // ==================== 鼠标悬浮控制 ====================

    private void setupHoverBehavior() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                mouseInsideMain = true;
                if (exitTimer != null) {
                    exitTimer.stop();
                    exitTimer = null;
                }
                if (!inputRow.isVisible()) {
                    showFullModeUI(true);
                }
                if (resultVisible && resultWindow != null && !resultWindow.isVisible()) {
                    resultWindow.setVisible(true);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                // 使用 MouseInfo 检查鼠标是否真的离开窗口边界
                Point mousePos = MouseInfo.getPointerInfo().getLocation();
                if (getBounds().contains(mousePos)) {
                    return;
                }

                mouseInsideMain = false;
                if (!inputRow.isVisible()) {
                    return;
                }

                if (exitTimer != null) {
                    exitTimer.stop();
                    exitTimer = null;
                }
                exitTimer = new Timer(50, ev -> {
                    if (resultWindow != null) {
                        Point mp = MouseInfo.getPointerInfo().getLocation();
                        if (resultWindow.getBounds().contains(mp)) {
                            return;
                        }
                    }
                    if (resultWindow != null && resultWindow.isVisible()) {
                        resultWindow.setVisible(false);
                    }
                    showFullModeUI(false);
                    exitTimer = null;
                });
                exitTimer.setRepeats(false);
                exitTimer.start();
            }
        });
    }

    private void showFullModeUI(boolean show) {
        inputRow.setVisible(show);
        statusLabel.setVisible(show);

        if (show) {
            setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        } else {
            setSize(MINI_WIDTH, MINI_HEIGHT);
            if (resultVisible && resultWindow != null) {
                resultWindow.setVisible(false);
            }
        }
    }

    // ==================== 原有功能 ====================

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
        // 设置宽度与主窗口一致
        resultWindow.setSize(getWidth(), resultWindow.getHeight());
        updateResultWindowLocation();
        resultWindow.setVisible(true);
        resultVisible = true;
    }

    private void hideResultWindow() {
        if (resultWindow != null) {
            resultWindow.setVisible(false);
        }
        resultVisible = false;
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
                        updateResultWindowLocation();
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
        if (visible) {
            showFullModeUI(false);
            mouseInsideMain = false;
            if (exitTimer != null) {
                exitTimer.stop();
                exitTimer = null;
            }
            SwingUtilities.invokeLater(this::updateLocation);
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