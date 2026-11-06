package com.omni.common.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 文件读写工具类。
 * <p>统一 UTF-8 编码，所有方法均自动创建父目录（写操作）。</p>
 */
public final class FileIOUtils {

    private FileIOUtils() {}

    public static String readString(Path file) throws IOException {
        byte[] data = Files.readAllBytes(file);
        return new String(data, StandardCharsets.UTF_8);
    }

    public static void writeString(Path file, String content) throws IOException {
        Path parent = file.getParent();
        if (parent != null && !Files.exists(parent)) Files.createDirectories(parent);
        Files.write(file, content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8));
    }

    public static void appendString(Path file, String content) throws IOException {
        Path parent = file.getParent();
        if (parent != null && !Files.exists(parent)) Files.createDirectories(parent);
        Files.write(file, content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
    }

    public static List<String> readLines(Path file) throws IOException {
        return Files.readAllLines(file, StandardCharsets.UTF_8);
    }

    public static byte[] readBytes(Path file) throws IOException {
        return Files.readAllBytes(file);
    }

    public static void writeBytes(Path file, byte[] data) throws IOException {
        Path parent = file.getParent();
        if (parent != null && !Files.exists(parent)) Files.createDirectories(parent);
        Files.write(file, data == null ? new byte[0] : data);
    }
}