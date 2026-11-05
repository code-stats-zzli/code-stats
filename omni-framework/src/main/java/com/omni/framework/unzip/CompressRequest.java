package com.omni.framework.unzip;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CompressRequest {
    private ArchiveType targetType;
    private List<CompressEntry> entries = new ArrayList<>();
    private Path outputFile;
    private String encoding = "UTF-8";

    public ArchiveType getTargetType() { return targetType; }
    public void setTargetType(ArchiveType targetType) { this.targetType = targetType; }
    public List<CompressEntry> getEntries() { return entries; }
    public void setEntries(List<CompressEntry> entries) { this.entries = entries; }
    public Path getOutputFile() { return outputFile; }
    public void setOutputFile(Path outputFile) { this.outputFile = outputFile; }
    public String getEncoding() { return encoding; }
    public void setEncoding(String encoding) { this.encoding = encoding; }
}