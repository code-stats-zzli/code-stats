package com.codestats.framework.codestats.core;

import com.codestats.framework.codestats.language.Language;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StatisticsResult {
    private final List<FileInfo> fileInfos = new ArrayList<>();
    private long totalLines = 0;
    private long totalCodeLines = 0;
    private long totalCommentLines = 0;
    private long totalBlankLines = 0;
    private long durationMs = 0;
    private final Map<Language, LanguageSummary> languageSummary = new HashMap<>();

    public void addFileInfo(FileInfo info) {
        fileInfos.add(info);
        totalLines += info.getLines();
        totalCodeLines += info.getCodeLines();
        totalCommentLines += info.getCommentLines();
        totalBlankLines += info.getBlankLines();
        languageSummary.computeIfAbsent(info.getLanguage(), k -> new LanguageSummary())
                .add(info);
    }
    public void aggregate() { /* 可在此计算平均值等 */ }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long ms) { this.durationMs = ms; }
    public List<FileInfo> getFileInfos() { return fileInfos; }
    public long getTotalLines() { return totalLines; }
    public long getTotalCodeLines() { return totalCodeLines; }
    public long getTotalCommentLines() { return totalCommentLines; }
    public long getTotalBlankLines() { return totalBlankLines; }
    public Map<Language, LanguageSummary> getLanguageSummary() { return languageSummary; }

    public static class LanguageSummary {
        private long files = 0, lines = 0, code = 0, comments = 0, blanks = 0;
        public void add(FileInfo info) {
            files++; lines += info.getLines(); code += info.getCodeLines();
            comments += info.getCommentLines(); blanks += info.getBlankLines();
        }
        // getters
        public long getFiles() { return files; }
        public long getLines() { return lines; }
        public long getCode() { return code; }
        public long getComments() { return comments; }
        public long getBlanks() { return blanks; }
    }
}