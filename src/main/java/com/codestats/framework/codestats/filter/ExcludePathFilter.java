package com.codestats.framework.codestats.filter;
import java.nio.file.Path;
import java.util.List;
public class ExcludePathFilter implements FileFilter {
    private final List<String> keywords;
    public ExcludePathFilter(List<String> keywords) { this.keywords = keywords; }
    @Override
    public boolean accept(Path path) {
        String abs = path.toAbsolutePath().toString();
        return keywords.stream().noneMatch(abs::contains);
    }
}