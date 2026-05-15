package com.omni.framework.codestats.sorter;

import com.omni.framework.codestats.core.FileInfo;
import java.util.Comparator;

public class ByLinesSorter implements ResultSorter {
    @Override
    public Comparator<FileInfo> getComparator() {
        return Comparator.comparingLong(FileInfo::getLines).reversed();
    }
}