package com.codestats.framework.log.filter;


import com.codestats.framework.log.core.LogEvent;

public abstract class Filter {
    public enum Result { ACCEPT, DENY, NEUTRAL }

    public abstract Result decide(LogEvent event);
}
