package com.codestats.framework.codestats.config;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GitIgnoreLoader {
    public static List<PathMatcher> load(Path rootDir) {
        List<PathMatcher> matchers = new ArrayList<>();
        Path gitignore = rootDir.resolve(".gitignore");
        if (Files.exists(gitignore)) {
            try (java.util.stream.Stream<String> lines = Files.lines(gitignore)) {
                matchers = lines.filter(l -> !l.trim().isEmpty() && !l.startsWith("#"))
                        .map(pattern -> {
                            if (pattern.startsWith("/")) pattern = pattern.substring(1);
                            if (pattern.endsWith("/")) pattern += "**";
                            return FileSystems.getDefault().getPathMatcher("glob:" + pattern);
                        })
                        .collect(Collectors.toList());
            } catch (IOException ignored) {}
        }
        return matchers;
    }
}
