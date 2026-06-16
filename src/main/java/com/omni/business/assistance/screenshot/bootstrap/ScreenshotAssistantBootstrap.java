package com.omni.business.assistance.screenshot.bootstrap;

import com.omni.business.assistance.screenshot.model.ScreenshotResult;
import com.omni.business.assistance.screenshot.service.ScreenshotService;
import com.omni.business.assistance.screenshot.ui.ResultPopupWindow;
import com.omni.business.assistance.screenshot.ui.ScreenSelectorWindow;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;

public class ScreenshotAssistantBootstrap {

    private static ScreenshotService screenshotService;
    private static ScreenSelectorWindow selectorWindow;
    private static ResultPopupWindow resultWindow;

    public static void start() {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            screenshotService = new ScreenshotService();
            resultWindow = new ResultPopupWindow();

            // 系统托盘
            if (SystemTray.isSupported()) {
                SystemTray tray = SystemTray.getSystemTray();
                PopupMenu popup = new PopupMenu();
                MenuItem captureItem = new MenuItem("📷 开始截图 (Ctrl+Shift+S)");
                MenuItem exitItem = new MenuItem("退出");

                captureItem.addActionListener(e -> startCapture());
                exitItem.addActionListener(e -> System.exit(0));

                popup.add(captureItem);
                popup.addSeparator();
                popup.add(exitItem);

                // 内存生成图标，无需外部文件
                Image icon = createTrayIconImage();
                TrayIcon trayIcon = new TrayIcon(icon, "截图助手", popup);
                trayIcon.setImageAutoSize(true);
                try {
                    tray.add(trayIcon);
                } catch (AWTException e) {
                    e.printStackTrace();
                }
            }

            // 全局热键 Ctrl+Shift+S
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .addKeyEventDispatcher(e -> {
                        if (e.isControlDown() && e.isShiftDown() && e.getKeyCode() == KeyEvent.VK_S) {
                            startCapture();
                            return true;
                        }
                        return false;
                    });
        });
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

    private static void startCapture() {
        if (selectorWindow != null) selectorWindow.dispose();
        selectorWindow = new ScreenSelectorWindow(() -> {
            Rectangle rect = selectorWindow.getSelectionRect();
            if (rect != null && rect.width > 10 && rect.height > 10) {
                new Thread(() -> {
                    ScreenshotResult result = screenshotService.captureAndSearch(rect);
                    SwingUtilities.invokeLater(() -> {
                        resultWindow.showResult(result, new Point(rect.x + rect.width, rect.y));
                        selectorWindow.dispose();
                    });
                }).start();
            } else {
                selectorWindow.dispose();
            }
        });
        selectorWindow.showSelector();
    }

    // 独立入口测试
    public static void main(String[] args) {
        start();
    }
}