package com.omni.framework.spring.mvc;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * 高性能边界查找器 - 批量读取模式，无单字节IO
 * 仅在缓冲区内部匹配边界，大幅提升解析效率
 */
public class BoundaryFinder {
    private final InputStream input;
    private final byte[] boundary;
    private final int boundaryLen;

    // 缓冲区配置：16KB 批量读取
    private static final int BUFFER_SIZE = 16384;
    private final byte[] buffer = new byte[BUFFER_SIZE];
    private int pos = 0;
    private int limit = 0;

    public BoundaryFinder(InputStream input, byte[] boundary) {
        this.input = input;
        this.boundary = boundary;
        this.boundaryLen = boundary.length;
    }

    /**
     * 批量查找边界，找到返回true，流定位到边界结束位置
     */
    public boolean findBoundary() throws IOException {
        if (boundaryLen == 0) return false;

        int match = 0;
        while (true) {
            // 缓冲区空，批量填充数据
            if (pos >= limit) {
                int read = input.read(buffer);
                if (read == -1) return false;
                pos = 0;
                limit = read;
            }

            // 批量匹配边界（核心：无单字节读取）
            while (pos < limit && match < boundaryLen) {
                if (buffer[pos] == boundary[match]) {
                    match++;
                } else {
                    // 匹配失败，重置匹配计数
                    match = Math.max(0, match - 1);
                    if (buffer[pos] == boundary[match]) {
                        match++;
                    }
                }
                pos++;
            }

            // 完整匹配到边界
            if (match == boundaryLen) {
                return handleBoundarySuffix();
            }
        }
    }

    /**
     * 处理边界后缀：\r\n 或 --
     */
    private boolean handleBoundarySuffix() throws IOException {
        byte[] peek = new byte[4];
        int peekLen = peekBytes(peek, 0, 4);

        // 结束边界 --boundary--
        if (peekLen >= 2 && peek[0] == '-' && peek[1] == '-') {
            pos += 2;
            // 跳过换行符
            if (peekLen >=4 && peek[2] == '\r' && peek[3] == '\n') pos +=2;
            else if (peekLen >=3 && peek[2] == '\n') pos +=1;
            return true;
        }

        // 标准边界 \r\n
        if (peekLen >=2 && peek[0] == '\r' && peek[1] == '\n') {
            pos +=2;
            return true;
        }
        // 兼容 \n
        if (peekLen >=1 && peek[0] == '\n') {
            pos +=1;
            return true;
        }
        return true;
    }

    /**
     * 批量预读数据，不移动指针
     */
    private int peekBytes(byte[] dest, int off, int len) {
        int available = Math.min(len, limit - pos);
        System.arraycopy(buffer, pos, dest, off, available);
        return available;
    }

    /**
     * 判断是否是结束边界
     */
    public boolean isClosingBoundary() throws IOException {
        byte[] peek = new byte[2];
        int len = peekBytes(peek, 0, 2);
        return len == 2 && peek[0] == '-' && peek[1] == '-';
    }

    // ==================== 供外部读取内容使用 ====================
    public int readBuffer(byte[] dest) throws IOException {
        if (pos >= limit) {
            int read = input.read(buffer);
            if (read == -1) return -1;
            pos = 0;
            limit = read;
        }
        int copy = Math.min(dest.length, limit - pos);
        System.arraycopy(buffer, pos, dest, 0, copy);
        pos += copy;
        return copy;
    }

    /**
     * 在当前缓冲区中查找边界，返回边界起始偏移量
     */
    public int findBoundaryInBuffer() {
        for (int i=pos; i <= limit - boundaryLen; i++) {
            if (Arrays.equals(buffer, i, i+boundaryLen, boundary, 0, boundaryLen)) {
                return i - pos;
            }
        }
        return -1;
    }

    public int getBufferRemaining() {
        return limit - pos;
    }

    public byte[] getBuffer() {
        return buffer;
    }

    public int getPosition() {
        return pos;
    }

    public void setPosition(int pos) {
        this.pos = pos;
    }
}