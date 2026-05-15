package com.codestats.framework.codestats.filter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


// 过滤器链
public class FilterChain implements FileFilter {
    private final List<FileFilter> filters = new ArrayList<>();
    public FilterChain add(FileFilter filter) { filters.add(filter); return this; }
    @Override
    public boolean accept(Path path) {
        return filters.stream().allMatch(f -> f.accept(path));
    }
}