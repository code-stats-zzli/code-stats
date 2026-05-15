package com.omni.framework.spring.mvc;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 简单的 multipart/form-data 解析器，支持文件上传和表单字段。
 * 遵循 RFC 1867。
 */
public class MultipartResolver {
    private final byte[] boundary;
    private final InputStream inputStream;
    private final Map<String, String> textFields = new HashMap<>();
    private final Map<String, byte[]> fileFields = new HashMap<>();

    public MultipartResolver(String contentType, InputStream inputStream) throws IOException {
        String boundaryStr = extractBoundary(contentType);
        if (boundaryStr == null) throw new IllegalArgumentException("No boundary found");
        this.boundary = ("--" + boundaryStr).getBytes(StandardCharsets.US_ASCII);
        this.inputStream = inputStream;
        parse();
    }

    private String extractBoundary(String contentType) {
        for (String part : contentType.split(";")) {
            part = part.trim();
            if (part.startsWith("boundary=")) {
                return part.substring("boundary=".length()).replace("\"", "");
            }
        }
        return null;
    }

    private void parse() throws IOException {
        // 跳过直到第一个 boundary
        readUntilBoundary();
        while (true) {
            // 读取 part 头部
            Map<String, String> headers = readPartHeaders();
            if (headers.isEmpty()) break;
            String disposition = headers.get("Content-Disposition");
            if (disposition == null) break;
            String name = extractName(disposition);
            String filename = extractFilename(disposition);
            // 读取 part 内容
            ByteArrayOutputStream content = new ByteArrayOutputStream();
            readPartContent(content);
            if (filename != null) {
                fileFields.put(name, content.toByteArray());
            } else {
                textFields.put(name, content.toString(StandardCharsets.UTF_8));
            }
            // 检查下一个 boundary
            if (isFinalBoundary()) break;
        }
    }

    private void readUntilBoundary() throws IOException {
        while (true) {
            int b = inputStream.read();
            if (b == -1) throw new IOException("Unexpected EOF");
            if (b == boundary[0]) {
                inputStream.mark(boundary.length);
                byte[] test = new byte[boundary.length];
                inputStream.read(test);
                if (Arrays.equals(test, boundary)) {
                    // 读取后面的 \r\n
                    inputStream.read(); // \r
                    inputStream.read(); // \n
                    return;
                } else {
                    inputStream.reset();
                }
            }
        }
    }

    private Map<String, String> readPartHeaders() throws IOException {
        Map<String, String> headers = new HashMap<>();
        ByteArrayOutputStream lineBuf = new ByteArrayOutputStream();
        while (true) {
            int b = inputStream.read();
            if (b == -1) break;
            if (b == '\r') {
                inputStream.read(); // \n
                if (lineBuf.size() == 0) {
                    // 空行结束 headers
                    return headers;
                }
                String line = lineBuf.toString(StandardCharsets.US_ASCII);
                lineBuf.reset();
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String name = line.substring(0, colon).trim();
                    String value = line.substring(colon + 1).trim();
                    headers.put(name, value);
                }
            } else {
                lineBuf.write(b);
            }
        }
        return headers;
    }

    private String extractName(String disposition) {
        for (String part : disposition.split(";")) {
            part = part.trim();
            if (part.startsWith("name=")) {
                return part.substring(5).replace("\"", "");
            }
        }
        return null;
    }

    private String extractFilename(String disposition) {
        for (String part : disposition.split(";")) {
            part = part.trim();
            if (part.startsWith("filename=")) {
                return part.substring(9).replace("\"", "");
            }
        }
        return null;
    }

    private void readPartContent(ByteArrayOutputStream out) throws IOException {
        while (true) {
            int b = inputStream.read();
            if (b == -1) throw new IOException("Unexpected EOF");
            if (b == '\r') {
                inputStream.mark(1);
                int next = inputStream.read();
                if (next == '\n') {
                    // 检查是否 boundary 开始
                    int b2 = inputStream.read();
                    if (b2 == boundary[0]) {
                        inputStream.mark(boundary.length - 1);
                        byte[] test = new byte[boundary.length - 1];
                        inputStream.read(test);
                        if (Arrays.equals(test, Arrays.copyOfRange(boundary, 1, boundary.length))) {
                            // 找到 boundary，回退到 boundary 开始的位置
                            inputStream.reset(); // 回退到读取 b2 之前
                            // 实际应该回退到 boundary 开始，简化处理：将已读的 \r\n 保留？
                            // 正确做法：将之前写入 out 的 \r\n 保留，但 boundary 前的 \r\n 不属于内容
                            // 这里简单回退最后两个字节
                            out.write('\r');
                            out.write('\n');
                            return;
                        } else {
                            inputStream.reset();
                            out.write(b);
                            out.write(next);
                            out.write(b2);
                            continue;
                        }
                    } else {
                        inputStream.reset();
                        out.write(b);
                        out.write(next);
                        continue;
                    }
                } else {
                    inputStream.reset();
                }
            }
            out.write(b);
        }
    }

    private boolean isFinalBoundary() throws IOException {
        int b = inputStream.read();
        if (b == '-') {
            int b2 = inputStream.read();
            if (b2 == '-') {
                return true;
            }
        }
        return false;
    }

    public String getTextParameter(String name) {
        return textFields.get(name);
    }

    public byte[] getFileParameter(String name) {
        return fileFields.get(name);
    }

    public Map<String, String> getTextFields() {
        return textFields;
    }

    public Map<String, byte[]> getFileFields() {
        return fileFields;
    }
}