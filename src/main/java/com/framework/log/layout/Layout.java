package com.framework.log.layout;

import com.framework.log.core.LogEvent;

public abstract class Layout {
    public abstract String format(LogEvent event);
}
