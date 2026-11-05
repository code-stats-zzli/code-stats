package com.omni.business.assistance.screenshot.util;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 截图助手 UI 字体工具类。
 *
 * <p>解决的问题：
 * <ul>
 *   <li>硬编码 {@code new Font("微软雅黑", ...)} 在 Linux/macOS 上字体不存在，
 *       JVM 回退到逻辑字体 Dialog，部分环境中文渲染为方块。</li>
 *   <li>emoji 字符（📷 ✖ 等）不在微软雅黑字形表中，Swing font fallback
 *       在 JDK8 / 部分系统上失败，显示为方块。</li>
 * </ul>
 *
 * <p>策略：按优先级探测系统可用字体，全平台兜底为逻辑字体 {@code Dialog}。
 * 所有 UI 类统一通过本工具获取字体，禁止再硬编码字体名。
 */
public final class FontUtil {

    private FontUtil() {}

    /**
     * 字体探测优先级：
     * Windows → macOS → Linux → 通用逻辑字体
     */
    private static final String[] PREFERRED_FONTS = {
            // Windows
            "Microsoft YaHei UI",
            "Microsoft YaHei",
            "微软雅黑",
            "SimHei",
            // macOS
            "PingFang SC",
            "Hiragino Sans GB",
            "STHeiti",
            // Linux
            "Noto Sans CJK SC",
            "Source Han Sans SC",
            "WenQuanYi Micro Hei",
            "WenQuanYi Zen Hei",
            // 兜底：Java 逻辑字体，由 JVM 映射到系统默认字体
            "Dialog"
    };

    private static volatile String cachedFamily;

    /**
     * 获取当前系统可用的 UI 字体族名。
     */
    public static String getUIFontFamily() {
        String family = cachedFamily;
        if (family != null) return family;
        synchronized (FontUtil.class) {
            if (cachedFamily != null) return cachedFamily;
            Set<String> available = new HashSet<>(Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            for (String name : PREFERRED_FONTS) {
                if (available.contains(name)) {
                    cachedFamily = name;
                    return cachedFamily;
                }
            }
            // 极端情况：连 Dialog 都不在列表里，直接返回 Dialog
            cachedFamily = "Dialog";
            return cachedFamily;
        }
    }

    /**
     * 直接获取指定样式的 UI 字体。
     */
    public static Font getUIFont(int style, int size) {
        return new Font(getUIFontFamily(), style, size);
    }

    /**
     * 从 UIManager 的某个键派生字体，保证与全局字体配置一致。
     * 若该键未设置，则回退到 {@link #getUIFont(int, int)}。
     *
     * @param uiKey UIManager 键，如 "Button.font"、"Label.font"
     */
    public static Font deriveUIFont(String uiKey, int style, int size) {
        Font base = UIManager.getFont(uiKey);
        if (base == null) {
            base = getUIFont(Font.PLAIN, 12);
        }
        return base.deriveFont(style, size);
    }
}