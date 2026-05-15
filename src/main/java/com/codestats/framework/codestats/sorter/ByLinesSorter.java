package com.codestats.framework.codestats.sorter;

import com.codestats.framework.codestats.core.FileInfo;
import java.util.Comparator;

public class ByLinesSorter implements ResultSorter {
    @Override
    public Comparator<FileInfo> getComparator() {
        return Comparator.comparingLong(FileInfo::getLines).reversed();
    }
}