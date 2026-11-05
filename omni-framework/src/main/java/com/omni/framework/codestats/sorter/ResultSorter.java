package com.omni.framework.codestats.sorter;

import com.omni.framework.codestats.core.FileInfo;

import java.util.Comparator;

public interface ResultSorter {
    Comparator<FileInfo> getComparator();
}
