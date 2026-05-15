package com.codestats.framework.tomcat.filter;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FilterConfig {
    private final String filterName;
    private final Map<String, String> initParams = new ConcurrentHashMap<>();

    public FilterConfig(String filterName) { this.filterName = filterName; }
    public String getFilterName() { return filterName; }
    public void addInitParameter(String name, String value) { initParams.put(name, value); }
    public String getInitParameter(String name) { return initParams.get(name); }
    public Enumeration<String> getInitParameterNames() { return Collections.enumeration(initParams.keySet()); }
}