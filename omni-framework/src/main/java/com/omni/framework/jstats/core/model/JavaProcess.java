package com.omni.framework.jstats.core.model;

public class JavaProcess {
    private final String pid;
    private final String mainClass;

    public JavaProcess(String pid, String mainClass) {
        this.pid = pid;
        this.mainClass = mainClass;
    }
    public String getPid() { return pid; }
    public String getMainClass() { return mainClass; }
}