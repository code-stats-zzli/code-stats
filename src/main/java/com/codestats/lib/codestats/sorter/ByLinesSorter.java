package com.codestats.lib.codestats.sorter;

import com.codestats.lib.codestats.core.FileInfo;
import java.util.Comparator;

public class ByLinesSorter implements ResultSorter {
    @Override
    public Comparator<FileInfo> getComparator() {
        return Comparator.comparingLong(FileInfo::getLines).reversed();
    }
}