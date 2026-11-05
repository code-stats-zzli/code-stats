package com.omni.framework.codestats.util;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.stream.Collectors;

public class GitIgnoreLoader {
    public static List<PathMatcher> load(Path rootDir) {
        Path gitignore = rootDir.resolve(".gitignore");
        if (!Files.exists(gitignore)) {
            List<PathMatcher> pathMatchers = new java.util.ArrayList<>();
            return pathMatchers;
        }
        try (java.util.stream.Stream<String> lines = Files.lines(gitignore)) {
            return lines.filter(l -> !l.trim().isEmpty() && !l.startsWith("#"))
                    .map(pattern -> {
                        if (pattern.startsWith("/")) pattern = pattern.substring(1);
                        if (pattern.endsWith("/")) pattern += "**";
                        return FileSystems.getDefault().getPathMatcher("glob:" + pattern);
                    })
                    .collect(Collectors.toList());
        } catch (IOException e) {
            List<PathMatcher> pathMatchers = new java.util.ArrayList<>();
            return pathMatchers;
        }
    }
}