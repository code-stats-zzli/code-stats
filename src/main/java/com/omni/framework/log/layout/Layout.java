package com.omni.framework.log.layout;

import com.omni.framework.log.core.LogEvent;

public abstract class Layout {
    public abstract String format(LogEvent event);
}
