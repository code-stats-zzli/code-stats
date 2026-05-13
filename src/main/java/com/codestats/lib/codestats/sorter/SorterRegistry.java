package com.codestats.lib.codestats.sorter;

import com.codestats.lib.codestats.core.FileInfo;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class SorterRegistry {
    private static final Map<String, Comparator<FileInfo>> SORTERS = new HashMap<>();
    static {
        SORTERS.put("name", Comparator.comparing(f -> f.getPath().toString()));
        SORTERS.put("lines", Comparator.comparingLong(FileInfo::getLines).reversed());
        SORTERS.put("language", Comparator.comparing(f -> f.getLanguage().name()));
        SORTERS.put("complexity", Comparator.comparingInt(FileInfo::getComplexity).reversed());
    }

    public static Comparator<FileInfo> getComparator(String name) {
        return SORTERS.getOrDefault(name, SORTERS.get("lines"));
    }

    public static void register(String name, Comparator<FileInfo> comparator) {
        SORTERS.put(name, comparator);
    }
}