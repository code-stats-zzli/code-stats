package com.codestats.lib.codestats.sorter;

import com.codestats.lib.codestats.core.FileInfo;

import java.util.Comparator;

public interface ResultSorter {
    Comparator<FileInfo> getComparator();
}
