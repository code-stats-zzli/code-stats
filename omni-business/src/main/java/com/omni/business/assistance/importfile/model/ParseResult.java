package com.omni.business.assistance.importfile.model;

import java.util.ArrayList;
import java.util.List;

public class ParseResult {
    private String format;
    private String formatDisplayName;
    private ImportPack pack;
    private List<ImportFile> files = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public String getFormatDisplayName() { return formatDisplayName; }
    public void setFormatDisplayName(String n) { this.formatDisplayName = n; }
    public ImportPack getPack() { return pack; }
    public void setPack(ImportPack pack) { this.pack = pack; }
    public List<ImportFile> getFiles() { return files; }
    public void setFiles(List<ImportFile> files) { this.files = files; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
}