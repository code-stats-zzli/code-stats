package com.omni.framework.codestats.filter;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 扩展名过滤器。
 * 修复：支持最长后缀匹配（如 "tar.gz"），原实现只取最后一个 "." 后的部分。
 */
public class ExtensionFilter implements FileFilter {
    private final List<String> allowed;

    public ExtensionFilter(List<String> exts) {
        this.allowed = exts.stream()
                .filter(e -> e != null && !e.trim().isEmpty())
                .map(e -> e.trim().toLowerCase())
                .map(e -> e.startsWith(".") ? e : "." + e)
                .collect(Collectors.toList());
    }

    @Override
    public boolean accept(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        for (String suffix : allowed) {
            if (name.endsWith(suffix)) return true;
        }
        return false;
    }
}