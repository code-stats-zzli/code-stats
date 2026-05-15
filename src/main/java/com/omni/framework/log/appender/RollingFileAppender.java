package com.omni.framework.log.appender;

import com.omni.framework.log.core.LogEvent;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class RollingFileAppender extends FileAppender {
    private final String baseFileName;
    private final long maxFileSize;
    private final int maxBackupIndex;
    private long currentSize = 0;

    public RollingFileAppender(String fileName, long maxFileSize, int maxBackupIndex) {
        super(fileName);
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
        super.append(event);
        currentSize += eventSize;
    }

    private void rollover() {
        try {
            for (int i = maxBackupIndex - 1; i >= 1; i--) {
                Path src = Paths.get(baseFileName + "." + i);
                Path dest = Paths.get(baseFileName + "." + (i + 1));
                if (Files.exists(src)) Files.move(src, dest);
            }
            Path current = Paths.get(baseFileName);
            Path backup = Paths.get(baseFileName + ".1");
            if (Files.exists(current)) Files.move(current, backup);
            currentSize = 0;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}