package com.framework.v4j.component;

import java.util.HashMap;
import java.util.Map;

/**
 * 模拟 CSS 样式对象，最终可渲染为 style 属性字符串
 */
public class Style {
    private final Map<String, String> styles = new HashMap<>();

    public Style set(String property, String value) {
        styles.put(property, value);
        return this;
    }

    public String toInlineStyle() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : styles.entrySet()) {
            sb.append(e.getKey()).append(":").append(e.getValue()).append(";");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return toInlineStyle();
    }
}