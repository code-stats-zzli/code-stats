package com.omni.framework.codestats.filter;
import com.omni.framework.codestats.config.GitIgnoreLoader;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
public class GitIgnoreFilter implements FileFilter {
    private final List<PathMatcher> matchers;
    public GitIgnoreFilter(Path rootDir) {
        this.matchers = GitIgnoreLoader.load(rootDir);
    }
    @Override
    public boolean accept(Path path) {
        if (matchers.isEmpty()) return true;
        Path absolute = path.toAbsolutePath();
        return matchers.stream().noneMatch(m -> m.matches(absolute));
    }
}