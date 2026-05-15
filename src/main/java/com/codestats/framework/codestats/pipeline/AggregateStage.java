package com.codestats.framework.codestats.pipeline;

import com.codestats.framework.codestats.core.FileInfo;
import com.codestats.framework.codestats.core.StatisticsResult;
import com.codestats.framework.codestats.sorter.SorterRegistry;
import java.util.Comparator;
import java.util.List;

public class AggregateStage implements Stage<List<FileInfo>, StatisticsResult> {
    private final Comparator<FileInfo> comparator;
    public AggregateStage(String sortBy) {
        this.comparator = SorterRegistry.getComparator(sortBy);
    }

    @Override
    public StatisticsResult process(List<FileInfo> infos) {
        infos.sort(comparator);
        StatisticsResult result = new StatisticsResult();
        for (FileInfo info : infos) {
            result.addFileInfo(info);
        }
        return result;
    }
}