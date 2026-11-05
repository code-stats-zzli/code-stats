package com.omni.framework.log.appender;

import com.omni.framework.log.core.LogEvent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class RollingFileAppender extends FileAppender {

    private final String baseFileName;
    private final long maxFileSize;
    private final int maxBackupIndex;
    private long currentSize = 0;

    public RollingFileAppender(String fileName, long maxFileSize, int maxBackupIndex) {
        super(fileName);
        // ★ 使用父类解析后的实际路径，保证回退后滚动逻辑仍一致
        this.baseFileName = getResolvedPath();
        this.maxFileSize = maxFileSize;
        // ★ 边界保护：至少保留 1 个备份，防止 for 循环不执行又去 move 当前文件
        this.maxBackupIndex = Math.max(1, maxBackupIndex);
        File f = new File(baseFileName);
        if (f.exists()) currentSize = f.length();
    }

    /**
     * ★ 加 synchronized：
     *   - 防止多线程同时触发 rollover，导致 writer 被关两次 / 丢事件
     *   - super.append 也是 synchronized，同线程可重入
     */
    @Override
    protected synchronized void append(LogEvent event) {
        String formatted = layout.format(event);
        long eventSize = formatted.getBytes(StandardCharsets.UTF_8).length;
        if (currentSize + eventSize > maxFileSize) {
            rollover();
        }
        super.append(event);
        currentSize += eventSize;
    }

    /**
     * ★ 核心修复：
     *   1) 先关闭 writer，释放 Windows 文件句柄
     *   2) 再 move（加 REPLACE_EXISTING）
     *   3) 最后在 finally 里重新打开 writer，保证后续日志不丢
     *
     *   之前直接 Files.move 一个被 PrintWriter 打开的文件，
     *   Windows 上必定抛 "另一个程序正在使用此文件"，
     *   且 currentSize 不清零导致每个事件都重试 → 反复刷错误。
     */
    private void rollover() {
        // 1. 关闭当前 writer，释放文件句柄
        closeWriterQuietly();

        boolean moved = false;
        try {
            // 2. 备份轮转 .N -> .N+1
            for (int i = maxBackupIndex - 1; i >= 1; i--) {
                Path src  = Paths.get(baseFileName + "." + i);
                Path dest = Paths.get(baseFileName + "." + (i + 1));
                if (Files.exists(src)) {
                    Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            // 3. 当前文件 -> .1
            Path current = Paths.get(baseFileName);
            Path backup  = Paths.get(baseFileName + ".1");
            if (Files.exists(current)) {
                Files.move(current, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            currentSize = 0;
            moved = true;
        } catch (IOException e) {
            System.err.println("[RollingFileAppender] 滚动日志失败: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            // ★ 关键：失败后清空计数，避免每个事件都重试 rollover → 日志刷屏
            currentSize = 0;
        } finally {
            // 4. 无论如何都要重新打开 writer，否则后续日志全部丢失
            try {
                reopenWriter();
            } catch (IOException e) {
                System.err.println("[RollingFileAppender] 重新打开日志文件失败: "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        if (!moved) {
            // 可在此处接入告警（邮件 / 日志监控），仅提示一次即可
            // 注意：不要在这里再 append 事件，避免递归触发 rollover
            System.err.println("[RollingFileAppender] 本次滚动未完成，当前日志继续写入原文件: "
                    + baseFileName);
        }
    }
}