package com.framework.log.appender;

import com.framework.log.Level;
import com.framework.log.core.LogEvent;
import com.framework.log.filter.Filter;
import com.framework.log.layout.Layout;
import java.util.ArrayList;
import java.util.List;

public abstract class Appender {
    protected String name;
    protected Layout layout;
    protected final List<Filter> filters = new ArrayList<>();
    protected Level threshold = Level.TRACE;

    public void setName(String name) { this.name = name; }
    public void setLayout(Layout layout) { this.layout = layout; }
    public void addFilter(Filter filter) { filters.add(filter); }
    public void setThreshold(Level level) { this.threshold = level; }

    public void doAppend(LogEvent event) {
        if (!event.getLevel().isGreaterOrEqual(threshold)) return;
        for (Filter filter : filters) {
            Filter.Result r = filter.decide(event);
            if (r == Filter.Result.DENY) return;
            if (r == Filter.Result.ACCEPT) break;
        }
        append(event);
    }

    protected abstract void append(LogEvent event);
    public abstract void close();
}