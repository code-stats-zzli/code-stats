package com.omni.framework.spring.mvc;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class MultipartResolver {
    private final byte[] boundary;
    private final BufferedInputStream inputStream;
    private final Map<String, String> textFields = new HashMap<>();
    private final Map<String, FileItem> fileFields = new HashMap<>();

    public MultipartResolver(String contentType, InputStream inputStream) throws IOException {
        if (inputStream instanceof BufferedInputStream) {
            this.inputStream = (BufferedInputStream) inputStream;
        } else {
            this.inputStream = new BufferedInputStream(inputStream);
        }
        String boundaryStr = extractBoundary(contentType);
        if (boundaryStr == null) {
            throw new IllegalArgumentException("No boundary found in Content-Type");
        }
        this.boundary = ("--" + boundaryStr).getBytes(StandardCharsets.US_ASCII);
        this.inputStream.mark(1024 * 1024);
        parse();
    }

    private String extractBoundary(String contentType) {
        for (String part : contentType.split(";")) {
            part = part.trim();
            if (part.startsWith("boundary=")) {
                String boundary = part.substring("boundary=".length());
                if (boundary.startsWith("\"") && boundary.endsWith("\"")) {
                    boundary = boundary.substring(1, boundary.length() - 1);
                }
                return boundary;
            }
        }
        return null;
    }

    private boolean findBoundary() throws IOException {
        this.inputStream.mark(8192);
        int b;
        while ((b = this.inputStream.read()) != -1) {
            if (b == boundary[0]) {
                this.inputStream.mark(boundary.length + 2);
                byte[] test = new byte[boundary.length];
                int read = this.inputStream.read(test);
                if (read == boundary.length && Arrays.equals(test, boundary)) {
                    int cr = this.inputStream.read();
                    if (cr == '\r') {
                        int lf = this.inputStream.read();
                        if (lf != '\n') {
                            this.inputStream.reset();
                        }
                    } else if (cr == '\n') {
                        // normal
                    } else if (cr == '-') {
                        this.inputStream.reset();
                        return true;
                    } else {
                        this.inputStream.reset();
                    }
                    return true;
                } else {
                    this.inputStream.reset();
                    this.inputStream.mark(8192);
                }
            }
        }
        return false;
    }

    private Map<String, String> readPartHeaders() throws IOException {
        Map<String, String> headers = new HashMap<>();
        ByteArrayOutputStream lineBuf = new ByteArrayOutputStream();
        while (true) {
            int b = this.inputStream.read();
            if (b == -1) break;
            if (b == '\r') {
                int next = this.inputStream.read();
                if (next == '\n') {
                    if (lineBuf.size() == 0) {
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
                    lineBuf.write(next);
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
                String value = part.substring(5);
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                return value;
            }
        }
        return null;
    }

    private String extractFilename(String disposition) {
        for (String part : disposition.split(";")) {
            part = part.trim();
            if (part.startsWith("filename=")) {
                String value = part.substring(9);
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                return value;
            }
        }
        return null;
    }

    private Path readPartContentToTempFile() throws IOException {
        Path tempFile = Files.createTempFile("upload_", ".tmp");
        try (OutputStream out = Files.newOutputStream(tempFile)) {
            int matchIndex = 0;
            while (true) {
                int b = this.inputStream.read();
                if (b == -1) break;
                if (b == boundary[matchIndex]) {
                    matchIndex++;
                    if (matchIndex == boundary.length) {
                        // 边界匹配完成，停止写入并返回临时文件
                        return tempFile;
                    }
                } else {
                    if (matchIndex > 0) {
                        for (int i = 0; i < matchIndex; i++) {
                            out.write(boundary[i]);
                        }
                        matchIndex = 0;
                    }
                    out.write(b);
                }
            }
        }
        return tempFile;
    }

    private void parse() throws IOException {
        if (!findBoundary()) throw new IOException("No starting boundary found");
        while (true) {
            Map<String, String> headers = readPartHeaders();
            if (headers.isEmpty()) break;
            String disposition = headers.get("Content-Disposition");
            if (disposition == null) break;
            String name = extractName(disposition);
            String filename = extractFilename(disposition);

            if (filename != null) {
                // 文件字段：写入临时文件
                Path temp = readPartContentToTempFile();
                long size = Files.size(temp);
                String contentType = headers.getOrDefault("Content-Type", "application/octet-stream");
                FileItem item = new FileItem(name, filename, contentType, size, temp);
                fileFields.put(name, item);
            } else {
                // 普通文本字段：读取内容
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                int matchIndex = 0;
                while (true) {
                    int b = this.inputStream.read();
                    if (b == -1) break;
                    if (b == boundary[matchIndex]) {
                        matchIndex++;
                        if (matchIndex == boundary.length) {
                            // 边界结束，停止读取
                            break;
                        }
                    } else {
                        if (matchIndex > 0) {
                            for (int i = 0; i < matchIndex; i++) {
                                baos.write(boundary[i]);
                            }
                            matchIndex = 0;
                        }
                        baos.write(b);
                    }
                }
                textFields.put(name, new String(baos.toByteArray(), StandardCharsets.UTF_8));
            }

            // 检查是否为结束边界
            int next1 = this.inputStream.read();
            int next2 = this.inputStream.read();
            if (next1 == '-' && next2 == '-') {
                break;
            }
            if (!findBoundary()) break;
        }
    }

    public String getTextParameter(String name) {
        return textFields.get(name);
    }

    public FileItem getFileItem(String name) {
        return fileFields.get(name);
    }

    @Deprecated
    public byte[] getFileParameter(String name) {
        FileItem item = fileFields.get(name);
        if (item == null) return null;
        try {
            return item.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("读取临时文件失败", e);
        }
    }

    public Map<String, String> getTextFields() {
        return textFields;
    }

    public Map<String, FileItem> getFileFields() {
        return fileFields;
    }

    public void cleanup() {
        for (FileItem item : fileFields.values()) {
            try {
                item.close();
            } catch (IOException e) {
                // log error
                e.printStackTrace();
            }
        }
        fileFields.clear();
    }
}