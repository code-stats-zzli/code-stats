package com.omni.framework.codestats.sorter;

import com.omni.framework.codestats.core.FileInfo;
import java.util.Comparator;

public class ByLanguageSorter implements ResultSorter {
    @Override
    public Comparator<FileInfo> getComparator() {
        return Comparator.comparing(f -> f.getLanguage().name());
    }
}