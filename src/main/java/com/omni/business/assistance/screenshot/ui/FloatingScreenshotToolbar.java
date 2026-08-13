// 文件路径: src/main/java/com/omni/business/assistance/screenshot/ui/FloatingScreenshotToolbar.java
package com.omni.business.assistance.screenshot.ui;

import com.omni.business.assistance.screenshot.service.ScreenshotService;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;

public class FloatingScreenshotToolbar extends JWindow {
    private static final Logger log = LoggerFactory.getLogger(FloatingScreenshotToolbar.class);
    private final ScreenshotService screenshotService;
    private JPanel contentPanel;
    private Point dragStart = null;

    public FloatingScreenshotToolbar(ScreenshotService service) {
        this.screenshotService = service;
        initUI();
        setupDrag();
        setSize(360, 60);
        setAlwaysOnTop(true);
        setBackground(new Color(0, 0, 0, 0));
    }

    private void initUI() {
        contentPanel = new JPanel();
        contentPanel.setLayout(new BorderLayout(5, 0));
        contentPanel.setBackground(new Color(30, 40, 60, 230));
        contentPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0, 102, 255, 200), 2),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));

        JLabel titleLabel = new JLabel("📷 截图助手");
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 14));
        titleLabel.setForeground(Color.WHITE);
        contentPanel.add(titleLabel, BorderLayout.WEST);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        btnPanel.setOpaque(false);

        JButton fullBtn = new JButton("全屏");
        fullBtn.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        fullBtn.setBackground(new Color(0, 102, 255));
        fullBtn.setForeground(Color.WHITE);
        fullBtn.setFocusPainted(false);
        fullBtn.setBorderPainted(false);
        fullBtn.setOpaque(true);
        fullBtn.setPreferredSize(new Dimension(55, 28));
        fullBtn.addActionListener(e -> doFullScreenCapture());
        btnPanel.add(fullBtn);

        JButton realtimeBtn = new JButton("实时全屏");
        realtimeBtn.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        realtimeBtn.setBackground(new Color(0, 200, 0));
        realtimeBtn.setForeground(Color.WHITE);
        realtimeBtn.setFocusPainted(false);
        realtimeBtn.setBorderPainted(false);
        realtimeBtn.setOpaque(true);
        realtimeBtn.setPreferredSize(new Dimension(80, 28));
        realtimeBtn.addActionListener(e -> startRealtimeCapture());
        btnPanel.add(realtimeBtn);

        JButton areaBtn = new JButton("区域");
        areaBtn.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        areaBtn.setBackground(new Color(255, 165, 0));
        areaBtn.setForeground(Color.WHITE);
        areaBtn.setFocusPainted(false);
        areaBtn.setBorderPainted(false);
        areaBtn.setOpaque(true);
        areaBtn.setPreferredSize(new Dimension(55, 28));
        areaBtn.addActionListener(e -> doAreaCapture());
        btnPanel.add(areaBtn);

        contentPanel.add(btnPanel, BorderLayout.CENTER);

        JButton closeBtn = new JButton("✖");
        closeBtn.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        closeBtn.setBackground(new Color(200, 50, 50));
        closeBtn.setForeground(Color.WHITE);
        closeBtn.setFocusPainted(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setOpaque(true);
        closeBtn.setPreferredSize(new Dimension(32, 28));
        closeBtn.addActionListener(e -> setVisible(false));
        contentPanel.add(closeBtn, BorderLayout.EAST);

        setContentPane(contentPanel);
    }

    private void setupDrag() {
        MouseAdapter dragAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragStart = e.getPoint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart != null) {
                    Point newPos = getLocation();
                    newPos.translate(e.getX() - dragStart.x, e.getY() - dragStart.y);
                    setLocation(newPos);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragStart = null;
            }
        };
        contentPanel.addMouseListener(dragAdapter);
        contentPanel.addMouseMotionListener(dragAdapter);
        for (Component comp : contentPanel.getComponents()) {
            comp.addMouseListener(dragAdapter);
            comp.addMouseMotionListener(dragAdapter);
        }
    }

    // ==================== 截图方法 ====================

    public void doFullScreenCapture() {
        setVisible(false);
        try {
            Robot robot = new Robot();
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage image = robot.createScreenCapture(screenRect);
            File saved = screenshotService.saveImage(image, "fullscreen");
            showNotification("✅ 全屏截图已保存\n" + saved.getAbsolutePath());
        } catch (Exception e) {
            log.error("全屏截图失败", e);
            showNotification("❌ 截图失败: " + e.getMessage());
        } finally {
            setVisible(true);
        }
    }

    /**
     * 实时全屏：点击按钮后显示全屏透明窗口，点击任意位置即截图（ESC取消）
     */
    public void startRealtimeCapture() {
        // 隐藏工具栏
        setVisible(false);

        // 创建透明全屏窗口
        JWindow clickWindow = new JWindow();
        clickWindow.setSize(Toolkit.getDefaultToolkit().getScreenSize());
        clickWindow.setAlwaysOnTop(true);
        clickWindow.setBackground(new Color(0, 0, 0, 0)); // 完全透明
        clickWindow.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

        // 鼠标点击 → 截图
        clickWindow.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                clickWindow.setVisible(false);
                clickWindow.dispose();
                doRealtimeCapture();
            }
        });

        // ESC → 取消
        clickWindow.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE) {
                    clickWindow.setVisible(false);
                    clickWindow.dispose();
                    setVisible(true); // 恢复工具栏
                }
            }
        });

        clickWindow.setVisible(true);
        clickWindow.toFront();
        clickWindow.requestFocus();
    }

    private void doRealtimeCapture() {
        try {
            Robot robot = new Robot();
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage image = robot.createScreenCapture(screenRect);
            File saved = screenshotService.saveImage(image, "realtime");
            showNotification("✅ 实时全屏截图已保存\n" + saved.getAbsolutePath());
        } catch (Exception e) {
            log.error("实时全屏截图失败", e);
            showNotification("❌ 截图失败: " + e.getMessage());
        } finally {
            setVisible(true);
        }
    }

    public void doAreaCapture() {
        setVisible(false);
        ScreenSelectorWindow selector = new ScreenSelectorWindow();
        selector.setOnCapture(() -> {
            Rectangle rect = selector.getSelectionRect();
            if (rect != null && rect.width > 5 && rect.height > 5) {
                try {
                    Robot robot = new Robot();
                    BufferedImage image = robot.createScreenCapture(rect);
                    File saved = screenshotService.saveImage(image, "screenshot");
                    showNotification("✅ 区域截图已保存\n" + saved.getAbsolutePath());
                } catch (Exception e) {
                    log.error("区域截图失败", e);
                    showNotification("❌ 截图失败: " + e.getMessage());
                }
            } else {
                showNotification("⚠️ 选区太小或已取消");
            }
            setVisible(true);
        });
        selector.showSelector();
    }

    private void showNotification(String msg) {
        if (SystemTray.isSupported()) {
            try {
                SystemTray tray = SystemTray.getSystemTray();
                TrayIcon[] icons = tray.getTrayIcons();
                if (icons.length > 0) {
                    icons[0].displayMessage("截图助手", msg, TrayIcon.MessageType.INFO);
                    return;
                }
            } catch (Exception e) {}
        }
        JOptionPane.showMessageDialog(this, msg, "截图助手", JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            int w = getWidth();
            int h = getHeight();
            setLocation(screen.width - w - 30, screen.height - h - 80);
        }
    }
}