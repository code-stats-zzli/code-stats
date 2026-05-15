package com.codestats.framework.codestats.core;

import com.codestats.framework.codestats.language.Language;
import java.nio.file.Path;

public class FileInfo {
    private final Path path;
    private Language language = Language.UNKNOWN;
    private long lines = 0;
    private long codeLines = 0;
    private long commentLines = 0;
    private long blankLines = 0;
    private int complexity = 0;  // 简单复杂度估算

    public FileInfo(Path path) { this.path = path; }

    public Path getPath() { return path; }
    public Language getLanguage() { return language; }
    public void setLanguage(Language language) { this.language = language; }
    public long getLines() { return lines; }
    public void setLines(long lines) { this.lines = lines; }
    public long getCodeLines() { return codeLines; }
    public void setCodeLines(long codeLines) { this.codeLines = codeLines; }
    public long getCommentLines() { return commentLines; }
    public void setCommentLines(long commentLines) { this.commentLines = commentLines; }
    public long getBlankLines() { return blankLines; }
    public void setBlankLines(long blankLines) { this.blankLines = blankLines; }
    public int getComplexity() { return complexity; }
    public void setComplexity(int complexity) { this.complexity = complexity; }
}