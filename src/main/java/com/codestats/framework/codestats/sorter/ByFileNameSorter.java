package com.codestats.framework.codestats.sorter;


import com.codestats.framework.codestats.core.FileInfo;

import java.util.Comparator;

public class ByFileNameSorter implements ResultSorter {
    @Override
    public Comparator<FileInfo> getComparator() {
        return Comparator.comparing(f -> f.getPath().toString());
    }
}
