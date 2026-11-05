package com.omni.framework.spring.mvc;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class MultipartResolver implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(MultipartResolver.class);

    private static final int CONTENT_BUFFER = 8192;
    private final BufferedInputStream inputStream;
    private final byte[] boundary;
    private final Map<String, String> textFields = new HashMap<>();
    private final Map<String, FileItem> fileFields = new HashMap<>();
    private boolean closed = false;

    public MultipartResolver(String contentType, InputStream inputStream) throws IOException {
        this.inputStream = new BufferedInputStream(inputStream, CONTENT_BUFFER * 2);
        String boundaryStr = extractBoundary(contentType);
        if (boundaryStr == null || boundaryStr.isBlank()) {
            throw new IllegalArgumentException("缺少 boundary");
        }
        this.boundary = ("--" + boundaryStr).getBytes(StandardCharsets.US_ASCII);
        BoundaryFinder finder = new BoundaryFinder(this.inputStream, this.boundary);
        parse(finder);

        log.info("Multipart 解析完成: 文本字段={}, 文件字段={}, 文件详情={}",
                textFields.keySet(),
                fileFields.keySet(),
                fileFields.entrySet().stream()
                        .map(e -> e.getKey() + "(" + e.getValue().getFileName()
                                + ", " + e.getValue().getSize() + "B)")
                        .collect(Collectors.toList()));
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
        if (!finder.findBoundary()) throw new IOException("找不到起始边界");
        int partCount = 0;
        while (true) {
            if (finder.isClosingBoundary()) break;
            partCount++;
            Map<String, String> headers = readPartHeaders(finder);
            if (headers == null || headers.isEmpty()) break;

            String dis = headers.get("content-disposition");
            if (dis == null) {
                if (!finder.findBoundary()) break;
                continue;
            }

            String name = extractHeaderValue(dis, "name");
            if (name == null || name.isBlank()) {
                if (!finder.findBoundary()) break;
                continue;
            }

            String filename = extractHeaderValue(dis, "filename");
            if (filename != null) {
                Path path = readFileContent(finder);
                long size = Files.size(path);
                fileFields.put(name, new FileItem(name, filename,
                        headers.getOrDefault("content-type", "application/octet-stream"),
                        size, path));
                log.debug("Multipart 文件字段: name={}, filename={}, size={}B", name, filename, size);
            } else {
                String text = readTextContent(finder);
                textFields.put(name, text);
                log.debug("Multipart 文本字段: name={}, length={}", name, text.length());
            }

            boolean found = finder.findBoundary();
            if (!found) break;
            if (finder.isClosingBoundary()) break;
        }
        log.debug("Multipart 解析结束，共 {} 个 part", partCount);
    }

    /**
     * 读 part 头，用 ISO-8859-1 解码保留原始字节（后续按需按 UTF-8 还原）。
     * key 转小写，value 保持原样。
     */
    private Map<String, String> readPartHeaders(BoundaryFinder finder) throws IOException {
        Map<String, String> headers = new HashMap<>();
        while (true) {
            String line = readLine(finder);
            if (line == null) return headers.isEmpty() ? null : headers;
            if (line.isEmpty()) break;
            int colon = line.indexOf(':');
            if (colon > 0) {
                String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                String value = line.substring(colon + 1).trim();
                headers.put(key, value);
            }
        }
        return headers;
    }

    /**
     * 用 ISO-8859-1 读行：保证原始字节 1:1 映射到 char，避免中文被替换为 ?
     */
    private String readLine(BoundaryFinder finder) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(256);
        byte[] buf = new byte[CONTENT_BUFFER];
        int consecutiveZeroReads = 0;
        while (true) {
            int r = finder.readBuffer(buf);
            if (r == -1) {
                return baos.size() == 0 ? null : baos.toString(StandardCharsets.ISO_8859_1);
            }
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
                    String line = baos.toString(StandardCharsets.ISO_8859_1);
                    return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
                }
            }
            baos.write(buf, 0, r);
        }
    }

    /**
     * ★ 修复点：读取文件二进制内容。
     *
     * <p>标准 multipart 中，part 内容后紧跟 {@code \r\n--boundary}。
     * {@code findBoundaryInBuffer()} 返回的是 {@code --boundary} 起始位置，
     * 所以写入的 {@code offset} 字节里<b>包含了末尾的 {@code \r\n}</b>，
     * 这是分隔符，不属于文件内容。</p>
     *
     * <p>原实现直接写入 {@code offset} 字节，导致每个上传的二进制文件
     * （JAR/ZIP/图片等）末尾都多 2 字节 {@code \r\n}，
     * 文件 SHA256 与原文件不同，表现为"文件损坏"。</p>
     *
     * <p>本修复在找到 boundary 后，回退 2 字节（兼容 {@code \r\n} 和
     * 部分实现的 {@code \n}），与 {@link #readTextContent} 的截断逻辑保持一致。</p>
     */
    private Path readFileContent(BoundaryFinder finder) throws IOException {
        Path tmp = Files.createTempFile("upload_", ".tmp");
        try (BufferedOutputStream out = new BufferedOutputStream(Files.newOutputStream(tmp))) {
            while (true) {
                int offset = finder.findBoundaryInBuffer();
                if (offset >= 0) {
                    int posStart = finder.getPosition();
                    // ★ 排除 boundary 前的 \r\n（multipart 分隔符不属于文件内容）
                    int writeLen = offset;
                    if (writeLen >= 2
                            && finder.buffer[posStart + writeLen - 2] == '\r'
                            && finder.buffer[posStart + writeLen - 1] == '\n') {
                        writeLen -= 2;
                    } else if (writeLen >= 1 && finder.buffer[posStart + writeLen - 1] == '\n') {
                        writeLen -= 1;
                    }
                    if (writeLen > 0) {
                        out.write(finder.buffer, posStart, writeLen);
                    }
                    finder.setPosition(posStart + offset);
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

    /**
     * 从 content-disposition 中提取参数（支持 RFC 5987 编码 filename*=UTF-8''xxx）。
     * 注意：绝不 lowercase 整个 header（会破坏 UTF-8 字节），只 lowercase key 部分。
     */
    private String extractHeaderValue(String header, String paramName) {
        if (header == null) return null;

        List<String> parts = splitParams(header);
        String lowerParam = paramName.toLowerCase(Locale.ROOT);

        // 1) 优先 RFC 5987: filename*=UTF-8''%E4%B8%AD.png
        for (String part : parts) {
            String trimmed = part.trim();
            int eq = trimmed.indexOf('=');
            if (eq <= 0) continue;
            String key = trimmed.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            if (!key.equals(lowerParam + "*")) continue;
            String val = trimmed.substring(eq + 1).trim();
            return parseRfc5987Value(val);
        }

        // 2) 标准格式 filename="xxx"，用 UTF-8 还原原始字节
        for (String part : parts) {
            String trimmed = part.trim();
            int eq = trimmed.indexOf('=');
            if (eq <= 0) continue;
            String key = trimmed.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            if (!key.equals(lowerParam)) continue;
            String val = trimmed.substring(eq + 1).trim();
            if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                val = val.substring(1, val.length() - 1);
            }
            return decodeIfUtf8(val);
        }
        return null;
    }

    /**
     * 按分号拆分，但要忽略引号内的分号
     */
    private List<String> splitParams(String header) {
        List<String> result = new ArrayList<>();
        boolean inQuote = false;
        int start = 0;
        for (int i = 0; i < header.length(); i++) {
            char c = header.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
            } else if (c == ';' && !inQuote) {
                result.add(header.substring(start, i));
                start = i + 1;
            }
        }
        result.add(header.substring(start));
        return result;
    }

    /**
     * 解析 RFC 5987: UTF-8''%E4%B8%AD.png
     */
    private String parseRfc5987Value(String val) {
        if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
            val = val.substring(1, val.length() - 1);
        }
        int quoteIdx = val.indexOf("''");
        if (quoteIdx >= 0) {
            String encoded = val.substring(quoteIdx + 2);
            try {
                return URLDecoder.decode(encoded, "UTF-8");
            } catch (Exception e) {
                log.warn("RFC5987 解码失败: {}", encoded, e);
                return encoded;
            }
        }
        return val;
    }

    /**
     * 用 ISO-8859-1 读到的字符串，如果原始字节是合法 UTF-8 则还原，否则原样返回
     */
    private String decodeIfUtf8(String s) {
        if (s == null || s.isEmpty()) return s;
        try {
            byte[] raw = s.getBytes(StandardCharsets.ISO_8859_1);
            String utf8 = new String(raw, StandardCharsets.UTF_8);
            if (Arrays.equals(utf8.getBytes(StandardCharsets.UTF_8), raw)) {
                return utf8;
            }
        } catch (Exception ignored) {}
        return s;
    }

    public String getParameter(String name) { return textFields.get(name); }
    public FileItem getFileItem(String name) { return fileFields.get(name); }
    public Set<String> getParameterNames() { return textFields.keySet(); }
    public Set<String> getFileNames() { return fileFields.keySet(); }

    @Override
    public void close() {
        if (closed) return;
        fileFields.values().forEach(i -> { try { i.close(); } catch (Exception ignored) {} });
        closed = true;
    }

    // ==================== BoundaryFinder（保持不变） ====================
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
                            else if (peekLen >= 3 && peek[2] == '\n') pos += 1;
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