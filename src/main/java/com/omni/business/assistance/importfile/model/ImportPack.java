package com.omni.business.assistance.importfile.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class ImportPack {
    private String name;
    private String version;
    private String description;
    private String targetRoot;
    private String conflict;
    private String encoding;
    private Map<String, Object> extra = new LinkedHashMap<>();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getTargetRoot() { return targetRoot; }
    public void setTargetRoot(String targetRoot) { this.targetRoot = targetRoot; }
    public String getConflict() { return conflict; }
    public void setConflict(String conflict) { this.conflict = conflict; }
    public String getEncoding() { return encoding; }
    public void setEncoding(String encoding) { this.encoding = encoding; }
    public Map<String, Object> getExtra() { return extra; }
    public void setExtra(Map<String, Object> extra) { this.extra = extra; }
}