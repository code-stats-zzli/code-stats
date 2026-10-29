package com.omni.framework.codestats.config;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * .gitignore 解析器。
 * 修复：原实现 pattern.endsWith("/") → pattern += "**"，
 *      导致 "target/" 只匹配根级 target/。
 *      现改为：
 *        - 不含 "/" 的模式：匹配任意层级（** /pattern）
 *        - 以 "/" 开头的模式：仅匹配根级
 *        - 含 "/" 的模式：从根开始匹配
 *        - 以 "/" 结尾的模式：匹配该目录下所有内容
 */
public class GitIgnoreLoader {

    public static List<PathMatcher> load(Path rootDir) {
        List<PathMatcher> matchers = new ArrayList<>();
        Path gitignore = rootDir.resolve(".gitignore");
        if (!Files.exists(gitignore)) return matchers;
        try (Stream<String> lines = Files.lines(gitignore)) {
            lines.filter(l -> !l.trim().isEmpty() && !l.startsWith("#"))
                    .forEach(line -> {
                        try {
                            PathMatcher m = compile(line.trim());
                            if (m != null) matchers.add(m);
                        } catch (Exception ignored) {}
                    });
        } catch (IOException ignored) {}
        return matchers;
    }

    private static PathMatcher compile(String pattern) {
        if (pattern == null || pattern.isEmpty()) return null;
        String p = pattern;
        boolean dirOnly = p.endsWith("/");
        if (dirOnly) p = p.substring(0, p.length() - 1);
        boolean rootOnly = p.startsWith("/");
        if (rootOnly) p = p.substring(1);

        String glob;
        if (dirOnly) {
            if (rootOnly) glob = p + "/**";
            else if (p.contains("/")) glob = "**/" + p + "/**";
            else glob = "**/" + p + "/**";
        } else {
            if (rootOnly) glob = p;
            else if (p.contains("/")) glob = "**/" + p;
            else glob = "**/" + p;
        }
        return FileSystems.getDefault().getPathMatcher("glob:" + glob);
    }
}