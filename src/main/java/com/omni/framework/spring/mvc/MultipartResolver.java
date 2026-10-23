package com.omni.framework.spring.mvc;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

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
        this.inputStream = new BufferedInputStream(inputStream, CONTENT_BUFFER * 2);
        String boundaryStr = extractBoundary(contentType);
        if (boundaryStr == null || boundaryStr.isBlank()) {
            throw new IllegalArgumentException("缺少 boundary");
        }
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
        if (!finder.findBoundary()) {
            throw new IOException("找不到起始边界");
        }
        int partCount = 0;
        while (true) {
            if (finder.isClosingBoundary()) break;
            partCount++;
            Map<String, String> headers = readPartHeaders(finder);
            if (headers == null || headers.isEmpty()) break;

            String dis = headers.get("content-disposition");
            if (dis == null) {
                // ★ 缺陷：continue 但没有 finder.findBoundary() 推进到下一个边界
                continue;
            }

            String name = extractHeaderValue(dis, "name");
            if (name == null || name.isBlank()) {
                // ★ 缺陷：同上
                continue;
            }

            String filename = extractHeaderValue(dis, "filename");
            if (filename != null) {
                Path path = readFileContent(finder);
                fileFields.put(name, new FileItem(name, filename,
                        headers.getOrDefault("content-type", "application/octet-stream"),
                        Files.size(path), path));
            } else {
                String text = readTextContent(finder);
                textFields.put(name, text);
            }

            boolean found = finder.findBoundary();
            if (!found) break;
            if (finder.isClosingBoundary()) break;
        }
    }

    private Map<String, String> readPartHeaders(BoundaryFinder finder) throws IOException {
        Map<String, String> headers = new HashMap<>();
        while (true) {
            String line = readLine(finder);
            if (line == null) return headers.isEmpty() ? null : headers;
            if (line.isEmpty()) break;
            int colon = line.indexOf(':');
            if (colon > 0) {
                String key = line.substring(0, colon).trim().toLowerCase();
                String value = line.substring(colon + 1).trim();
                headers.put(key, value);
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
            if (r == -1) return baos.size() == 0 ? null : baos.toString(StandardCharsets.US_ASCII);
            if (r == 0) {
                consecutiveZeroReads++;
                if (consecutiveZeroReads > 10) return null;
                Thread.yield();
                continue;
            }
            consecutiveZeroReads = 0;
            for (int i = 0; i < r; i++) {
                if (buf[i] == '\n') {
                    baos.write(buf, 0, i);
                    int remain = r - i - 1;
                    if (remain > 0) finder.unread(buf, i + 1, remain);
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
        if (text.endsWith("\r\n")) text = text.substring(0, text.length() - 2);
        else if (text.endsWith("\n")) text = text.substring(0, text.length() - 1);
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
    }

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
                            lastBoundaryFound = true;
                        } else if (peekLen >= 2 && peek[0] == '\r' && peek[1] == '\n') {
                            pos += 2;
                            lastBoundaryFound = false;
                        } else if (peekLen >= 1 && peek[0] == '\n') {
                            pos += 1;
                            lastBoundaryFound = false;
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

        public boolean isClosingBoundary() { return lastBoundaryFound; }

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

        public void unread(byte[] data, int off, int len) {
            if (len > 0) {
                System.arraycopy(data, off, buffer, 0, len);
                pos = 0;
                limit = len;
            }
        }

        public int findBoundaryInBuffer() {
            for (int i = pos; i <= limit - boundaryLen; i++) {
                boolean ok = true;
                for (int j = 0; j < boundaryLen; j++) {
                    if (buffer[i + j] != boundary[j]) { ok = false; break; }
                }
                if (ok) return i - pos;
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