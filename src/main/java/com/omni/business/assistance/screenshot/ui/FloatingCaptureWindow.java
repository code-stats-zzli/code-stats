// 文件路径: src/main/java/com/omni/business/assistance/screenshot/ui/FloatingCaptureWindow.java
package com.omni.business.assistance.screenshot.ui;

import com.omni.business.assistance.screenshot.bootstrap.ScreenshotAssistantBootstrap;
import com.omni.business.assistance.screenshot.model.ScreenshotResult;
import com.omni.business.assistance.screenshot.service.ScreenshotService;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 截图助手浮窗 - 完整模式
 * 支持：拖拽识别、选区识别、搜索、复制、保存截图，以及切换到极简模式
 *
 * <p>尺寸优化：默认 600×580 → 540×520，内边距、字体、按钮宽度统一收紧，
 * 减少右下角遮挡面积，同时保证按钮不换行。
 */
public class FloatingCaptureWindow extends JWindow {
    private static final Logger log = LoggerFactory.getLogger(FloatingCaptureWindow.class);
    private final ScreenshotService screenshotService;

    // UI 组件
    private JTextArea recognizedTextArea;
    private JTextArea resultTextArea;
    private JLabel statusLabel;
    private JPanel titleBar;
    private JButton closeButton, copyTextButton, saveScreenshotButton, selectAreaButton, recaptureButton;
    private JButton zoomInButton, zoomOutButton;
    private JButton pasteButton;
    private JButton searchButton;
    private JButton miniModeButton;

    // 状态控制
    private final AtomicBoolean isCapturing = new AtomicBoolean(false);
    private final AtomicBoolean isSelecting = new AtomicBoolean(false);
    private ScreenshotResult lastResult;
    private Timer captureThrottleTimer;
    private Point dragStart = null;
    private boolean wasDragged = false;

    /** ★ 尺寸优化：600×580 → 540×520 */
    private static final int DEFAULT_WIDTH = 540;
    private static final int DEFAULT_HEIGHT = 520;
    private int currentWidth = DEFAULT_WIDTH;
    private int currentHeight = DEFAULT_HEIGHT;

    public FloatingCaptureWindow(ScreenshotService service) {
        this.screenshotService = service;
        setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        setAlwaysOnTop(true);
        setBackground(new Color(0, 0, 0, 100));

        initComponents();
        setupDragAndDrop();
        setupResizeListener();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                setVisible(false);
            }
        });

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                if (lastResult == null) {
                    SwingUtilities.invokeLater(() -> doFloatingCapture());
                }
            }
        });

        log.info("截图助手浮窗初始化完成（尺寸 {}×{}）", DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    // ==================== UI 初始化 ====================

    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(4, 4));
        mainPanel.setOpaque(false);
        mainPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0, 102, 255, 200), 2),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));

        // ---- 标题栏（可拖拽） ----
        titleBar = new JPanel(new BorderLayout());
        titleBar.setOpaque(false);
        titleBar.setBorder(BorderFactory.createEmptyBorder(6, 8, 3, 8));
        JLabel dragHandle = new JLabel("⠿");
        dragHandle.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        dragHandle.setForeground(Color.WHITE);
        dragHandle.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        titleBar.add(dragHandle, BorderLayout.WEST);
        JLabel titleLabel = new JLabel("截图助手");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(new Font("Inter", Font.BOLD, 13));
        titleBar.add(titleLabel, BorderLayout.CENTER);
        mainPanel.add(titleBar, BorderLayout.NORTH);

        // ---- 识别文字展示区 ----
        recognizedTextArea = new JTextArea();
        recognizedTextArea.setEditable(true);
        recognizedTextArea.setLineWrap(true);
        recognizedTextArea.setWrapStyleWord(true);
        recognizedTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        recognizedTextArea.setBackground(new Color(0, 0, 0, 80));
        recognizedTextArea.setForeground(Color.WHITE);
        recognizedTextArea.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(255, 255, 255, 100)), "识别文字（可编辑）",
                TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION,
                new Font("Inter", Font.PLAIN, 11), Color.WHITE
        ));
        recognizedTextArea.setOpaque(true);
        updateTextAreaPreferredSize();
        mainPanel.add(recognizedTextArea, BorderLayout.NORTH);

        // ---- 结果区域（标题栏 + 搜索行 + 文本区） ----
        JPanel resultPanel = new JPanel(new BorderLayout(0, 4));
        resultPanel.setOpaque(false);
        resultPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(255, 255, 255, 100)), "匹配结果",
                TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION,
                new Font("Inter", Font.PLAIN, 11), Color.WHITE
        ));

        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        searchRow.setOpaque(false);

        pasteButton = createSmallButton("粘贴并搜索", new Color(60, 60, 60, 200));
        pasteButton.setToolTipText("从剪贴板粘贴内容到识别文字区，并自动搜索");
        pasteButton.addActionListener(e -> pasteAndSearch());
        searchRow.add(pasteButton);

        miniModeButton = createSmallButton("极简", new Color(60, 60, 60, 200));
        miniModeButton.setToolTipText("切换到极简模式");
        miniModeButton.addActionListener(e -> {
            ScreenshotAssistantBootstrap.showMiniMode();
            setVisible(false);
        });
        searchRow.add(miniModeButton);

        searchButton = createSmallButton("搜索", new Color(0, 102, 255, 230));
        searchButton.setToolTipText("使用当前识别文字搜索题库");
        searchButton.addActionListener(e -> searchByKeyword());
        searchRow.add(searchButton);

        copyTextButton = createSmallButton("复制文本", new Color(60, 60, 60, 200));
        copyTextButton.setToolTipText("复制识别文字");
        copyTextButton.addActionListener(e -> copyRecognizedText());
        searchRow.add(copyTextButton);

        closeButton = createSmallButton("关闭", new Color(60, 60, 60, 200));
        closeButton.addActionListener(e -> setVisible(false));
        searchRow.add(closeButton);

        resultPanel.add(searchRow, BorderLayout.NORTH);

        resultTextArea = new JTextArea();
        resultTextArea.setEditable(false);
        resultTextArea.setLineWrap(true);
        resultTextArea.setWrapStyleWord(true);
        resultTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        resultTextArea.setBackground(new Color(0, 0, 0, 150));
        resultTextArea.setForeground(Color.WHITE);
        resultTextArea.setOpaque(true);

        JScrollPane resultScroll = new JScrollPane(resultTextArea);
        resultScroll.setBorder(null);
        resultScroll.setOpaque(false);
        resultScroll.getViewport().setOpaque(false);
        resultPanel.add(resultScroll, BorderLayout.CENTER);

        mainPanel.add(resultPanel, BorderLayout.CENTER);

        // ---- 底部面板（状态行 + 功能按钮行） ----
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(3, 6, 6, 6));

        JPanel statusRow = new JPanel(new BorderLayout());
        statusRow.setOpaque(false);
        statusLabel = new JLabel("就绪");
        statusLabel.setFont(new Font("Inter", Font.PLAIN, 11));
        statusLabel.setForeground(Color.WHITE);
        statusLabel.setBackground(new Color(0, 0, 0, 180));
        statusLabel.setOpaque(true);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        statusRow.add(statusLabel, BorderLayout.WEST);
        bottomPanel.add(statusRow);
        bottomPanel.add(Box.createVerticalStrut(3));

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttonRow.setOpaque(false);

        zoomOutButton = createSmallButton("缩小", new Color(60, 60, 60, 200));
        zoomOutButton.setToolTipText("缩小浮窗（10%）");
        zoomOutButton.addActionListener(e -> zoomWindow(0.9));
        buttonRow.add(zoomOutButton);

        zoomInButton = createSmallButton("放大", new Color(60, 60, 60, 200));
        zoomInButton.setToolTipText("放大浮窗（10%）");
        zoomInButton.addActionListener(e -> zoomWindow(1.1));
        buttonRow.add(zoomInButton);

        selectAreaButton = createSmallButton("选区识别", new Color(60, 60, 60, 200));
        selectAreaButton.setToolTipText("在屏幕上拖拽选择任意区域进行识别");
        selectAreaButton.addActionListener(e -> startAreaSelection());
        buttonRow.add(selectAreaButton);

        recaptureButton = createSmallButton("重新识别", new Color(60, 60, 60, 200));
        recaptureButton.setToolTipText("重新识别当前浮窗内文字区域的内容");
        recaptureButton.addActionListener(e -> doFloatingCapture());
        buttonRow.add(recaptureButton);

        saveScreenshotButton = createSmallButton("保存截图", new Color(60, 60, 60, 200));
        saveScreenshotButton.setToolTipText("将本次识别的屏幕截图保存到目录（默认复制到剪贴板）");
        saveScreenshotButton.addActionListener(e -> saveScreenshotImage());
        buttonRow.add(saveScreenshotButton);

        bottomPanel.add(buttonRow);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private JButton createSmallButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Inter", Font.PLAIN, 11));
        btn.setForeground(Color.WHITE);
        btn.setBackground(bg);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setOpaque(true);
        return btn;
    }

    // ==================== 粘贴并自动搜索 ====================

    private void pasteAndSearch() {
        try {
            String clipboardText = Toolkit.getDefaultToolkit().getSystemClipboard()
                    .getData(java.awt.datatransfer.DataFlavor.stringFlavor)
                    .toString();
            if (clipboardText != null && !clipboardText.isEmpty()) {
                recognizedTextArea.setText(clipboardText);
                searchByKeyword();
            } else {
                statusLabel.setText("剪贴板为空");
            }
        } catch (Exception e) {
            log.warn("读取剪贴板失败", e);
            statusLabel.setText("无法读取剪贴板");
        }
    }

    // ==================== 搜索（基于识别文字区域） ====================

    private void searchByKeyword() {
        String keyword = recognizedTextArea.getText().trim();
        if (keyword.isEmpty()) {
            statusLabel.setText("识别文字为空，请输入或粘贴内容");
            return;
        }
        statusLabel.setText("搜索中...");
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
                        resultTextArea.setText("未找到匹配的题目");
                        statusLabel.setText("无匹配");
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
                        resultTextArea.setText(sb.toString());
                        statusLabel.setText("找到 " + rows.size() + " 条匹配");
                    }
                } catch (Exception e) {
                    log.error("搜索失败", e);
                    resultTextArea.setText("搜索失败: " + e.getMessage());
                    statusLabel.setText("搜索失败");
                }
            }
        }.execute();
    }

    // ==================== 复制识别文字 ====================

    private void copyRecognizedText() {
        String text = recognizedTextArea.getText();
        if (text == null || text.trim().isEmpty()) {
            statusLabel.setText("没有可复制的文字");
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                new java.awt.datatransfer.StringSelection(text), null
        );
        statusLabel.setText("已复制文字");
    }

    // ==================== 窗口缩放 ====================

    private void zoomWindow(double factor) {
        int newWidth = (int) Math.round(currentWidth * factor);
        int newHeight = (int) Math.round(currentHeight * factor);
        if (newWidth < 300 || newHeight < 280) {
            statusLabel.setText("已到最小尺寸");
            return;
        }
        if (newWidth > 1200 || newHeight > 900) {
            statusLabel.setText("已到最大尺寸");
            return;
        }
        currentWidth = newWidth;
        currentHeight = newHeight;
        setSize(currentWidth, currentHeight);
        updateTextAreaPreferredSize();
        revalidate();
        repaint();
        statusLabel.setText("缩放至 " + currentWidth + "×" + currentHeight);
    }

    private void updateTextAreaPreferredSize() {
        int textAreaHeight = Math.max(60, (int) (currentHeight * 0.12));
        recognizedTextArea.setPreferredSize(new Dimension(currentWidth - 20, textAreaHeight));
        recognizedTextArea.setRows(Math.max(2, textAreaHeight / 20));
    }

    // ==================== 拖拽支持 ====================

    private void setupDragAndDrop() {
        MouseAdapter dragAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragStart = e.getPoint();
                wasDragged = false;
                titleBar.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(0, 255, 255), 4),
                        BorderFactory.createEmptyBorder(2, 2, 2, 2)
                ));
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (dragStart != null && wasDragged) {
                    triggerDelayedFloatingCapture();
                }
                dragStart = null;
                wasDragged = false;
                titleBar.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(0, 102, 255, 200), 3),
                        BorderFactory.createEmptyBorder(2, 2, 2, 2)
                ));
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart != null) {
                    Point newPos = getLocation();
                    newPos.translate(e.getX() - dragStart.x, e.getY() - dragStart.y);
                    setLocation(newPos);
                    wasDragged = true;
                }
            }
        };
        for (Component comp : titleBar.getComponents()) {
            comp.addMouseListener(dragAdapter);
            comp.addMouseMotionListener(dragAdapter);
        }
        getContentPane().addMouseListener(dragAdapter);
        getContentPane().addMouseMotionListener(dragAdapter);
    }

    // ==================== 浮窗模式识别（防抖） ====================

    private void triggerDelayedFloatingCapture() {
        if (captureThrottleTimer != null && captureThrottleTimer.isRunning()) {
            captureThrottleTimer.restart();
        } else {
            captureThrottleTimer = new Timer(300, e -> {
                if (!isCapturing.get()) {
                    doFloatingCapture();
                }
            });
            captureThrottleTimer.setRepeats(false);
            captureThrottleTimer.start();
        }
    }

    private void doFloatingCapture() {
        if (isCapturing.compareAndSet(false, true)) {
            SwingUtilities.invokeLater(() -> {
                recognizedTextArea.setText("");
                recognizedTextArea.setBackground(new Color(0, 0, 0, 80));
                recognizedTextArea.repaint();
            });

            statusLabel.setText("截取文字区域...");
            log.debug("浮窗模式：截取识别文字区域（内缩5px）");

            Point locationOnScreen = recognizedTextArea.getLocationOnScreen();
            int inset = 5;
            Rectangle rect = new Rectangle(
                    locationOnScreen.x + inset,
                    locationOnScreen.y + inset,
                    recognizedTextArea.getWidth() - inset * 2,
                    recognizedTextArea.getHeight() - inset * 2
            );

            BufferedImage capturedImage = null;
            try {
                Robot robot = new Robot();
                capturedImage = robot.createScreenCapture(rect);
            } catch (AWTException e) {
                log.error("截图文字区域失败", e);
                statusLabel.setText("截图失败");
                isCapturing.set(false);
                return;
            }

            performRecognition(capturedImage, "文字区域");
        }
    }

    // ==================== 选区模式 ====================

    public void startAreaSelection() {
        if (isSelecting.get()) return;
        isSelecting.set(true);

        JWindow overlay = new JWindow();
        overlay.setAlwaysOnTop(true);
        overlay.setBackground(new Color(0, 0, 0, 150));
        overlay.setSize(Toolkit.getDefaultToolkit().getScreenSize());
        overlay.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

        final Rectangle selection = new Rectangle();
        final Point[] start = new Point[1];

        JPanel drawPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (selection.width > 0 && selection.height > 0) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setColor(new Color(0, 102, 255, 80));
                    g2.fill(selection);
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(2));
                    g2.draw(selection);
                    String size = selection.width + " × " + selection.height;
                    g2.setFont(new Font("Inter", Font.BOLD, 14));
                    g2.drawString(size, selection.x + 8, selection.y + 24);
                }
            }
        };
        drawPanel.setOpaque(false);
        overlay.add(drawPanel);

        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                start[0] = e.getPoint();
                selection.setBounds(start[0].x, start[0].y, 0, 0);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (start[0] != null) {
                    int x = Math.min(start[0].x, e.getX());
                    int y = Math.min(start[0].y, e.getY());
                    int w = Math.abs(e.getX() - start[0].x);
                    int h = Math.abs(e.getY() - start[0].y);
                    selection.setBounds(x, y, w, h);
                    drawPanel.repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                overlay.dispose();
                isSelecting.set(false);
                if (selection.width > 5 && selection.height > 5) {
                    doAreaCapture(selection);
                } else {
                    statusLabel.setText("选区太小，已取消");
                }
            }
        };
        drawPanel.addMouseListener(adapter);
        drawPanel.addMouseMotionListener(adapter);

        KeyAdapter escAdapter = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    overlay.dispose();
                    isSelecting.set(false);
                    statusLabel.setText("已取消选区");
                }
            }
        };
        overlay.addKeyListener(escAdapter);
        overlay.setFocusable(true);
        overlay.requestFocus();

        overlay.setVisible(true);
    }

    private void doAreaCapture(Rectangle rect) {
        if (isCapturing.compareAndSet(false, true)) {
            SwingUtilities.invokeLater(() -> {
                recognizedTextArea.setText("");
                recognizedTextArea.setBackground(new Color(0, 0, 0, 80));
                recognizedTextArea.repaint();
            });

            statusLabel.setText("截取选区...");
            log.debug("选区模式截图: {}", rect);

            try {
                Robot robot = new Robot();
                BufferedImage capturedImage = robot.createScreenCapture(rect);
                performRecognition(capturedImage, "选区");
            } catch (AWTException e) {
                log.error("选区截图失败", e);
                statusLabel.setText("选区截图失败");
                isCapturing.set(false);
            }
        }
    }

    // ==================== 公共识别逻辑 ====================

    private void performRecognition(BufferedImage image, String sourceDesc) {
        if (image == null) {
            statusLabel.setText("无效图像");
            isCapturing.set(false);
            return;
        }

        statusLabel.setText("识别中 (" + sourceDesc + ")...");
        new SwingWorker<ScreenshotResult, Void>() {
            @Override
            protected ScreenshotResult doInBackground() throws Exception {
                ScreenshotResult result = screenshotService.captureAndSearchWithImage(image);
                log.debug("识别原始文本: {}", result.getRecognizedText());
                return result;
            }

            @Override
            protected void done() {
                try {
                    ScreenshotResult result = get();
                    lastResult = result;
                    recognizedTextArea.setBackground(new Color(0, 0, 0, 80));
                    String text = result.getRecognizedText();
                    recognizedTextArea.setText(text == null || text.trim().isEmpty() ? "（未识别到文字）" : text);
                    recognizedTextArea.repaint();

                    updateResultText(result);

                    if (result.isFound()) {
                        statusLabel.setText("找到匹配 (" + sourceDesc + ")");
                    } else {
                        statusLabel.setText("未匹配 (" + sourceDesc + ")");
                    }
                } catch (Exception e) {
                    log.error("识别异常", e);
                    recognizedTextArea.setText("识别失败: " + e.getMessage());
                    statusLabel.setText("识别失败");
                } finally {
                    isCapturing.set(false);
                }
            }
        }.execute();
    }

    // ==================== UI 更新辅助 ====================

    private void updateResultText(ScreenshotResult result) {
        if (!result.isFound() || result.getMatchedRows() == null || result.getMatchedRows().isEmpty()) {
            resultTextArea.setText("未找到匹配的题目");
            return;
        }
        List<String> columns = result.getColumns();
        if (columns == null || columns.isEmpty()) {
            resultTextArea.setText("无列信息");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String col : columns) {
            sb.append("【").append(col).append("】");
        }
        sb.append("\n");
        sb.append("=".repeat(60)).append("\n");
        for (Map<String, String> row : result.getMatchedRows()) {
            for (String col : columns) {
                String val = row.getOrDefault(col, "");
                sb.append(col).append(": ").append(val).append("\n");
            }
            sb.append("---\n");
        }
        resultTextArea.setText(sb.toString());
    }

    // ==================== 保存截图 ====================

    private void saveScreenshotImage() {
        if (lastResult == null || lastResult.getImage() == null) {
            statusLabel.setText("没有可保存的截图");
            return;
        }
        try {
            File saveDir = screenshotService.getSaveDirectory();
            if (!saveDir.exists() && !saveDir.mkdirs()) {
                statusLabel.setText("无法创建截图目录");
                return;
            }
            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
            File outputFile = new File(saveDir, "screenshot_" + timestamp + ".png");
            BufferedImage image = lastResult.getImage();
            ImageIO.write(image, "png", outputFile);
            statusLabel.setText("截图已保存: " + outputFile.getName());
            log.info("截图保存成功: {}", outputFile.getAbsolutePath());
        } catch (Exception e) {
            statusLabel.setText("保存截图失败: " + e.getMessage());
            log.error("保存截图失败", e);
        }
    }

    // ==================== 其他 ====================

    private void setupResizeListener() {
        // 由缩放按钮控制，不自动调整
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible && lastResult == null) {
            SwingUtilities.invokeLater(() -> doFloatingCapture());
        }
    }
}