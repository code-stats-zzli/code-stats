package com.codestats.lib.codestats.statistics;

import com.codestats.lib.codestats.core.FileInfo;

import java.nio.file.Path;

public interface StatisticStrategy {
    void analyze(Path file, FileInfo info);
}
