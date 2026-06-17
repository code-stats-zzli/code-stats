package com.omni.business.assistance.screenshot.bootstrap;

import com.omni.business.assistance.directory.ui.DirectoryFloatingWindow;
import com.omni.business.assistance.screenshot.service.ScreenshotService;
import com.omni.business.assistance.screenshot.ui.FloatingCaptureWindow;
import com.omni.business.assistance.screenshot.ui.MiniCaptureWindow;
import com.omni.framework.spring.config.ConfigLoader;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;

/**
 * 截图助手启动类（系统托盘 + 浮窗管理）
 * 默认不打开任何浮窗，右键托盘菜单选择模式
 */
public class ScreenshotAssistantBootstrap {

    private static ScreenshotService screenshotService;
    private static MiniCaptureWindow miniWindow;
    private static FloatingCaptureWindow fullWindow;
    private static TrayIcon trayIcon;
    private static boolean fullModeActive = false;

    public static void start() {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                // 设置默认字体支持中文
                Font defaultFont = new Font("微软雅黑", Font.PLAIN, 12);
                UIManager.put("Label.font", defaultFont);
                UIManager.put("Button.font", defaultFont);
                UIManager.put("TextField.font", defaultFont);
                UIManager.put("TextArea.font", defaultFont);
                UIManager.put("MenuItem.font", defaultFont);
                UIManager.put("Menu.font", defaultFont);
            } catch (Exception ignored) {}

            screenshotService = new ScreenshotService();

            // 默认不打开任何窗口（仅托盘）
            // 但热键 Ctrl+Shift+S 可切换极简模式

            // 系统托盘
            if (SystemTray.isSupported()) {
                SystemTray tray = SystemTray.getSystemTray();
                PopupMenu popup = new PopupMenu();

                // 菜单项（设置字体避免乱码）
                MenuItem showMiniItem = new MenuItem("📷 极简模式");
                MenuItem showFullItem = new MenuItem("📷 完整模式");
                MenuItem showDirItem = new MenuItem("📁 目录浏览");
                MenuItem exitItem = new MenuItem("退出");

                Font menuFont = new Font("微软雅黑", Font.PLAIN, 12);
                showMiniItem.setFont(menuFont);
                showFullItem.setFont(menuFont);
                showDirItem.setFont(menuFont);
                exitItem.setFont(menuFont);

                showMiniItem.addActionListener(e -> showMiniMode());
                showFullItem.addActionListener(e -> showFullMode());
                showDirItem.addActionListener(e -> new DirectoryFloatingWindow());
                exitItem.addActionListener(e -> System.exit(0));

                popup.add(showMiniItem);
                popup.add(showFullItem);
                popup.addSeparator();
                popup.add(showDirItem);
                popup.addSeparator();
                popup.add(exitItem);

                Image icon = createTrayIconImage();
                trayIcon = new TrayIcon(icon, "截图助手", popup);
                trayIcon.setImageAutoSize(true);
                trayIcon.addActionListener(e -> {
                    // 双击托盘切换极简模式（如果无窗口则打开，有则隐藏）
                    if (miniWindow != null && miniWindow.isVisible()) {
                        miniWindow.setVisible(false);
                    } else if (fullWindow != null && fullWindow.isVisible()) {
                        fullWindow.setVisible(false);
                    } else {
                        showMiniMode();
                    }
                });
                try {
                    tray.add(trayIcon);
                } catch (AWTException e) {
                    e.printStackTrace();
                }
            }

            // 全局热键 Ctrl+Shift+S 显示/隐藏极简模式
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .addKeyEventDispatcher(e -> {
                        if (e.isControlDown() && e.isShiftDown() && e.getKeyCode() == KeyEvent.VK_S) {
                            if (miniWindow != null && miniWindow.isVisible()) {
                                miniWindow.setVisible(false);
                            } else {
                                showMiniMode();
                            }
                            return true;
                        }
                        return false;
                    });

            // 应用退出时清理
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (trayIcon != null) {
                    SystemTray.getSystemTray().remove(trayIcon);
                }
                if (miniWindow != null) miniWindow.dispose();
                if (fullWindow != null) fullWindow.dispose();
            }));
        });
    }

    private static void showMiniMode() {
        if (fullWindow != null && fullWindow.isVisible()) {
            fullWindow.setVisible(false);
        }
        if (miniWindow == null) {
            miniWindow = new MiniCaptureWindow(screenshotService);
            // 定位到右下角
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            int w = miniWindow.getWidth();
            int h = miniWindow.getHeight();
            miniWindow.setLocation(screen.width - w - 30, screen.height - h - 50);
        }
        miniWindow.setVisible(true);
        fullModeActive = false;
    }

    private static void showFullMode() {
        if (miniWindow != null && miniWindow.isVisible()) {
            miniWindow.setVisible(false);
        }
        if (fullWindow == null) {
            fullWindow = new FloatingCaptureWindow(screenshotService);
            // 定位到右下角
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            int w = fullWindow.getWidth();
            int h = fullWindow.getHeight();
            fullWindow.setLocation(screen.width - w - 30, screen.height - h - 50);
        }
        fullWindow.setVisible(true);
        fullModeActive = true;
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

    public static void main(String[] args) {
        start();
    }
}