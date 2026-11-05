package com.omni.framework.scriptexec.executor.model;

import java.nio.file.Path;
import java.util.Map;

public class ScriptSource {
    private String content;
    private Path path;
    private String executionMode;
    private String language;
    private boolean compiled;
    private Path compiledArtifact;
    private Map<String, Object> extra;

    // getters/setters...
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Path getPath() { return path; }
    public void setPath(Path path) { this.path = path; }
    public String getExecutionMode() { return executionMode; }
    public void setExecutionMode(String executionMode) { this.executionMode = executionMode; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public boolean isCompiled() { return compiled; }
    public void setCompiled(boolean compiled) { this.compiled = compiled; }
    public Path getCompiledArtifact() { return compiledArtifact; }
    public void setCompiledArtifact(Path compiledArtifact) { this.compiledArtifact = compiledArtifact; }
    public Map<String, Object> getExtra() { return extra; }
    public void setExtra(Map<String, Object> extra) { this.extra = extra; }
}