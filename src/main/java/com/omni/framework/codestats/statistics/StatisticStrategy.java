package com.omni.framework.codestats.statistics;

import com.omni.framework.codestats.core.FileInfo;

import java.nio.file.Path;

public interface StatisticStrategy {
    void analyze(Path file, FileInfo info);
}
