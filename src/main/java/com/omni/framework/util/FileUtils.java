package com.omni.framework.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 文件/流操作工具类（Java 8 兼容）
 */
public final class FileUtils {

    private static final int DEFAULT_BUFFER_SIZE = 8192;

    private FileUtils() {
        // 私有构造，防止实例化
    }

    /**
     * 将 InputStream 读取为 String（UTF-8 编码）
     * @param inputStream 输入流
     * @return 字符串内容
     * @throws IOException 读取失败时抛出
     */
    public static String readInputStreamToString(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return null;
        }
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString(StandardCharsets.UTF_8.name());
    }

    /**
     * 将 InputStream 中的数据全部传输到 OutputStream（等价于 Java 9+ 的 inputStream.transferTo(output)）
     * @param input  源输入流
     * @param output 目标输出流
     * @throws IOException 读写异常时抛出
     */
    public static void transferTo(InputStream input, OutputStream output) throws IOException {
        transferTo(input, output, DEFAULT_BUFFER_SIZE);
    }

    /**
     * 将 InputStream 中的数据全部传输到 OutputStream，可指定缓冲区大小
     * @param input        源输入流
     * @param output       目标输出流
     * @param bufferSize   缓冲区大小（字节）
     * @throws IOException 读写异常时抛出
     */
    public static void transferTo(InputStream input, OutputStream output, int bufferSize) throws IOException {
        if (input == null || output == null) {
            throw new IllegalArgumentException("输入流和输出流不能为 null");
        }
        byte[] buffer = new byte[bufferSize];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
        output.flush(); // 确保所有数据写出
    }
}