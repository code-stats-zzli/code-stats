package com.codestats.framework.codestats.sorter;

import com.codestats.framework.codestats.core.FileInfo;

import java.util.Comparator;

public interface ResultSorter {
    Comparator<FileInfo> getComparator();
}
