package com.omni.framework.spring.mvc;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 最终修复版：彻底解决最后一步卡死
 */
public class MultipartResolver implements AutoCloseable {
    private static final int CONTENT_BUFFER = 8192;
    private final BufferedInputStream inputStream;
    private final byte[] boundary;
    private final Map<String, String> textFields = new HashMap<>();
    private final Map<String, FileItem> fileFields = new HashMap<>();
    private boolean closed = false;

    public MultipartResolver(String contentType, InputStream inputStream) throws IOException {
        this.inputStream = new BufferedInputStream(inputStream, CONTENT_BUFFER * 2);
        this.inputStream.mark(CONTENT_BUFFER * 10);
        printMultipartStructure(contentType);
        this.inputStream.reset();

        String boundaryStr = extractBoundary(contentType);
        if (boundaryStr == null || boundaryStr.isBlank()) {
            throw new IllegalArgumentException("缺少 boundary");
        }
        this.boundary = ("--" + boundaryStr).getBytes(StandardCharsets.US_ASCII);

        BoundaryFinder finder = new BoundaryFinder(this.inputStream, this.boundary);
        parse(finder);
    }

    private void printMultipartStructure(String contentType) {
        System.out.println("========== Multipart 请求结构（不包含文件内容）==========");
        System.out.println("Content-Type: " + contentType);
        String boundaryStr = extractBoundary(contentType);
        if (boundaryStr == null) {
            System.err.println("无法提取 boundary");
            return;
        }

        try {
            byte[] lineBuf = new byte[4096];
            boolean firstBoundaryFound = false;
            StringBuilder currentHeaders = new StringBuilder();
            boolean inHeaders = false;

            while (true) {
                int lineLen = readLineSafe(inputStream, lineBuf);
                if (lineLen <= 0) break;

                String line = new String(lineBuf, 0, lineLen, StandardCharsets.US_ASCII);
                boolean isBoundary = line.startsWith("--" + boundaryStr);

                if (isBoundary) {
                    if (!currentHeaders.isEmpty()) {
                        printPartInfo(parseHeaders(currentHeaders.toString()));
                        currentHeaders.setLength(0);
                    }
                    if (line.trim().endsWith("--")) {
                        System.out.println("-- 结束边界 --");
                        break;
                    }
                    firstBoundaryFound = true;
                    inHeaders = true;
                    continue;
                }
                if (firstBoundaryFound && inHeaders) {
                    if (line.isEmpty()) inHeaders = false;
                    else currentHeaders.append(line).append("\n");
                }
            }
            if (!currentHeaders.isEmpty()) {
                printPartInfo(parseHeaders(currentHeaders.toString()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("=====================================================");
    }

    private int readLineSafe(InputStream is, byte[] buffer) throws IOException {
        int pos = 0;
        int b;
        while (pos < buffer.length) {
            b = is.read();
            if (b == -1) return pos > 0 ? pos : -1;
            buffer[pos++] = (byte) b;
            if (b == '\n') return pos;
        }
        return pos;
    }

    private Map<String, String> parseHeaders(String headerBlock) {
        Map<String, String> headers = new HashMap<>();
        for (String line : headerBlock.split("\n")) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                String name = line.substring(0, colon).trim().toLowerCase();
                String value = line.substring(colon + 1).trim();
                headers.put(name, value);
            }
        }
        return headers;
    }

    private void printPartInfo(Map<String, String> headers) {
        String disposition = headers.get("content-disposition");
        if (disposition == null) return;
        String name = extractHeaderValue(disposition, "name");
        String filename = extractHeaderValue(disposition, "filename");
        String contentType = headers.get("content-type");
        if (filename != null && !filename.isEmpty()) {
            System.out.println("文件字段: name=" + name + ", filename=" + filename + ", contentType=" + (contentType != null ? contentType : "application/octet-stream"));
        } else {
            System.out.println("文本字段: name=" + name);
        }
    }

    private String extractHeaderValue(String header, String paramName) {
        if (header == null) return null;
        for (String part : header.split(";")) {
            part = part.trim().toLowerCase();
            String key = paramName + "=";
            if (part.startsWith(key)) {
                String val = part.substring(key.length()).trim();
                if (val.startsWith("\"") && val.endsWith("\"")) val = val.substring(1, val.length() - 1);
                return val;
            }
        }
        return null;
    }

    private String extractBoundary(String contentType) {
        if (contentType == null || !contentType.startsWith("multipart/form-data")) return null;
        for (String part : contentType.split(";")) {
            part = part.trim();
            if (part.startsWith("boundary=")) {
                String val = part.substring(9).trim();
                if (val.startsWith("\"") && val.endsWith("\"")) val = val.substring(1, val.length() - 1);
                return val;
            }
        }
        return null;
    }

    // ==================== 修复 parse 逻辑 ====================
    private void parse(BoundaryFinder finder) throws IOException {
        System.out.println("[DEBUG] 开始查找起始边界...");
        if (!finder.findBoundary()) throw new IOException("找不到起始边界");
        System.out.println("[DEBUG] 起始边界已找到");

        while (true) {
            System.out.println("[DEBUG] 读取 part 头部...");
            Map<String, String> headers = readPartHeaders(finder);

            // ===================== 【修复 1：空头部直接退出】=====================
            if (headers == null || headers.isEmpty()) {
                System.out.println("[DEBUG] 空头部，解析结束");
                break;
            }

            String dis = headers.get("content-disposition");
            if (dis == null) break;

            String name = extractHeaderValue(dis, "name");
            if (name == null || name.isBlank()) continue;

            String filename = extractHeaderValue(dis, "filename");
            if (filename != null) {
                System.out.println("[DEBUG] 解析文件: " + name);
                Path path = readFileContent(finder);
                fileFields.put(name, new FileItem(name, filename, headers.getOrDefault("content-type", "application/octet-stream"), Files.size(path), path));
            } else {
                System.out.println("[DEBUG] 解析文本: " + name);
                textFields.put(name, readTextContent(finder));
            }

            System.out.println("[DEBUG] 查找下一个边界...");
            boolean found = finder.findBoundary();
            if (!found) {
                System.out.println("[DEBUG] 未找到边界，解析结束");
                break;
            }
            if (finder.isClosingBoundary()) {
                System.out.println("[DEBUG] 遇到结束边界，解析完成");
                break;
            }
        }
        System.out.println("[DEBUG] 解析完成，文本字段=" + textFields.size() + "，文件字段=" + fileFields.size());
    }

    private Map<String, String> readPartHeaders(BoundaryFinder finder) throws IOException {
        Map<String, String> headers = new HashMap<>();
        while (true) {
            String line = readLine(finder);
            // ===================== 【修复 2：null 直接退出】=====================
            if (line == null) {
                System.out.println("[DEBUG] readLine 返回 null，结束头部读取");
                return headers;
            }
            if (line.isEmpty()) break;
            int colon = line.indexOf(':');
            if (colon > 0) {
                headers.put(line.substring(0, colon).trim().toLowerCase(), line.substring(colon + 1).trim());
            }
        }
        return headers;
    }

    private String readLine(BoundaryFinder finder) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(256);
        byte[] buf = new byte[CONTENT_BUFFER];
        while (true) {
            int r = finder.readBuffer(buf);
            // ===================== 【修复 3：流结束返回 null】=====================
            if (r <= 0) {
                return baos.size() == 0 ? null : baos.toString(StandardCharsets.US_ASCII);
            }
            for (int i = 0; i < r; i++) {
                if (buf[i] == '\n') {
                    baos.write(buf, 0, i);
                    int remain = r - i - 1;
                    if (remain > 0) finder.setPosition(finder.getPosition() - remain);
                    String line = baos.toString(StandardCharsets.US_ASCII);
                    return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
                }
            }
            baos.write(buf, 0, r);
        }
    }

    private Path readFileContent(BoundaryFinder finder) throws IOException {
        Path tmp = Files.createTempFile("upload_", ".tmp");
        try (BufferedOutputStream out = new BufferedOutputStream(Files.newOutputStream(tmp))) {
            while (true) {
                int offset = finder.findBoundaryInBuffer();
                if (offset >= 0) {
                    if (offset > 0) out.write(finder.buffer, finder.getPosition(), offset);
                    finder.setPosition(finder.getPosition() + offset);
                    break;
                }
                int available = finder.limit - finder.pos;
                if (available > 0) {
                    out.write(finder.buffer, finder.pos, available);
                    finder.setPosition(finder.limit);
                }
                if (finder.pos >= finder.limit) {
                    int read = finder.input.read(finder.buffer);
                    if (read <= 0) break;
                    finder.pos = 0;
                    finder.limit = read;
                }
            }
            return tmp;
        } catch (Exception e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
    }

    private String readTextContent(BoundaryFinder finder) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while (true) {
            int offset = finder.findBoundaryInBuffer();
            if (offset >= 0) {
                if (offset > 0) baos.write(finder.buffer, finder.getPosition(), offset);
                finder.setPosition(finder.getPosition() + offset);
                break;
            }
            int available = finder.limit - finder.pos;
            if (available > 0) {
                baos.write(finder.buffer, finder.pos, available);
                finder.setPosition(finder.limit);
            }
            if (finder.pos >= finder.limit) {
                int read = finder.input.read(finder.buffer);
                if (read <= 0) break;
                finder.pos = 0;
                finder.limit = read;
            }
        }
        String text = baos.toString(StandardCharsets.UTF_8);
        if (text.endsWith("\r\n")) return text.substring(0, text.length() - 2);
        if (text.endsWith("\n")) return text.substring(0, text.length() - 1);
        return text;
    }

    public String getParameter(String name) { return textFields.get(name); }
    public FileItem getFileItem(String name) { return fileFields.get(name); }

    @Override
    public void close() {
        if (closed) return;
        fileFields.values().forEach(i -> { try { i.close(); } catch (Exception ignored) {} });
        closed = true;
    }

    // ==================== BoundaryFinder 不变 ====================
    private static class BoundaryFinder {
        private final InputStream input;
        private final byte[] boundary;
        private final int boundaryLen;
        private static final int BUFFER_SIZE = 16384;
        private final byte[] buffer = new byte[BUFFER_SIZE];
        private int pos = 0;
        private int limit = 0;

        public BoundaryFinder(InputStream input, byte[] boundary) {
            this.input = input;
            this.boundary = boundary;
            this.boundaryLen = boundary.length;
        }

        public boolean findBoundary() throws IOException {
            if (boundaryLen == 0) return false;
            int match = 0;
            while (true) {
                if (pos >= limit) {
                    int read = input.read(buffer);
                    if (read <= 0) return false;
                    pos = 0;
                    limit = read;
                }
                byte b = buffer[pos++];
                if (b == boundary[match]) {
                    match++;
                    if (match == boundaryLen) {
                        byte[] peek = new byte[4];
                        int peekLen = peekBytes(peek, 0, 4);
                        if (peekLen >= 2 && peek[0] == '-' && peek[1] == '-') {
                            pos += 2;
                            if (peekLen >= 4 && peek[2] == '\r' && peek[3] == '\n') pos += 2;
                            else if (peekLen >= 1 && peek[2] == '\n') pos += 1;
                        } else if (peekLen >= 2 && peek[0] == '\r' && peek[1] == '\n') {
                            pos += 2;
                        } else if (peekLen >= 1 && peek[0] == '\n') {
                            pos += 1;
                        }
                        return true;
                    }
                } else {
                    if (match > 0) {
                        pos -= match;
                        match = 0;
                    }
                }
            }
        }

        private int peekBytes(byte[] dest, int off, int len) {
            int avail = Math.min(len, limit - pos);
            if (avail > 0) System.arraycopy(buffer, pos, dest, off, avail);
            return avail;
        }

        public boolean isClosingBoundary() {
            byte[] peek = new byte[2];
            int len = peekBytes(peek, 0, 2);
            return len == 2 && peek[0] == '-' && peek[1] == '-';
        }

        public int readBuffer(byte[] dest) throws IOException {
            if (pos >= limit) {
                int read = input.read(buffer);
                if (read <= 0) return -1;
                pos = 0;
                limit = read;
            }
            int cp = Math.min(dest.length, limit - pos);
            System.arraycopy(buffer, pos, dest, 0, cp);
            pos += cp;
            return cp;
        }

        public int findBoundaryInBuffer() {
            for (int i = pos; i <= limit - boundaryLen; i++) {
                boolean ok = true;
                for (int j = 0; j < boundaryLen; j++) {
                    if (buffer[i + j] != boundary[j]) {
                        ok = false;
                        break;
                    }
                }
                if (ok) return i - pos;
            }
            return -1;
        }

        public int getPosition() { return pos; }
        public void setPosition(int pos) { this.pos = Math.max(0, Math.min(pos, limit)); }
    }

}