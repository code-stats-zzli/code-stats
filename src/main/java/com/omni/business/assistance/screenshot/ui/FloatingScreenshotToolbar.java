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

/**
 * 截图助手浮动工具栏（垂直布局，默认隐藏）。
 *
 * <p>设计要点：
 * <ul>
 *   <li>垂直排列：宽 110，高 200，右下角，占用屏幕面积小、功能一目了然。</li>
 *   <li>语义化配色：全屏=蓝、实时=翠绿、区域=琥珀、隐藏=石板灰。</li>
 *   <li>hover 反馈：鼠标悬停按钮变亮，光标变手型。</li>
 *   <li>默认隐藏：由 {@link ScreenshotAssistantBootstrap} 控制，需通过
 *       {@code Ctrl+Shift+S} 或托盘唤起。</li>
 *   <li>拖拽：仅标题栏可拖拽，避免与按钮点击冲突。用户拖拽后位置记忆。</li>
 *   <li>截图行为：全部复制到剪贴板，不再落盘。</li>
 * </ul>
 */
public class FloatingScreenshotToolbar extends JWindow {
    private static final Logger log = LoggerFactory.getLogger(FloatingScreenshotToolbar.class);
    private final ScreenshotService screenshotService;
    private JPanel contentPanel;
    private JPanel titleBar;
    private Point dragStart = null;
    /** 用户是否手动拖拽过（拖拽后不再重置位置） */
    private boolean hasUserMoved = false;

    /** 垂直工具栏尺寸 */
    private static final int DEFAULT_WIDTH = 110;
    private static final int DEFAULT_HEIGHT = 200;

    // 语义化配色
    private static final Color BG_COLOR         = new Color(20, 28, 45, 235);
    private static final Color BORDER_COLOR     = new Color(0, 102, 255, 200);
    private static final Color BTN_FULL_SCREEN  = new Color(0, 102, 255);     // 蓝
    private static final Color BTN_REALTIME     = new Color(16, 185, 129);    // 翠绿
    private static final Color BTN_AREA         = new Color(245, 158, 11);    // 琥珀
    private static final Color BTN_HIDE         = new Color(100, 116, 139);   // 石板灰

    public FloatingScreenshotToolbar(ScreenshotService service) {
        this.screenshotService = service;
        initUI();
        setupDrag();
        setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        setAlwaysOnTop(true);
        setBackground(new Color(0, 0, 0, 0));
    }

    // ==================== UI 初始化 ====================

    private void initUI() {
        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(BG_COLOR);
        contentPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 2),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));

        // ---------- 标题栏（可拖拽） ----------
        titleBar = new JPanel(new BorderLayout());
        titleBar.setOpaque(false);
        titleBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        titleBar.setPreferredSize(new Dimension(DEFAULT_WIDTH - 16, 24));
        titleBar.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));

        JLabel titleLabel = new JLabel("截图助手", SwingConstants.CENTER);
        titleLabel.setFont(FontUtil.deriveUIFont("Label.font", Font.BOLD, 13));
        titleLabel.setForeground(Color.WHITE);
        titleBar.add(titleLabel, BorderLayout.CENTER);
        contentPanel.add(titleBar);

        // ---------- 分隔线 ----------
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(0, 102, 255, 150));
        sep.setBackground(new Color(0, 102, 255, 150));
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        contentPanel.add(Box.createVerticalStrut(6));
        contentPanel.add(sep);
        contentPanel.add(Box.createVerticalStrut(8));

        // ---------- 功能按钮（垂直排列） ----------
        addVerticalButton("全屏截图", BTN_FULL_SCREEN, this::doFullScreenCapture);
        addVerticalButton("实时全屏", BTN_REALTIME,    this::startRealtimeCapture);
        addVerticalButton("区域截图", BTN_AREA,        this::doAreaCapture);
        addVerticalButton("隐藏工具", BTN_HIDE,        () -> setVisible(false));

        setContentPane(contentPanel);
    }

    /**
     * 添加一个占满宽度的垂直按钮，带 hover 变亮效果。
     */
    private void addVerticalButton(String text, Color baseColor, Runnable action) {
        JButton btn = new JButton(text);
        btn.setFont(FontUtil.deriveUIFont("Button.font", Font.PLAIN, 12));
        btn.setForeground(Color.WHITE);
        btn.setBackground(baseColor);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);

        Dimension size = new Dimension(DEFAULT_WIDTH - 20, 32);
        btn.setMaximumSize(size);
        btn.setPreferredSize(size);
        btn.setMinimumSize(size);

        // hover 反馈：变亮 15%
        Color hoverColor = brighten(baseColor, 0.15f);
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { btn.setBackground(hoverColor); }
            @Override public void mouseExited(MouseEvent e)  { btn.setBackground(baseColor); }
        });
        btn.addActionListener(e -> action.run());

        contentPanel.add(btn);
        contentPanel.add(Box.createVerticalStrut(6));
    }

    /** 颜色变亮 */
    private static Color brighten(Color c, float factor) {
        int r = Math.min(255, (int) (c.getRed()   + 255 * factor));
        int g = Math.min(255, (int) (c.getGreen() + 255 * factor));
        int b = Math.min(255, (int) (c.getBlue()  + 255 * factor));
        return new Color(r, g, b);
    }

    // ==================== 拖拽（仅标题栏） ====================

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
                    hasUserMoved = true;
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragStart = null;
            }
        };
        // 标题栏和标题 Label 都可拖拽
        titleBar.addMouseListener(dragAdapter);
        titleBar.addMouseMotionListener(dragAdapter);
        for (Component comp : titleBar.getComponents()) {
            comp.addMouseListener(dragAdapter);
            comp.addMouseMotionListener(dragAdapter);
        }
    }

    // ==================== 截图方法（默认复制到剪贴板） ====================

    public void doFullScreenCapture() {
        setVisible(false);
        try {
            Robot robot = new Robot();
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage image = robot.createScreenCapture(screenRect);
            boolean ok = screenshotService.copyToClipboard(image);
            showNotification(ok
                    ? "全屏截图已复制到剪贴板\n直接 Ctrl+V 即可粘贴使用"
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
            showNotification(ok
                    ? "实时截图已复制到剪贴板\n直接 Ctrl+V 即可粘贴使用"
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
                    showNotification(ok
                            ? "区域截图已复制到剪贴板\n直接 Ctrl+V 即可粘贴使用"
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

    // ==================== 显示与定位 ====================

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            // 仅首次显示（用户未拖拽过）时定位到右下角
            if (!hasUserMoved) {
                Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
                int w = getWidth();
                int h = getHeight();
                setLocation(screen.width - w - 30, screen.height - h - 80);
            }
        }
    }
}