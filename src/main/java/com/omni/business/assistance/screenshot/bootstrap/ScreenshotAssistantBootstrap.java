package com.omni.business.assistance.screenshot.bootstrap;

import com.omni.business.assistance.screenshot.service.ScreenshotService;
import com.omni.business.assistance.screenshot.ui.FloatingCaptureWindow;
import com.omni.framework.spring.config.ConfigLoader;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;

/**
 * 截图助手启动类（系统托盘 + 浮窗管理）
 * 支持最小化到托盘，点击托盘图标显示/隐藏浮窗
 */
public class ScreenshotAssistantBootstrap {

    private static FloatingCaptureWindow captureWindow;
    private static ScreenshotService screenshotService;
    private static TrayIcon trayIcon;

    public static void start() {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            screenshotService = new ScreenshotService();

            // 创建浮窗（默认在右下角）
            captureWindow = new FloatingCaptureWindow(screenshotService);
            // ★ 新增：读取配置，控制是否默认显示面板，默认 false（关闭）
            boolean autoShow = Boolean.parseBoolean(ConfigLoader.getProperty("screenshot.assistant.autoShow", "false"));
            captureWindow.setVisible(autoShow);
            // 定位到右下角
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            int w = captureWindow.getWidth();
            int h = captureWindow.getHeight();
            captureWindow.setLocation(screen.width - w - 30, screen.height - h - 50);

            // 系统托盘
            if (SystemTray.isSupported()) {
                SystemTray tray = SystemTray.getSystemTray();
                PopupMenu popup = new PopupMenu();
                MenuItem showItem = new MenuItem("📷 显示截图助手");
                MenuItem hideItem = new MenuItem("🙈 隐藏截图助手");
                MenuItem exitItem = new MenuItem("退出");

                showItem.addActionListener(e -> showWindow());
                hideItem.addActionListener(e -> hideWindow());
                exitItem.addActionListener(e -> System.exit(0));

                popup.add(showItem);
                popup.add(hideItem);
                popup.addSeparator();
                popup.add(exitItem);

                Image icon = createTrayIconImage();
                trayIcon = new TrayIcon(icon, "截图助手", popup);
                trayIcon.setImageAutoSize(true);
                trayIcon.addActionListener(e -> {
                    // 双击托盘图标切换显示
                    if (captureWindow.isVisible()) {
                        hideWindow();
                    } else {
                        showWindow();
                    }
                });
                try {
                    tray.add(trayIcon);
                } catch (AWTException e) {
                    e.printStackTrace();
                }
            }

            // 全局热键 Ctrl+Shift+S 显示/隐藏浮窗（保留）
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .addKeyEventDispatcher(e -> {
                        if (e.isControlDown() && e.isShiftDown() && e.getKeyCode() == KeyEvent.VK_S) {
                            if (captureWindow.isVisible()) {
                                hideWindow();
                            } else {
                                showWindow();
                            }
                            return true;
                        }
                        return false;
                    });

            // 应用退出时清理资源
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (trayIcon != null) {
                    SystemTray.getSystemTray().remove(trayIcon);
                }
                if (captureWindow != null) {
                    captureWindow.dispose();
                }
            }));
        });
    }

    private static void showWindow() {
        if (captureWindow != null) {
            captureWindow.setVisible(true);
            captureWindow.toFront();
            captureWindow.requestFocus();
        }
    }

    private static void hideWindow() {
        if (captureWindow != null) {
            captureWindow.setVisible(false);
        }
    }

    private static Image createTrayIconImage() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(new Color(0, 102, 255));
        g2d.fillOval(0, 0, 16, 16);
        g2d.setColor(Color.WHITE);
        g2d.fillOval(4, 4, 8, 8);
        g2d.setColor(new Color(0, 102, 255));
        g2d.fillOval(6, 6, 4, 4);
        g2d.dispose();
        return img;
    }

    // 独立入口测试
    public static void main(String[] args) {
        start();
    }
}