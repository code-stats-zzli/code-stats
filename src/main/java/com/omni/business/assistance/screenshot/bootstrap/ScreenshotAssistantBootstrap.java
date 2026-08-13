// 文件路径: src/main/java/com/omni/business/assistance/screenshot/bootstrap/ScreenshotAssistantBootstrap.java
package com.omni.business.assistance.screenshot.bootstrap;

import com.omni.business.assistance.directory.ui.DirectoryFloatingWindow;
import com.omni.business.assistance.screenshot.service.ScreenshotService;
import com.omni.business.assistance.screenshot.ui.FloatingCaptureWindow;
import com.omni.business.assistance.screenshot.ui.FloatingScreenshotToolbar;
import com.omni.business.assistance.screenshot.ui.MiniCaptureWindow;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;

public class ScreenshotAssistantBootstrap {
    private static final Logger log = LoggerFactory.getLogger(ScreenshotAssistantBootstrap.class);
    private static ScreenshotService screenshotService;
    private static MiniCaptureWindow miniWindow;
    private static FloatingCaptureWindow fullWindow;
    private static FloatingScreenshotToolbar toolbar;
    private static TrayIcon trayIcon;

    public static void start() {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                Font defaultFont = new Font("微软雅黑", Font.PLAIN, 12);
                UIManager.put("Label.font", defaultFont);
                UIManager.put("Button.font", defaultFont);
                UIManager.put("TextField.font", defaultFont);
                UIManager.put("TextArea.font", defaultFont);
                UIManager.put("MenuItem.font", defaultFont);
                UIManager.put("Menu.font", defaultFont);
            } catch (Exception ignored) {}

            screenshotService = new ScreenshotService();
            toolbar = new FloatingScreenshotToolbar(screenshotService);
            toolbar.setVisible(true);

            if (SystemTray.isSupported()) {
                SystemTray tray = SystemTray.getSystemTray();
                PopupMenu popup = new PopupMenu();

                MenuItem toolbarItem = new MenuItem("📌 显示/隐藏工具栏");
                MenuItem fullScreenItem = new MenuItem("📷 全屏截图");
                MenuItem realtimeItem = new MenuItem("🎯 实时全屏");
                MenuItem areaScreenItem = new MenuItem("✂️ 区域截图");
                MenuItem showMiniItem = new MenuItem("📷 极简模式");
                MenuItem showFullItem = new MenuItem("📷 完整模式");
                MenuItem showDirItem = new MenuItem("📁 目录浏览");
                MenuItem exitItem = new MenuItem("退出");

                Font menuFont = new Font("微软雅黑", Font.PLAIN, 12);
                for (MenuItem item : new MenuItem[]{toolbarItem, fullScreenItem, realtimeItem, areaScreenItem,
                        showMiniItem, showFullItem, showDirItem, exitItem}) {
                    item.setFont(menuFont);
                }

                toolbarItem.addActionListener(e -> toolbar.setVisible(!toolbar.isVisible()));
                fullScreenItem.addActionListener(e -> toolbar.doFullScreenCapture());
                realtimeItem.addActionListener(e -> toolbar.startRealtimeCapture());
                areaScreenItem.addActionListener(e -> toolbar.doAreaCapture());
                showMiniItem.addActionListener(e -> showMiniMode());
                showFullItem.addActionListener(e -> showFullMode());
                showDirItem.addActionListener(e -> new DirectoryFloatingWindow());
                exitItem.addActionListener(e -> System.exit(0));

                popup.add(toolbarItem);
                popup.addSeparator();
                popup.add(fullScreenItem);
                popup.add(realtimeItem);
                popup.add(areaScreenItem);
                popup.addSeparator();
                popup.add(showMiniItem);
                popup.add(showFullItem);
                popup.addSeparator();
                popup.add(showDirItem);
                popup.addSeparator();
                popup.add(exitItem);

                Image icon = createTrayIconImage();
                trayIcon = new TrayIcon(icon, "截图助手", popup);
                trayIcon.setImageAutoSize(true);
                trayIcon.addActionListener(e -> toolbar.setVisible(!toolbar.isVisible()));
                try {
                    tray.add(trayIcon);
                } catch (AWTException e) {
                    log.error("托盘添加失败", e);
                }
            }

            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .addKeyEventDispatcher(e -> {
                        if (e.isControlDown() && e.isShiftDown() && e.getKeyCode() == KeyEvent.VK_S) {
                            toolbar.setVisible(!toolbar.isVisible());
                            return true;
                        }
                        return false;
                    });

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (trayIcon != null) SystemTray.getSystemTray().remove(trayIcon);
                if (miniWindow != null) miniWindow.dispose();
                if (fullWindow != null) fullWindow.dispose();
                if (toolbar != null) toolbar.dispose();
            }));
        });
    }

    public static void showMiniMode() {
        if (fullWindow != null && fullWindow.isVisible()) fullWindow.setVisible(false);
        if (miniWindow == null) {
            miniWindow = new MiniCaptureWindow(screenshotService);
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            int w = miniWindow.getWidth();
            int h = miniWindow.getHeight();
            miniWindow.setLocation(screen.width - w - 30, screen.height - h - 50);
        }
        miniWindow.setVisible(true);
    }

    private static void showFullMode() {
        if (miniWindow != null && miniWindow.isVisible()) miniWindow.setVisible(false);
        if (fullWindow == null) {
            fullWindow = new FloatingCaptureWindow(screenshotService);
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            int w = fullWindow.getWidth();
            int h = fullWindow.getHeight();
            fullWindow.setLocation(screen.width - w - 30, screen.height - h - 50);
        }
        fullWindow.setVisible(true);
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