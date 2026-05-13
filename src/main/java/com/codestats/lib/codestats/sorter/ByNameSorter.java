package com.codestats.lib.codestats.sorter;

import com.codestats.lib.codestats.core.FileInfo;
import java.util.Comparator;

public class ByNameSorter implements ResultSorter {
    @Override
    public Comparator<FileInfo> getComparator() {
        return Comparator.comparing(f -> f.getPath().toString());
    }
}