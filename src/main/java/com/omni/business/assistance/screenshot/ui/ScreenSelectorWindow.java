package com.omni.business.assistance.screenshot.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ScreenSelectorWindow extends JWindow {
    private Rectangle selectionRect;
    private Point startPoint, endPoint;
    private Runnable onCapture;

    public ScreenSelectorWindow(Runnable onCapture) {
        this.onCapture = onCapture;
        setSize(Toolkit.getDefaultToolkit().getScreenSize());
        setAlwaysOnTop(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

        // 半透明遮罩（类似全屏覆盖）
        setBackground(new Color(0, 0, 0, 80));

        // 鼠标事件
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                startPoint = e.getPoint();
                selectionRect = new Rectangle(startPoint);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (selectionRect != null && selectionRect.width > 5 && selectionRect.height > 5) {
                    setVisible(false);
                    if (onCapture != null) onCapture.run();
                } else {
                    // 选区太小，取消
                    dispose();
                }
            }
        });

        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                endPoint = e.getPoint();
                if (startPoint != null) {
                    int x = Math.min(startPoint.x, endPoint.x);
                    int y = Math.min(startPoint.y, endPoint.y);
                    int w = Math.abs(startPoint.x - endPoint.x);
                    int h = Math.abs(startPoint.y - endPoint.y);
                    selectionRect = new Rectangle(x, y, w, h);
                    repaint();
                }
            }
        });
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        if (selectionRect != null && selectionRect.width > 0 && selectionRect.height > 0) {
            Graphics2D g2d = (Graphics2D) g;
            // 高亮选区（反色）
            g2d.setColor(new Color(0, 102, 255, 60));
            g2d.fill(selectionRect);
            g2d.setColor(Color.WHITE);
            g2d.setStroke(new BasicStroke(2));
            g2d.draw(selectionRect);
            // 显示尺寸
            String sizeText = selectionRect.width + " x " + selectionRect.height;
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Inter", Font.BOLD, 14));
            g2d.drawString(sizeText, selectionRect.x + 8, selectionRect.y + 24);
        }
    }

    public Rectangle getSelectionRect() {
        return selectionRect;
    }

    public void showSelector() {
        setVisible(true);
        toFront();
        requestFocus();
    }
}