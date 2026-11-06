package com.omni.common.util;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

/**
 * 摘要工具类（MD5 / SHA-256）。
 */
public final class DigestUtils {

    private DigestUtils() {}

    public static String md5Hex(byte[] data) {
        if (data == null) return null;
        try {
            return hex(MessageDigest.getInstance("MD5").digest(data));
        } catch (Exception e) {
            throw new RuntimeException("MD5 计算失败", e);
        }
    }

    public static String md5Hex(String text) {
        if (text == null) return null;
        return md5Hex(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String md5Hex(Path file) throws Exception {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            return hex(md.digest());
        }
    }

    public static String sha256Hex(byte[] data) {
        if (data == null) return null;
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 计算失败", e);
        }
    }

    public static String sha256Hex(String text) {
        if (text == null) return null;
        return sha256Hex(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }
}