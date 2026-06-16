package com.omni.business.assistance.screenshot.ui;

import com.omni.business.assistance.screenshot.model.ScreenshotResult;
import com.omni.business.assistance.screenshot.service.ScreenshotService;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * 深色半透明浮窗截图助手
 * - 背景深色半透明，可看到屏幕内容，文字清晰
 * - 上半部分显示识别文字（半透明背景）
 * - 下半部分显示匹配结果表格（半透明背景）
 * - 标题栏包含拖拽手柄和标题
 * - 底部包含三个操作按钮：复制文字、保存截图、关闭
 * - 拖拽时窗口边框高亮，移动过程中自动识别
 * - 保存截图保存的是OCR识别的原始屏幕区域图片，而非浮窗UI
 */
public class FloatingCaptureWindow extends JWindow {

    private static final Logger log = LoggerFactory.getLogger(FloatingCaptureWindow.class);

    private final ScreenshotService screenshotService;
    private ScreenshotResult lastResult;

    // 界面组件
    private JTextArea recognizedTextArea;
    private JTable resultTable;
    private DefaultTableModel tableModel;
    private JLabel statusLabel;

    // 标题栏组件
    private JPanel titleBar;
    private JLabel dragHandle;

    // 底部按钮
    private JButton closeButton;
    private JButton copyTextButton;
    private JButton saveScreenshotButton;

    // 拖动相关
    private Point dragStart = null;
    private boolean wasDragged = false;

    // 识别防重入
    private boolean isCapturing = false;

    // 节流定时器
    private Timer captureThrottleTimer;

    private static final int DEFAULT_WIDTH = 450;
    private static final int DEFAULT_HEIGHT = 420;

    public FloatingCaptureWindow(ScreenshotService service) {
        this.screenshotService = service;
        setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        setAlwaysOnTop(true);
        // 深色半透明背景
        setBackground(new Color(0, 0, 0, 200));

        initComponents();
        setupResizeToCapture();

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
                    SwingUtilities.invokeLater(() -> doCapture());
                }
            }
        });

        log.info("截图助手浮窗初始化完成（深色半透明版，底部按钮，保存截图保存原始图片）");
    }

    private void initComponents() {
        // 主面板：背景透明，边框发光
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setOpaque(false);
        mainPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0, 102, 255, 200), 3),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));

        // ---------- 标题栏（仅拖拽手柄和标题） ----------
        titleBar = new JPanel(new BorderLayout());
        titleBar.setOpaque(false);
        titleBar.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));

        // 左侧：拖拽手柄
        dragHandle = new JLabel("⠿");
        dragHandle.setFont(new Font("Segoe UI", Font.PLAIN, 20));
        dragHandle.setForeground(Color.WHITE);
        dragHandle.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        titleBar.add(dragHandle, BorderLayout.WEST);

        // 中间：标题
        JLabel titleLabel = new JLabel("📷 截图助手");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(new Font("Inter", Font.BOLD, 14));
        titleBar.add(titleLabel, BorderLayout.CENTER);

        mainPanel.add(titleBar, BorderLayout.NORTH);

        // ---------- 识别文字区域（半透明深色背景） ----------
        recognizedTextArea = new JTextArea();
        recognizedTextArea.setEditable(false);
        recognizedTextArea.setLineWrap(true);
        recognizedTextArea.setWrapStyleWord(true);
        recognizedTextArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        recognizedTextArea.setBackground(new Color(0, 0, 0, 150));
        recognizedTextArea.setForeground(Color.WHITE);
        recognizedTextArea.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(255, 255, 255, 100)), "📝 识别文字",
                TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION,
                new Font("Inter", Font.PLAIN, 12), Color.WHITE
        ));
        recognizedTextArea.setOpaque(true);
        recognizedTextArea.setPreferredSize(new Dimension(getWidth(), 60));
        mainPanel.add(recognizedTextArea, BorderLayout.NORTH);

        // ---------- 结果表格（半透明深色背景） ----------
        tableModel = new DefaultTableModel();
        resultTable = new JTable(tableModel);
        resultTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        resultTable.setFillsViewportHeight(true);
        resultTable.setBackground(new Color(0, 0, 0, 150));
        resultTable.setForeground(Color.WHITE);
        resultTable.setGridColor(new Color(255, 255, 255, 50));
        resultTable.setShowGrid(true);
        resultTable.getTableHeader().setBackground(new Color(0, 0, 0, 150));
        resultTable.getTableHeader().setForeground(Color.WHITE);
        resultTable.getTableHeader().setOpaque(true);
        // 单元格渲染器（保持半透明）
        resultTable.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                c.setBackground(new Color(0, 0, 0, 150));
                c.setForeground(Color.WHITE);
                if (isSelected) {
                    c.setBackground(new Color(0, 102, 255, 180));
                }
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(resultTable);
        scrollPane.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(255, 255, 255, 100)), "📚 匹配结果",
                TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION,
                new Font("Inter", Font.PLAIN, 12), Color.WHITE
        ));
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBackground(new Color(0, 0, 0, 0));
        scrollPane.getViewport().setBackground(new Color(0, 0, 0, 0));
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // ---------- 底部面板：状态标签 + 三个按钮 ----------
        JPanel bottomPanel = new JPanel(new BorderLayout(4, 4));
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));

        // 状态标签
        statusLabel = new JLabel("就绪");
        statusLabel.setFont(new Font("Inter", Font.PLAIN, 12));
        statusLabel.setForeground(Color.WHITE);
        statusLabel.setBackground(new Color(0, 0, 0, 180));
        statusLabel.setOpaque(true);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        bottomPanel.add(statusLabel, BorderLayout.WEST);

        // 右侧按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttonPanel.setOpaque(false);

        // 复制文字按钮
        copyTextButton = new JButton("📋 复制文字");
        copyTextButton.setToolTipText("复制识别文字");
        copyTextButton.setFont(new Font("Inter", Font.PLAIN, 12));
        copyTextButton.setForeground(Color.WHITE);
        copyTextButton.setBackground(new Color(60, 60, 60, 200));
        copyTextButton.setBorderPainted(false);
        copyTextButton.setFocusPainted(false);
        copyTextButton.setOpaque(true);
        copyTextButton.addActionListener(e -> copyRecognizedText());
        buttonPanel.add(copyTextButton);

        // 保存截图按钮（保存原始识别图片）
        saveScreenshotButton = new JButton("💾 保存截图");
        saveScreenshotButton.setToolTipText("保存本次识别的屏幕截图（原始图片）");
        saveScreenshotButton.setFont(new Font("Inter", Font.PLAIN, 12));
        saveScreenshotButton.setForeground(Color.WHITE);
        saveScreenshotButton.setBackground(new Color(60, 60, 60, 200));
        saveScreenshotButton.setBorderPainted(false);
        saveScreenshotButton.setFocusPainted(false);
        saveScreenshotButton.setOpaque(true);
        saveScreenshotButton.addActionListener(e -> saveScreenshotImage());
        buttonPanel.add(saveScreenshotButton);

        // 关闭按钮
        closeButton = new JButton("✖ 关闭");
        closeButton.setFont(new Font("Inter", Font.PLAIN, 12));
        closeButton.setForeground(Color.WHITE);
        closeButton.setBackground(new Color(60, 60, 60, 200));
        closeButton.setBorderPainted(false);
        closeButton.setFocusPainted(false);
        closeButton.setOpaque(true);
        closeButton.addActionListener(e -> {
            log.info("用户点击关闭按钮，隐藏浮窗");
            setVisible(false);
        });
        buttonPanel.add(closeButton);

        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);

        // ---- 标题栏和主面板拖动支持 ----
        MouseAdapter dragAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragStart = e.getPoint();
                wasDragged = false;
                mainPanel.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(0, 255, 255), 4),
                        BorderFactory.createEmptyBorder(2, 2, 2, 2)
                ));
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (dragStart != null && wasDragged) {
                    triggerDelayedCapture();
                }
                dragStart = null;
                wasDragged = false;
                mainPanel.setBorder(BorderFactory.createCompoundBorder(
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
                    triggerRealTimeCapture();
                }
            }
        };

        // 标题栏所有组件支持拖动
        for (Component comp : titleBar.getComponents()) {
            comp.addMouseListener(dragAdapter);
            comp.addMouseMotionListener(dragAdapter);
        }
        // 主面板也支持拖动
        mainPanel.addMouseListener(dragAdapter);
        mainPanel.addMouseMotionListener(dragAdapter);
    }

    // ---- 调整大小触发识别 ----
    private void setupResizeToCapture() {
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                triggerDelayedCapture();
            }
        });
    }

    // ---- 防抖定时器 ----
    private void triggerDelayedCapture() {
        if (captureThrottleTimer != null && captureThrottleTimer.isRunning()) {
            captureThrottleTimer.restart();
        } else {
            captureThrottleTimer = new Timer(300, e -> {
                if (!isCapturing) {
                    doCapture();
                }
            });
            captureThrottleTimer.setRepeats(false);
            captureThrottleTimer.start();
        }
    }

    // ---- 实时识别节流 ----
    private long lastRealTimeCapture = 0;
    private static final long REAL_TIME_INTERVAL = 200;

    private void triggerRealTimeCapture() {
        long now = System.currentTimeMillis();
        if (now - lastRealTimeCapture > REAL_TIME_INTERVAL && !isCapturing) {
            lastRealTimeCapture = now;
            doCapture();
        }
    }

    // ---- 执行截图识别 ----
    private void doCapture() {
        if (isCapturing) return;
        isCapturing = true;
        statusLabel.setText("⏳ 识别中...");
        log.info("触发截图识别，浮窗位置: ({}, {})", getX(), getY());

        new SwingWorker<ScreenshotResult, Void>() {
            @Override
            protected ScreenshotResult doInBackground() throws Exception {
                Rectangle bounds = getBounds();
                return screenshotService.captureAndSearch(bounds);
            }

            @Override
            protected void done() {
                isCapturing = false;
                try {
                    ScreenshotResult result = get();
                    lastResult = result;
                    updateUI(result);
                    statusLabel.setText(result.isFound() ? "✅ 找到匹配" : "😞 未匹配");
                    log.info("截图识别完成，匹配结果: {}", result.isFound());
                } catch (Exception e) {
                    statusLabel.setText("❌ 识别失败: " + e.getMessage());
                    log.error("截图识别异常", e);
                }
            }
        }.execute();
    }

    // ---- 更新界面 ----
    private void updateUI(ScreenshotResult result) {
        String text = result.getRecognizedText();
        if (text == null || text.trim().isEmpty()) {
            recognizedTextArea.setText("（未识别到文字）");
        } else {
            recognizedTextArea.setText(text);
        }

        tableModel.setRowCount(0);
        tableModel.setColumnCount(0);

        if (!result.isFound() || result.getMatchedRows() == null || result.getMatchedRows().isEmpty()) {
            tableModel.setColumnCount(1);
            tableModel.addRow(new Object[]{"😞 未找到匹配的题目"});
            return;
        }

        List<String> columns = result.getColumns();
        if (columns == null || columns.isEmpty()) {
            tableModel.setColumnCount(1);
            tableModel.addRow(new Object[]{"无列信息"});
            return;
        }

        for (String col : columns) {
            tableModel.addColumn(col);
        }

        for (Map<String, String> row : result.getMatchedRows()) {
            Object[] rowData = new Object[columns.size()];
            for (int i = 0; i < columns.size(); i++) {
                rowData[i] = row.getOrDefault(columns.get(i), "");
            }
            tableModel.addRow(rowData);
        }
    }

    // ---- 复制识别文字 ----
    private void copyRecognizedText() {
        String text = recognizedTextArea.getText();
        if (text == null || text.trim().isEmpty()) {
            statusLabel.setText("⚠️ 没有可复制的文字");
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                new java.awt.datatransfer.StringSelection(text), null
        );
        statusLabel.setText("✅ 已复制文字");
        log.info("复制文字成功，长度: {}", text.length());
    }

    // ---- 保存本次识别的原始屏幕截图（图片） ----
    private void saveScreenshotImage() {
        if (lastResult == null || lastResult.getImage() == null) {
            statusLabel.setText("⚠️ 没有可保存的截图，请先识别");
            return;
        }
        try {
            File saveDir = screenshotService.getSaveDirectory();
            if (!saveDir.exists() && !saveDir.mkdirs()) {
                statusLabel.setText("❌ 无法创建截图目录");
                return;
            }
            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
            File outputFile = new File(saveDir, "screenshot_region_" + timestamp + ".png");

            // ★ 关键：保存 lastResult.getImage()，即OCR截取的原始屏幕区域
            BufferedImage image = lastResult.getImage();
            ImageIO.write(image, "png", outputFile);

            statusLabel.setText("✅ 截图已保存: " + outputFile.getName());
            log.info("保存识别区域截图成功: {}", outputFile.getAbsolutePath());
        } catch (Exception e) {
            statusLabel.setText("❌ 保存截图失败: " + e.getMessage());
            log.error("保存截图失败", e);
        }
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible && lastResult == null) {
            SwingUtilities.invokeLater(() -> doCapture());
        }
        log.info("浮窗显示状态: {}", visible);
    }
}