package com.codestats.lib.codestats.filter;

import java.nio.file.Path;

public interface FileFilter {
    boolean accept(Path path);
}
