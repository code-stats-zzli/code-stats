package com.codestats.framework.codestats.statistics;

import com.codestats.framework.codestats.core.FileInfo;

import java.nio.file.Path;

public interface StatisticStrategy {
    void analyze(Path file, FileInfo info);
}
