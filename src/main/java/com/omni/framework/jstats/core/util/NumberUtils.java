package com.omni.framework.jstats.util;

public class NumberUtils {
    public static long parseLongSafely(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            try {
                double d = Double.parseDouble(s);
                return (long) d;
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("无法解析为数字: " + s, ex);
            }
        }
    }

    public static double parseDoubleSafely(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无法解析为浮点数: " + s, e);
        }
    }
}