package com.codestats.framework.log.filter;

import com.codestats.framework.log.Level;
import com.codestats.framework.log.core.LogEvent;

public class LevelRangeFilter extends Filter {
    private Level minLevel = Level.TRACE;
    private Level maxLevel = Level.ERROR;

    public void setMinLevel(String level) { this.minLevel = Level.toLevel(level); }
    public void setMaxLevel(String level) { this.maxLevel = Level.toLevel(level); }

    @Override
    public Result decide(LogEvent event) {
        Level level = event.getLevel();
        if (level.intValue() >= minLevel.intValue() && level.intValue() <= maxLevel.intValue()) {
            return Result.ACCEPT;
        }
        return Result.DENY;
    }
}