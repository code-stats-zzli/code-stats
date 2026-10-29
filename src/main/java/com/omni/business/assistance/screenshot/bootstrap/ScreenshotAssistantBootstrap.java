// 文件路径: src/main/java/com/omni/business/assistance/screenshot/bootstrap/ScreenshotAssistantBootstrap.java
package com.omni.business.assistance.screenshot.bootstrap;

import com.omni.business.assistance.directory.ui.DirectoryFloatingWindow;
import com.omni.business.assistance.screenshot.service.ScreenshotService;
import com.omni.business.assistance.screenshot.ui.FloatingCaptureWindow;
import com.omni.business.assistance.screenshot.ui.FloatingScreenshotToolbar;
import com.omni.business.assistance.screenshot.ui.MiniCaptureWindow;
import com.omni.business.assistance.screenshot.util.FontUtil;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

public class ScreenshotAssistantBootstrap {
    private static final Logger log = LoggerFactory.getLogger(ScreenshotAssistantBootstrap.class);
    private static ScreenshotService screenshotService;
    private static MiniCaptureWindow miniWindow;
    private static FloatingCaptureWindow fullWindow;
    private static FloatingScreenshotToolbar toolbar;
    private static TrayIcon trayIcon;

    private static JPopupMenu trayMenu;
    private static JWindow menuAnchor;

    public static void start() {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            Font defaultFont = FontUtil.getUIFont(Font.PLAIN, 12);
            log.info("截图助手 UI 字体: {}", defaultFont.getFamily());

            UIManager.put("Label.font", defaultFont);
            UIManager.put("Button.font", defaultFont);
            UIManager.put("TextField.font", defaultFont);
            UIManager.put("TextArea.font", defaultFont);
            UIManager.put("MenuItem.font", defaultFont);
            UIManager.put("Menu.font", defaultFont);
            UIManager.put("PopupMenu.font", defaultFont);
            UIManager.put("OptionPane.messageFont", defaultFont);
            UIManager.put("OptionPane.buttonFont", defaultFont);
            UIManager.put("CheckBox.font", defaultFont);
            UIManager.put("RadioButton.font", defaultFont);
            UIManager.put("ComboBox.font", defaultFont);
            UIManager.put("List.font", defaultFont);
            UIManager.put("Table.font", defaultFont);
            UIManager.put("TableHeader.font", defaultFont);
            UIManager.put("Tree.font", defaultFont);
            UIManager.put("TitledBorder.font", defaultFont);
            UIManager.put("ToolTip.font", defaultFont);

            screenshotService = new ScreenshotService();
            toolbar = new FloatingScreenshotToolbar(screenshotService);
            toolbar.setVisible(false);

            if (SystemTray.isSupported()) {
                try {
                    SystemTray tray = SystemTray.getSystemTray();
                    Image icon = createTrayIconImage();
                    trayIcon = new TrayIcon(icon, "截图助手");
                    trayIcon.setImageAutoSize(true);

                    trayIcon.addActionListener(e -> toolbar.setVisible(!toolbar.isVisible()));
                    trayIcon.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mousePressed(MouseEvent e) {
                            if (e.isPopupTrigger()) showTrayMenu();
                        }
                        @Override
                        public void mouseReleased(MouseEvent e) {
                            if (e.isPopupTrigger() || e.getButton() == MouseEvent.BUTTON3) {
                                showTrayMenu();
                            }
                        }
                    });

                    tray.add(trayIcon);
                    log.info("截图助手已启动，按 Ctrl+Shift+S 显示工具栏，或双击托盘图标");
                } catch (Exception e) {
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

            // ★★★ 关键修复 ★★★
            // 旧代码在 shutdown hook 里调 dispose()/SystemTray.remove()，这些都会同步到 AWT-EventQueue-0，
            // shutdown 期间 EDT 可能已停止响应，导致 hook 死锁，JVM 卡在 "waiting for shutdown hooks to finish"
            //
            // 新方案：shutdown hook 只做最简单的、带超时的托盘清理，不碰任何 Swing 组件
            //（窗口由 JVM 退出时自动释放，无需显式 dispose）
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    if (trayIcon != null) {
                        Thread cleaner = new Thread(() -> {
                            try {
                                SystemTray.getSystemTray().remove(trayIcon);
                            } catch (Throwable ignored) {}
                        }, "tray-cleaner");
                        cleaner.setDaemon(true);
                        cleaner.start();
                        cleaner.join(1500);   // 最多等 1.5 秒
                    }
                } catch (Throwable ignored) {}
                log.info("截图助手已释放（shutdown hook 未调用任何 Swing 方法）");
            }, "screenshot-shutdown"));
        });
    }

    // ==================== 以下方法保持不变 ====================

    private static void initTrayMenu() {
        trayMenu = new JPopupMenu();
        Font itemFont = FontUtil.deriveUIFont("MenuItem.font", Font.PLAIN, 12);

        addMenuItem(trayMenu, "显示/隐藏工具栏", itemFont, () -> toolbar.setVisible(!toolbar.isVisible()));
        trayMenu.addSeparator();
        addMenuItem(trayMenu, "全屏截图", itemFont, () -> toolbar.doFullScreenCapture());
        addMenuItem(trayMenu, "文字识别", itemFont, () -> toolbar.doOcrCapture());
        addMenuItem(trayMenu, "区域截图", itemFont, () -> toolbar.doAreaCapture());
        trayMenu.addSeparator();
        addMenuItem(trayMenu, "极简模式", itemFont, ScreenshotAssistantBootstrap::showMiniMode);
        addMenuItem(trayMenu, "完整模式", itemFont, ScreenshotAssistantBootstrap::showFullMode);
        trayMenu.addSeparator();
        addMenuItem(trayMenu, "目录浏览", itemFont, () -> new DirectoryFloatingWindow());
        trayMenu.addSeparator();
        addMenuItem(trayMenu, "退出", itemFont, () -> System.exit(0));

        trayMenu.addPopupMenuListener(new PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(PopupMenuEvent e) {}
            @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent e) { disposeAnchor(); }
            @Override public void popupMenuCanceled(PopupMenuEvent e) { disposeAnchor(); }
        });
    }

    private static void addMenuItem(JPopupMenu menu, String text, Font font, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.setFont(font);
        item.addActionListener(e -> action.run());
        menu.add(item);
    }

    private static void showTrayMenu() {
        if (trayMenu == null) initTrayMenu();
        if (menuAnchor == null || !menuAnchor.isDisplayable()) {
            menuAnchor = new JWindow();
            menuAnchor.setSize(1, 1);
            try {
                menuAnchor.setBackground(new Color(0, 0, 0, 0));
                menuAnchor.setOpacity(0f);
            } catch (Exception ignored) {}
        }
        Point p = MouseInfo.getPointerInfo().getLocation();
        menuAnchor.setLocation(p);
        menuAnchor.setVisible(true);
        trayMenu.show(menuAnchor, 0, 0);
    }

    private static void disposeAnchor() {
        if (menuAnchor != null) {
            menuAnchor.setVisible(false);
            menuAnchor.dispose();
            menuAnchor = null;
        }
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

    public static void showFullMode() {
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