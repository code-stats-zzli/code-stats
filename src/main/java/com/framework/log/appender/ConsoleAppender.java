package com.framework.log.appender;

import com.framework.log.core.LogEvent;

public class ConsoleAppender extends Appender {
    @Override
    protected void append(LogEvent event) {
        String formatted = layout.format(event);
        System.out.print(formatted);
        System.out.flush();
    }

    @Override
    public void close() {}
}