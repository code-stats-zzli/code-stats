package com.codestats.framework.log.layout;

import com.codestats.framework.log.core.LogEvent;

public abstract class Layout {
    public abstract String format(LogEvent event);
}
