package com.omni.framework.log.appender;

import com.omni.framework.log.core.LogEvent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class RollingFileAppender extends FileAppender {
    private final String baseFileName;
    private final long maxFileSize;
    private final int maxBackupIndex;
    private long currentSize = 0;

    public RollingFileAppender(String fileName, long maxFileSize, int maxBackupIndex) {
        super(fileName);   // ★ 父类构造已保证父目录存在
        this.baseFileName = fileName;
        this.maxFileSize = maxFileSize;
        this.maxBackupIndex = maxBackupIndex;
        File f = new File(fileName);
        if (f.exists()) currentSize = f.length();
    }

    @Override
    protected void append(LogEvent event) {
        String formatted = layout.format(event);
        long eventSize = formatted.getBytes().length;
        if (currentSize + eventSize > maxFileSize) {
            rollover();
        }
        super.append(event);   // ★ 父类的 append 会判断 writer 是否为 null
        currentSize += eventSize;
    }

    private void rollover() {
        try {
            // 先改名 .1 → .2, .2 → .3 ... 从后往前
            for (int i = maxBackupIndex - 1; i >= 1; i--) {
                Path src = Paths.get(baseFileName + "." + i);
                Path dest = Paths.get(baseFileName + "." + (i + 1));
                if (Files.exists(src)) Files.move(src, dest);
            }
            // 当前文件 → .1
            Path current = Paths.get(baseFileName);
            Path backup = Paths.get(baseFileName + ".1");
            if (Files.exists(current)) Files.move(current, backup);
            currentSize = 0;
        } catch (IOException e) {
            // ★ 修复：不 printStackTrace 打印全部堆栈，仅简要提示
            System.err.println("[RollingFileAppender] 滚动日志失败: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}