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

    /** Swing 自绘托盘菜单（替代 AWT 原生 PopupMenu，避免中文方块） */
    private static JPopupMenu trayMenu;
    /** 菜单锚点窗口：1×1 透明窗口 */
    private static JWindow menuAnchor;

    public static void start() {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            // ============ 全局字体 ============
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
            // ★ 改动1：默认不显示工具栏
            toolbar.setVisible(false);

            if (SystemTray.isSupported()) {
                SystemTray tray = SystemTray.getSystemTray();
                Image icon = createTrayIconImage();
                trayIcon = new TrayIcon(icon, "截图助手");
                trayIcon.setImageAutoSize(true);

                // 不调用 setPopupMenu（原生菜单中文方块），改为监听右键 + Swing JPopupMenu

                // 左键：切换工具栏（Windows 上通常双击触发 ActionListener）
                trayIcon.addActionListener(e -> toolbar.setVisible(!toolbar.isVisible()));

                // 右键：弹出 Swing 菜单
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

                try {
                    tray.add(trayIcon);
                    // ★ 改动2：启动时弹一次托盘提示，告诉用户快捷键
                    trayIcon.displayMessage(
                            "截图助手已启动",
                            "按 Ctrl+Shift+S 显示工具栏，或双击托盘图标",
                            TrayIcon.MessageType.INFO
                    );
                } catch (AWTException e) {
                    log.error("托盘添加失败", e);
                }
            }

            // 全局快捷键：Ctrl+Shift+S 切换工具栏
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
                disposeAnchor();
            }));
        });
    }

    // ==================== 托盘菜单（Swing 自绘） ====================

    private static void initTrayMenu() {
        trayMenu = new JPopupMenu();
        Font itemFont = FontUtil.deriveUIFont("MenuItem.font", Font.PLAIN, 12);

        addMenuItem(trayMenu, "显示/隐藏工具栏", itemFont, () -> toolbar.setVisible(!toolbar.isVisible()));
        trayMenu.addSeparator();
        addMenuItem(trayMenu, "全屏截图", itemFont, () -> toolbar.doFullScreenCapture());
        addMenuItem(trayMenu, "实时全屏", itemFont, () -> toolbar.startRealtimeCapture());
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

    // ==================== 模式切换 ====================

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