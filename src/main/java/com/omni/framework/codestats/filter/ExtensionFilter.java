package com.omni.framework.codestats.filter;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class ExtensionFilter implements FileFilter {
    private final List<String> allowed;
    public ExtensionFilter(List<String> exts) { this.allowed = exts.stream().map(String::toLowerCase).collect(Collectors.toList()); }
    @Override
    public boolean accept(Path path) {
        String name = path.toString();
        int dot = name.lastIndexOf('.');
        if (dot == -1) return false;
        String ext = name.substring(dot + 1).toLowerCase();
        return allowed.contains(ext);
    }
}