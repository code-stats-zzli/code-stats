// 文件路径: src/main/java/com/omni/business/assistance/screenshot/ui/FloatingScreenshotToolbar.java
package com.omni.business.assistance.screenshot.ui;

import com.omni.business.assistance.screenshot.service.ScreenshotService;
import com.omni.business.assistance.screenshot.util.FontUtil;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

public class FloatingScreenshotToolbar extends JWindow {
    private static final Logger log = LoggerFactory.getLogger(FloatingScreenshotToolbar.class);
    private final ScreenshotService screenshotService;
    private JPanel contentPanel;
    private Point dragStart = null;

    /** ★ 尺寸优化：360×60 → 320×48 */
    private static final int DEFAULT_WIDTH = 320;
    private static final int DEFAULT_HEIGHT = 48;

    public FloatingScreenshotToolbar(ScreenshotService service) {
        this.screenshotService = service;
        initUI();
        setupDrag();
        setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        setAlwaysOnTop(true);
        setBackground(new Color(0, 0, 0, 0));
    }

    private void initUI() {
        contentPanel = new JPanel();
        contentPanel.setLayout(new BorderLayout(4, 0));
        contentPanel.setBackground(new Color(30, 40, 60, 230));
        contentPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0, 102, 255, 200), 2),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)
        ));

        // 标题
        JLabel titleLabel = new JLabel("截图助手");
        titleLabel.setFont(FontUtil.deriveUIFont("Label.font", Font.BOLD, 13));
        titleLabel.setForeground(Color.WHITE);
        contentPanel.add(titleLabel, BorderLayout.WEST);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        btnPanel.setOpaque(false);

        JButton fullBtn = createButton("全屏", new Color(0, 102, 255), 45);
        fullBtn.addActionListener(e -> doFullScreenCapture());
        btnPanel.add(fullBtn);

        JButton realtimeBtn = createButton("实时全屏", new Color(0, 200, 0), 70);
        realtimeBtn.addActionListener(e -> startRealtimeCapture());
        btnPanel.add(realtimeBtn);

        JButton areaBtn = createButton("区域", new Color(255, 165, 0), 45);
        areaBtn.addActionListener(e -> doAreaCapture());
        btnPanel.add(areaBtn);

        contentPanel.add(btnPanel, BorderLayout.CENTER);

        // 关闭按钮：× (U+00D7)，所有字体都有
        JButton closeBtn = new JButton("×");
        closeBtn.setFont(FontUtil.deriveUIFont("Button.font", Font.PLAIN, 15));
        closeBtn.setBackground(new Color(200, 50, 50));
        closeBtn.setForeground(Color.WHITE);
        closeBtn.setFocusPainted(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setOpaque(true);
        closeBtn.setPreferredSize(new Dimension(28, 24));
        closeBtn.addActionListener(e -> setVisible(false));
        contentPanel.add(closeBtn, BorderLayout.EAST);

        setContentPane(contentPanel);
    }

    private JButton createButton(String text, Color bg, int width) {
        JButton btn = new JButton(text);
        btn.setFont(FontUtil.deriveUIFont("Button.font", Font.PLAIN, 11));
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setPreferredSize(new Dimension(width, 24));
        return btn;
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

    // ==================== 截图方法（★ 默认复制到剪贴板，不再落盘） ====================

    public void doFullScreenCapture() {
        setVisible(false);
        try {
            Robot robot = new Robot();
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage image = robot.createScreenCapture(screenRect);
            boolean ok = screenshotService.copyToClipboard(image);
            showNotification(ok ? "全屏截图已复制到剪贴板\n直接 Ctrl+V 即可粘贴使用"
                    : "全屏截图复制到剪贴板失败");
        } catch (Exception e) {
            log.error("全屏截图失败", e);
            showNotification("截图失败: " + e.getMessage());
        } finally {
            setVisible(true);
        }
    }

    /**
     * 实时全屏：显示全屏透明窗口，点击任意位置即截图（ESC 取消），截图复制到剪贴板。
     */
    public void startRealtimeCapture() {
        setVisible(false);

        JWindow clickWindow = new JWindow();
        clickWindow.setSize(Toolkit.getDefaultToolkit().getScreenSize());
        clickWindow.setAlwaysOnTop(true);
        clickWindow.setBackground(new Color(0, 0, 0, 0));
        clickWindow.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

        clickWindow.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                clickWindow.setVisible(false);
                clickWindow.dispose();
                doRealtimeCapture();
            }
        });

        clickWindow.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE) {
                    clickWindow.setVisible(false);
                    clickWindow.dispose();
                    setVisible(true);
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
            boolean ok = screenshotService.copyToClipboard(image);
            showNotification(ok ? "实时截图已复制到剪贴板\n直接 Ctrl+V 即可粘贴使用"
                    : "实时截图复制到剪贴板失败");
        } catch (Exception e) {
            log.error("实时全屏截图失败", e);
            showNotification("截图失败: " + e.getMessage());
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
                    boolean ok = screenshotService.copyToClipboard(image);
                    showNotification(ok ? "区域截图已复制到剪贴板\n直接 Ctrl+V 即可粘贴使用"
                            : "区域截图复制到剪贴板失败");
                } catch (Exception e) {
                    log.error("区域截图失败", e);
                    showNotification("截图失败: " + e.getMessage());
                }
            } else {
                showNotification("选区太小或已取消");
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
            } catch (Exception ignored) {}
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