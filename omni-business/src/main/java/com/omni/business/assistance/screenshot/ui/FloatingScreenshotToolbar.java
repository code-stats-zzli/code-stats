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
 * 截图助手浮动工具栏（紧凑图标版）。
 *
 * <p>本版本改动：
 * <ul>
 *   <li>宽度 37、高度 130（较原 74 再缩小一半）。</li>
 *   <li>按钮图标改为 <b>Graphics2D 自绘矢量图标</b>，不再依赖 emoji/特殊字符字体，
 *       彻底解决部分系统图标不显示的问题。</li>
 *   <li>截图或识别完成后，工具栏<b>不再隐藏</b>。</li>
 *   <li>成功/失败提示为轻量 Toast，显示在工具栏正上方，<b>1 秒后自动消失</b>。</li>
 *   <li>「文字识别」：选区截图 → OCR → 识别文字写入系统剪贴板。</li>
 * </ul>
 */
public class FloatingScreenshotToolbar extends JWindow {
    private static final Logger log = LoggerFactory.getLogger(FloatingScreenshotToolbar.class);
    private final ScreenshotService screenshotService;
    private JPanel contentPanel;
    private JPanel titleBar;
    private Point dragStart = null;
    private boolean hasUserMoved = false;

    /** 紧凑尺寸：宽 37、高 130 */
    private static final int DEFAULT_WIDTH = 37;
    private static final int DEFAULT_HEIGHT = 130;

    // 语义化配色
    private static final Color BG_COLOR        = new Color(20, 28, 45, 235);
    private static final Color BORDER_COLOR    = new Color(0, 102, 255, 200);
    private static final Color BTN_FULL_SCREEN = new Color(0, 102, 255);     // 蓝
    private static final Color BTN_OCR         = new Color(16, 185, 129);    // 翠绿
    private static final Color BTN_AREA        = new Color(245, 158, 11);    // 琥珀
    private static final Color BTN_HIDE        = new Color(100, 116, 139);   // 石板灰

    // Toast 状态
    private JWindow toastWindow;
    private Timer toastTimer;
    private static final int TOAST_DURATION_MS = 1000;

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
                BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));

        // ---------- 标题栏（拖拽手柄） ----------
        titleBar = new JPanel(new BorderLayout());
        titleBar.setOpaque(false);
        titleBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 14));
        titleBar.setPreferredSize(new Dimension(DEFAULT_WIDTH - 8, 14));
        titleBar.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));

        JLabel titleLabel = new JLabel("•••", SwingConstants.CENTER);
        titleLabel.setFont(FontUtil.getUIFont(Font.BOLD, 10));
        titleLabel.setForeground(new Color(180, 200, 230));
        titleBar.add(titleLabel, BorderLayout.CENTER);
        contentPanel.add(titleBar);
        contentPanel.add(Box.createVerticalStrut(2));

        // ---------- 功能按钮（自绘矢量图标） ----------
        addIconButton(IconKind.FULL_SCREEN, "全屏截图（复制图片到剪贴板）", BTN_FULL_SCREEN, this::doFullScreenCapture);
        addIconButton(IconKind.OCR,        "文字识别（选区截图 → OCR → 复制文字到剪贴板）", BTN_OCR, this::doOcrCapture);
        addIconButton(IconKind.AREA,       "区域截图（复制图片到剪贴板）", BTN_AREA, this::doAreaCapture);
        addIconButton(IconKind.HIDE,       "隐藏工具", BTN_HIDE, () -> setVisible(false));

        setContentPane(contentPanel);
    }

    /**
     * 添加一个占满宽度的矢量图标按钮。
     */
    private void addIconButton(IconKind kind, String tooltip, Color baseColor, Runnable action) {
        JButton btn = new JButton(new VectorIcon(kind, Color.WHITE, 16));
        btn.setToolTipText(tooltip);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(true);
        btn.setOpaque(true);
        btn.setBackground(baseColor);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setMargin(new Insets(0, 0, 0, 0));

        Dimension size = new Dimension(DEFAULT_WIDTH - 12, 22);
        btn.setMaximumSize(size);
        btn.setPreferredSize(size);
        btn.setMinimumSize(size);

        Color hoverColor = brighten(baseColor, 0.15f);
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { btn.setBackground(hoverColor); }
            @Override public void mouseExited(MouseEvent e)  { btn.setBackground(baseColor); }
        });
        btn.addActionListener(e -> action.run());

        contentPanel.add(Box.createVerticalStrut(2));
        contentPanel.add(btn);
    }

    private static Color brighten(Color c, float factor) {
        int r = Math.min(255, (int) (c.getRed()   + 255 * factor));
        int g = Math.min(255, (int) (c.getGreen() + 255 * factor));
        int b = Math.min(255, (int) (c.getBlue()  + 255 * factor));
        return new Color(r, g, b);
    }

    // ==================== 矢量图标（不依赖字体） ====================

    private enum IconKind { FULL_SCREEN, OCR, AREA, HIDE }

    /**
     * 使用 Graphics2D 绘制的矢量图标，任何环境都能显示。
     */
    private static class VectorIcon implements Icon {
        private final IconKind kind;
        private final Color color;
        private final int size;

        VectorIcon(IconKind kind, Color color, int size) {
            this.kind = kind;
            this.color = color;
            this.size = size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            int pad = 3;
            int w = size - pad * 2;
            int h = size - pad * 2;
            int x0 = x + pad;
            int y0 = y + pad;

            switch (kind) {
                case FULL_SCREEN: {
                    // 四角取景框
                    int corner = Math.max(3, w / 3);
                    // 左上
                    g2.drawLine(x0, y0, x0 + corner, y0);
                    g2.drawLine(x0, y0, x0, y0 + corner);
                    // 右上
                    g2.drawLine(x0 + w, y0, x0 + w - corner, y0);
                    g2.drawLine(x0 + w, y0, x0 + w, y0 + corner);
                    // 左下
                    g2.drawLine(x0, y0 + h, x0 + corner, y0 + h);
                    g2.drawLine(x0, y0 + h, x0, y0 + h - corner);
                    // 右下
                    g2.drawLine(x0 + w, y0 + h, x0 + w - corner, y0 + h);
                    g2.drawLine(x0 + w, y0 + h, x0 + w, y0 + h - corner);
                    break;
                }
                case OCR: {
                    // 大写 T（代表 Text）
                    int midX = x0 + w / 2;
                    g2.drawLine(x0 + 1, y0 + 2, x0 + w - 1, y0 + 2);   // 顶横
                    g2.drawLine(midX, y0 + 2, midX, y0 + h - 1);       // 中竖
                    // 底部小横（让 T 更稳重）
                    g2.drawLine(midX - 2, y0 + h - 1, midX + 2, y0 + h - 1);
                    break;
                }
                case AREA: {
                    // 虚线矩形
                    g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                            10f, new float[]{3f, 2f}, 0f));
                    g2.drawRect(x0, y0, w, h);
                    break;
                }
                case HIDE: {
                    // × 号
                    int d = 2;
                    g2.drawLine(x0 + d, y0 + d, x0 + w - d, y0 + h - d);
                    g2.drawLine(x0 + w - d, y0 + d, x0 + d, y0 + h - d);
                    break;
                }
            }
            g2.dispose();
        }

        @Override public int getIconWidth()  { return size; }
        @Override public int getIconHeight() { return size; }
    }

    // ==================== 拖拽 ====================

    private void setupDrag() {
        MouseAdapter dragAdapter = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { dragStart = e.getPoint(); }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart != null) {
                    Point newPos = getLocation();
                    newPos.translate(e.getX() - dragStart.x, e.getY() - dragStart.y);
                    setLocation(newPos);
                    hasUserMoved = true;
                }
            }

            @Override public void mouseReleased(MouseEvent e) { dragStart = null; }
        };
        titleBar.addMouseListener(dragAdapter);
        titleBar.addMouseMotionListener(dragAdapter);
        for (Component comp : titleBar.getComponents()) {
            comp.addMouseListener(dragAdapter);
            comp.addMouseMotionListener(dragAdapter);
        }
    }

    // ==================== 截图与识别（工具栏不隐藏） ====================

    public void doFullScreenCapture() {
        try {
            Robot robot = new Robot();
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage image = robot.createScreenCapture(screenRect);
            boolean ok = screenshotService.copyToClipboard(image);
            showToast(ok ? "✅ 截图成功，已复制" : "❌ 截图复制失败", ok);
        } catch (Exception e) {
            log.error("全屏截图失败", e);
            showToast("❌ 截图失败", false);
        }
    }

    public void doOcrCapture() {
        ScreenSelectorWindow selector = new ScreenSelectorWindow();
        selector.setOnCapture(() -> {
            Rectangle rect = selector.getSelectionRect();
            if (rect != null && rect.width > 5 && rect.height > 5) {
                try {
                    Robot robot = new Robot();
                    BufferedImage image = robot.createScreenCapture(rect);
                    boolean ok = screenshotService.ocrToClipboard(image);
                    showToast(ok ? "✅ 识别成功，文字已复制" : "❌ 未识别到文字", ok);
                } catch (Exception e) {
                    log.error("文字识别截图失败", e);
                    showToast("❌ 识别失败", false);
                }
            } else {
                showToast("选区太小或已取消", false);
            }
        });
        selector.showSelector();
    }

    public void doAreaCapture() {
        ScreenSelectorWindow selector = new ScreenSelectorWindow();
        selector.setOnCapture(() -> {
            Rectangle rect = selector.getSelectionRect();
            if (rect != null && rect.width > 5 && rect.height > 5) {
                try {
                    Robot robot = new Robot();
                    BufferedImage image = robot.createScreenCapture(rect);
                    boolean ok = screenshotService.copyToClipboard(image);
                    showToast(ok ? "✅ 截图成功，已复制" : "❌ 截图复制失败", ok);
                } catch (Exception e) {
                    log.error("区域截图失败", e);
                    showToast("❌ 截图失败", false);
                }
            } else {
                showToast("选区太小或已取消", false);
            }
        });
        selector.showSelector();
    }

    // ==================== Toast（1 秒自动消失） ====================

    private void showToast(String msg, boolean success) {
        if (toastWindow != null) { toastWindow.dispose(); toastWindow = null; }
        if (toastTimer != null) { toastTimer.stop(); toastTimer = null; }

        toastWindow = new JWindow();
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(success ? new Color(16, 185, 129, 235)
                : new Color(220, 38, 38, 235));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(success ? new Color(34, 197, 94)
                        : new Color(248, 113, 113), 1),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        JLabel label = new JLabel(msg);
        label.setForeground(Color.WHITE);
        label.setFont(FontUtil.getUIFont(Font.PLAIN, 11));
        panel.add(label, BorderLayout.CENTER);
        toastWindow.setContentPane(panel);
        toastWindow.pack();

        Point loc = getLocationOnScreen();
        int x = loc.x;
        int y = loc.y - toastWindow.getHeight() - 4;
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        if (x + toastWindow.getWidth() > screen.width) {
            x = screen.width - toastWindow.getWidth() - 8;
        }
        if (x < 0) x = 8;
        if (y < 0) y = loc.y + getHeight() + 4; // 顶部空间不足时显示在下方
        toastWindow.setLocation(x, y);
        toastWindow.setAlwaysOnTop(true);
        toastWindow.setVisible(true);

        toastTimer = new Timer(TOAST_DURATION_MS, e -> {
            if (toastWindow != null) { toastWindow.dispose(); toastWindow = null; }
        });
        toastTimer.setRepeats(false);
        toastTimer.start();
    }

    // ==================== 显示与定位 ====================

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            if (!hasUserMoved) {
                Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
                setLocation(screen.width - getWidth() - 30, screen.height - getHeight() - 80);
            }
        } else {
            if (toastWindow != null) { toastWindow.dispose(); toastWindow = null; }
            if (toastTimer != null) { toastTimer.stop(); toastTimer = null; }
        }
    }
}