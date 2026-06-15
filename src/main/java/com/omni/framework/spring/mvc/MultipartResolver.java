package com.omni.framework.spring.mvc;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 最终修复版：正确识别结束边界，避免死循环
 */
public class MultipartResolver implements AutoCloseable {
    private static final int CONTENT_BUFFER = 8192;
    private final BufferedInputStream inputStream;
    private final byte[] boundary;
    private final Map<String, String> textFields = new HashMap<>();
    private final Map<String, FileItem> fileFields = new HashMap<>();
    private boolean closed = false;
    private static final boolean DEBUG = true;

    private static void log(String msg) {
        if (DEBUG) System.out.println("[MultipartResolver] " + msg);
    }

    public MultipartResolver(String contentType, InputStream inputStream) throws IOException {
        log("初始化 MultipartResolver, Content-Type: " + contentType);
        this.inputStream = new BufferedInputStream(inputStream, CONTENT_BUFFER * 2);
        String boundaryStr = extractBoundary(contentType);
        if (boundaryStr == null || boundaryStr.isBlank()) {
            throw new IllegalArgumentException("缺少 boundary");
        }
        log("提取 boundary: " + boundaryStr);
        this.boundary = ("--" + boundaryStr).getBytes(StandardCharsets.US_ASCII);
        BoundaryFinder finder = new BoundaryFinder(this.inputStream, this.boundary);
        parse(finder);
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

    private void parse(BoundaryFinder finder) throws IOException {
        log("开始解析 multipart 数据");
        if (!finder.findBoundary()) {
            log("错误：找不到起始边界");
            throw new IOException("找不到起始边界");
        }
        log("找到起始边界");

        int partCount = 0;
        while (true) {
            // 如果当前已是结束边界，直接退出
            if (finder.isClosingBoundary()) {
                log("检测到结束边界，退出解析");
                break;
            }
            partCount++;
            log("解析第 " + partCount + " 个 part 的头部...");
            Map<String, String> headers = readPartHeaders(finder);
            if (headers == null || headers.isEmpty()) {
                log("part 头部为空，解析结束");
                break;
            }

            String dis = headers.get("content-disposition");
            if (dis == null) {
                log("content-disposition 缺失，跳过该 part");
                continue;
            }

            String name = extractHeaderValue(dis, "name");
            if (name == null || name.isBlank()) {
                log("name 为空，跳过");
                continue;
            }

            String filename = extractHeaderValue(dis, "filename");
            if (filename != null) {
                log("发现文件字段: name=" + name + ", filename=" + filename);
                Path path = readFileContent(finder);
                fileFields.put(name, new FileItem(name, filename, headers.getOrDefault("content-type", "application/octet-stream"), Files.size(path), path));
                log("文件已保存到临时文件: " + path);
            } else {
                log("发现文本字段: name=" + name);
                String text = readTextContent(finder);
                textFields.put(name, text);
                log("文本内容长度: " + (text != null ? text.length() : 0));
            }

            log("查找下一个边界...");
            boolean found = finder.findBoundary();
            if (!found) {
                log("未找到下一个边界，解析结束");
                break;
            }
            // 重要：遇到结束边界则终止循环
            if (finder.isClosingBoundary()) {
                log("遇到结束边界，解析完成");
                break;
            }
        }
        log("解析完成，共解析 " + partCount + " 个 part，文本字段=" + textFields.size() + "，文件字段=" + fileFields.size());
    }

    private Map<String, String> readPartHeaders(BoundaryFinder finder) throws IOException {
        Map<String, String> headers = new HashMap<>();
        while (true) {
            String line = readLine(finder);
            if (line == null) {
                log("readLine 返回 null，结束头部读取");
                return headers.isEmpty() ? null : headers;
            }
            if (line.isEmpty()) {
                log("头部空行，结束头部读取");
                break;
            }
            int colon = line.indexOf(':');
            if (colon > 0) {
                String key = line.substring(0, colon).trim().toLowerCase();
                String value = line.substring(colon + 1).trim();
                headers.put(key, value);
                log("头部: " + key + " = " + value);
            }
        }
        return headers;
    }

    private String readLine(BoundaryFinder finder) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(256);
        byte[] buf = new byte[CONTENT_BUFFER];
        int consecutiveZeroReads = 0;
        while (true) {
            int r = finder.readBuffer(buf);
            if (r == -1) {
                log("readLine: 流结束，返回已有内容");
                return baos.size() == 0 ? null : baos.toString(StandardCharsets.US_ASCII);
            }
            if (r == 0) {
                consecutiveZeroReads++;
                if (consecutiveZeroReads > 10) {
                    log("readLine: 连续10次读取0字节，返回 null");
                    return null;
                }
                log("readLine: readBuffer 返回 0，等待数据，次数=" + consecutiveZeroReads);
                Thread.yield();
                continue;
            }
            consecutiveZeroReads = 0;
            for (int i = 0; i < r; i++) {
                if (buf[i] == '\n') {
                    baos.write(buf, 0, i);
                    int remain = r - i - 1;
                    if (remain > 0) {
                        finder.unread(buf, i + 1, remain);
                    }
                    String line = baos.toString(StandardCharsets.US_ASCII);
                    return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
                }
            }
            baos.write(buf, 0, r);
        }
    }

    private Path readFileContent(BoundaryFinder finder) throws IOException {
        log("开始读取文件内容...");
        Path tmp = Files.createTempFile("upload_", ".tmp");
        try (BufferedOutputStream out = new BufferedOutputStream(Files.newOutputStream(tmp))) {
            long totalWritten = 0;
            while (true) {
                int offset = finder.findBoundaryInBuffer();
                if (offset >= 0) {
                    if (offset > 0) {
                        out.write(finder.buffer, finder.getPosition(), offset);
                        totalWritten += offset;
                        log("文件内容写入 " + offset + " 字节，累计 " + totalWritten);
                    }
                    finder.setPosition(finder.getPosition() + offset);
                    log("文件边界已找到，停止读取");
                    break;
                }
                int available = finder.limit - finder.pos;
                if (available > 0) {
                    out.write(finder.buffer, finder.pos, available);
                    totalWritten += available;
                    log("文件内容写入 " + available + " 字节，累计 " + totalWritten);
                    finder.setPosition(finder.limit);
                }
                if (finder.pos >= finder.limit) {
                    int read = finder.input.read(finder.buffer);
                    if (read <= 0) {
                        log("文件内容读取完毕，未找到边界，强制结束");
                        break;
                    }
                    finder.pos = 0;
                    finder.limit = read;
                    log("刷新缓冲区，读取 " + read + " 字节新数据");
                }
            }
            log("文件内容读取完成，总大小 " + totalWritten + " 字节");
            return tmp;
        } catch (Exception e) {
            Files.deleteIfExists(tmp);
            log("读取文件内容异常: " + e.getMessage());
            throw e;
        }
    }

    private String readTextContent(BoundaryFinder finder) throws IOException {
        log("开始读取文本内容...");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while (true) {
            int offset = finder.findBoundaryInBuffer();
            if (offset >= 0) {
                if (offset > 0) baos.write(finder.buffer, finder.getPosition(), offset);
                finder.setPosition(finder.getPosition() + offset);
                log("文本边界已找到，停止读取");
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
                log("文本缓冲区刷新，读取 " + read + " 字节");
            }
        }
        String text = baos.toString(StandardCharsets.UTF_8);
        if (text.endsWith("\r\n")) text = text.substring(0, text.length() - 2);
        else if (text.endsWith("\n")) text = text.substring(0, text.length() - 1);
        log("文本内容读取完成，长度=" + text.length());
        return text;
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

    public String getParameter(String name) { return textFields.get(name); }
    public FileItem getFileItem(String name) { return fileFields.get(name); }

    @Override
    public void close() {
        if (closed) return;
        fileFields.values().forEach(i -> { try { i.close(); } catch (Exception ignored) {} });
        closed = true;
        log("MultipartResolver 已关闭");
    }

    // ==================== BoundaryFinder 内部类 ====================
    private static class BoundaryFinder {
        private final InputStream input;
        private final byte[] boundary;
        private final int boundaryLen;
        private static final int BUFFER_SIZE = 16384;
        private final byte[] buffer = new byte[BUFFER_SIZE];
        private int pos = 0;
        private int limit = 0;
        private boolean lastBoundaryFound = false;

        public BoundaryFinder(InputStream input, byte[] boundary) {
            this.input = input;
            this.boundary = boundary;
            this.boundaryLen = boundary.length;
            log("BoundaryFinder 初始化，boundary 长度=" + boundaryLen);
        }

        public boolean findBoundary() throws IOException {
            log("findBoundary: 开始查找边界");
            if (boundaryLen == 0) return false;
            int match = 0;
            while (true) {
                if (pos >= limit) {
                    int read = input.read(buffer);
                    if (read <= 0) {
                        log("findBoundary: 流结束，未找到边界");
                        return false;
                    }
                    pos = 0;
                    limit = read;
                    log("findBoundary: 读取 " + read + " 字节，新 limit=" + limit);
                }
                byte b = buffer[pos++];
                if (b == boundary[match]) {
                    match++;
                    if (match == boundaryLen) {
                        log("findBoundary: 匹配到边界，位置 pos=" + pos);
                        // 处理边界后的后缀，并记录是否为结束边界
                        byte[] peek = new byte[4];
                        int peekLen = peekBytes(peek, 0, 4);
                        if (peekLen >= 2 && peek[0] == '-' && peek[1] == '-') {
                            pos += 2;
                            if (peekLen >= 4 && peek[2] == '\r' && peek[3] == '\n') pos += 2;
                            else if (peekLen >= 1 && peek[2] == '\n') pos += 1;
                            lastBoundaryFound = true;
                            log("findBoundary: 结束边界，pos 前进至 " + pos);
                        } else if (peekLen >= 2 && peek[0] == '\r' && peek[1] == '\n') {
                            pos += 2;
                            lastBoundaryFound = false;
                            log("findBoundary: 标准边界 \\r\\n，pos 前进至 " + pos);
                        } else if (peekLen >= 1 && peek[0] == '\n') {
                            pos += 1;
                            lastBoundaryFound = false;
                            log("findBoundary: 标准边界 \\n，pos 前进至 " + pos);
                        }
                        return true;
                    }
                } else {
                    if (match > 0) {
                        pos -= match;
                        match = 0;
                        log("findBoundary: 匹配失败，回退 match，当前 pos=" + pos);
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
            return lastBoundaryFound;
        }

        public int readBuffer(byte[] dest) throws IOException {
            if (pos >= limit) {
                int read = input.read(buffer);
                if (read <= 0) {
                    log("readBuffer: 流结束，返回 " + read);
                    return -1;
                }
                pos = 0;
                limit = read;
                log("readBuffer: 读取新块 " + read + " 字节");
            }
            int cp = Math.min(dest.length, limit - pos);
            System.arraycopy(buffer, pos, dest, 0, cp);
            pos += cp;
            log("readBuffer: 复制 " + cp + " 字节到目标数组，新 pos=" + pos);
            return cp;
        }

        public void unread(byte[] data, int off, int len) {
            if (len > 0) {
                System.arraycopy(data, off, buffer, 0, len);
                pos = 0;
                limit = len;
                log("unread: 回退 " + len + " 字节，pos=0, limit=" + limit);
            }
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
                if (ok) {
                    log("findBoundaryInBuffer: 在偏移 " + (i - pos) + " 处找到边界");
                    return i - pos;
                }
            }
            return -1;
        }

        public int getPosition() { return pos; }
        public void setPosition(int pos) { this.pos = Math.max(0, Math.min(pos, limit)); }
        public byte[] getBuffer() { return buffer; }
        public int getLimit() { return limit; }
        public InputStream getInputStream() { return input; }
    }
}