package com.codestats.framework.codestats.sorter;

import com.codestats.framework.codestats.core.FileInfo;
import java.util.Comparator;

public class ByLanguageSorter implements ResultSorter {
    @Override
    public Comparator<FileInfo> getComparator() {
        return Comparator.comparing(f -> f.getLanguage().name());
    }
}