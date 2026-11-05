package com.omni.framework.jstats.core.parser;

import com.omni.framework.jstats.core.model.HeapHistogram;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class JmapHistoParser {
    private static final Pattern LINE = Pattern.compile("^\\s*(\\d+):\\s+(\\d+)\\s+(\\d+)\\s+(.+)$");
    public HeapHistogram parse(String output) {
        List<HeapHistogram.ClassEntry> entries = new ArrayList<>();
        boolean inData = false;
        for (String line : output.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("num") && line.contains("instances")) { inData = true; continue; }
            if (inData) {
                var m = LINE.matcher(line);
                if (m.matches()) {
                    int idx = Integer.parseInt(m.group(1));
                    int inst = Integer.parseInt(m.group(2));
                    long bytes = Long.parseLong(m.group(3));
                    String cls = m.group(4);
                    entries.add(new HeapHistogram.ClassEntry(idx, inst, bytes, cls));
                }
            }
        }
        return new HeapHistogram(entries);
    }
}