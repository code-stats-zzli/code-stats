package com.omni.framework.log.appender;

import com.omni.framework.log.core.LogEvent;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class FileAppender extends Appender {
    private PrintWriter writer;
    private final String fileName;

    public FileAppender(String fileName) {
        this.fileName = fileName;
        try {
            this.writer = new PrintWriter(new FileWriter(fileName, true));
        } catch (IOException e) {
            e.printStackTrace();
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