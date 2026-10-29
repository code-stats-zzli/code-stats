package com.omni.framework.log.appender;

import com.omni.framework.log.core.LogEvent;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class FileAppender extends Appender {
    private PrintWriter writer;
    private final String fileName;

    public FileAppender(String fileName) {
        this.fileName = fileName;
        try {
            // ★ 修复1：自动创建父目录（如 D:\logs 不存在则创建）
            File file = new File(fileName).getAbsoluteFile();
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                boolean created = parentDir.mkdirs();
                if (created) {
                    System.out.println("[FileAppender] 已创建日志目录: " + parentDir.getAbsolutePath());
                } else {
                    System.err.println("[FileAppender] 警告：无法创建日志目录: " + parentDir.getAbsolutePath());
                }
            }
            this.writer = new PrintWriter(new FileWriter(file, true));
        } catch (IOException e) {
            // ★ 修复1：不抛异常也不打印完整堆栈，避免日志初始化失败导致整个应用无日志
            // 输出到 stderr 提示即可，writer = null 后续 append 静默跳过
            System.err.println("[FileAppender] 打开日志文件失败: " + fileName
                    + " - " + e.getClass().getSimpleName() + ": " + e.getMessage());
            this.writer = null;
        }
    }

    @Override
    protected void append(LogEvent event) {
        if (writer != null) {
            writer.print(layout.format(event));
            writer.flush();
        }
    }

    @Override
    public void close() {
        if (writer != null) writer.close();
    }
}