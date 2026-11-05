package com.omni.business.assistance.autotest.core.engine;

import java.util.HashMap;
import java.util.Map;

public class Context {
    private final Map<String, Object> variables = new HashMap<>();
    private final Map<String, String> cookies = new HashMap<>();
    private String currentUrl;
    private boolean stopped = false;
    private Object lastActionResult;

    public Object getVariable(String name) { return variables.get(name); }
    public void setVariable(String name, Object value) { variables.put(name, value); }
    public Map<String, Object> getVariables() { return variables; }
    public Map<String, String> getCookies() { return cookies; }
    public String getCurrentUrl() { return currentUrl; }
    public void setCurrentUrl(String currentUrl) { this.currentUrl = currentUrl; }
    public boolean isStopped() { return stopped; }
    public void setStopped(boolean stopped) { this.stopped = stopped; }
    public Object getLastActionResult() { return lastActionResult; }
    public void setLastActionResult(Object lastActionResult) { this.lastActionResult = lastActionResult; }
}