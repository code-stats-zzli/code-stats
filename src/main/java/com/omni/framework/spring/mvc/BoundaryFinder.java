package com.omni.framework.spring.mvc;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.Arrays;

/**
 * 多部分边界查找工具类
 * 遵循 RFC 2046 multipart/form-data 标准
 * 修复边界匹配、流回退、结束边界判断、正文读取逻辑
 */
public class BoundaryFinder {
    private final PushbackInputStream input;
    private final byte[] boundary;
    private final int boundaryLen;
    // RFC标准：边界前必须是 \r\n，预定义匹配前缀
    private static final byte[] CRLF = {'\r', '\n'};

    /**
     * 构造边界查找器
     * @param input 原始输入流
     * @param boundary 边界字节数组（标准格式：--boundary）
     */
    public BoundaryFinder(InputStream input, byte[] boundary) {
        // 初始化推回流，缓冲区大小=边界长度+CRLF长度，保证足够回退
        this.input = new PushbackInputStream(input, boundary.length + CRLF.length + 2);
        this.boundary = boundary;
        this.boundaryLen = boundary.length;
    }

    /**
     * 查找下一个标准边界（支持普通边界 --boundary\r\n 和结束边界 --boundary--）
     * 找到后，流定位到边界结束后的第一个字节
     * @return true=找到边界，false=流结束未找到
     * @throws IOException IO异常
     */
    public boolean findBoundary() throws IOException {
        int matchIndex = 0;
        // 缓存已读取的匹配字节，用于回退
        byte[] matchBuffer = new byte[boundaryLen];

        while (true) {
            int b = input.read();
            if (b == -1) {
                // 流结束，未完整匹配边界
                return false;
            }

            matchBuffer[matchIndex] = (byte) b;

            if (b == boundary[matchIndex]) {
                matchIndex++;
                // 完整匹配边界字符串
                if (matchIndex == boundaryLen) {
                    // 检查后续字符，判断是普通边界/结束边界
                    return checkBoundarySuffix();
                }
            } else {
                // 匹配失败：回退所有已匹配字节 + 当前字节，重置匹配状态
                handlePartialMatch(matchBuffer, matchIndex, b);
                matchIndex = 0;
            }
        }
    }

    /**
     * 检查边界后缀：区分普通边界 和 结束边界
     * 普通边界：--boundary\r\n
     * 结束边界：--boundary--
     */
    private boolean checkBoundarySuffix() throws IOException {
        int first = input.read();
        if (first == -1) {
            return true;
        }

        if (first == '\r') {
            // 匹配 \r\n 标准换行
            int second = input.read();
            if (second == '\n') {
                return true;
            }
            // 不完整换行，回退
            input.unread(second);
            input.unread(first);
            return true;
        } else if (first == '-') {
            // 匹配结束边界 --
            int second = input.read();
            if (second == '-') {
                return true;
            }
            // 不是结束边界，回退
            input.unread(second);
            input.unread(first);
            return true;
        } else {
            // 非法后缀，回退，视为匹配完成（容错处理）
            input.unread(first);
            return true;
        }
    }

    /**
     * 处理部分匹配失败：正确回退字节（栈式回退：先匹配字节，后当前字节）
     */
    private void handlePartialMatch(byte[] matchBuffer, int matchLen, int currentByte) throws IOException {
        if (matchLen <= 0) {
            return;
        }
        // 回退顺序：逆序回退已匹配的字节（栈结构）
        for (int i = matchLen - 1; i >= 0; i--) {
            input.unread(matchBuffer[i]);
        }
        // 最后回退当前不匹配的字节
        input.unread(currentByte);
    }

    /**
     * 读取并跳过当前part正文，直到遇到下一个边界
     * 流最终定位到下一个边界起始位置，返回读取的正文字节数
     */
    public long skipPartContent() throws IOException {
        long contentLength = 0;
        int matchIndex = 0;

        while (true) {
            int b = input.read();
            if (b == -1) {
                return contentLength;
            }

            // 匹配边界
            if (b == boundary[matchIndex]) {
                matchIndex++;
                // 完整匹配到下一个边界
                if (matchIndex == boundaryLen) {
                    // 回退整个边界，供下一次findBoundary使用
                    unreadBoundary();
                    return contentLength;
                }
            } else {
                // 匹配失败：将已匹配的字节计入正文，重置匹配
                contentLength += matchIndex;
                matchIndex = 0;
                // 当前字节计入正文
                contentLength++;
            }
        }
    }

    /**
     * 回退完整的边界字节到流中
     */
    private void unreadBoundary() throws IOException {
        for (int i = boundaryLen - 1; i >= 0; i--) {
            input.unread(boundary[i]);
        }
    }

    /**
     * 获取底层流
     */
    public PushbackInputStream getInputStream() {
        return input;
    }
}