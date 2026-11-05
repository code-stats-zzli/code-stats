package com.omni.framework.jstats.core.parser;

import com.omni.framework.jstats.core.model.CpuMemoryStats;
import java.util.regex.Pattern;

public class PsParser {
    private static final Pattern PS_LINE = Pattern.compile("^\\s*(\\d+)\\s+([\\d.]+)\\s+([\\d.]+)\\s+(\\d+)\\s+.*");

    public CpuMemoryStats parse(String output) {
        String[] lines = output.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.toLowerCase().contains("pid")) continue;
            var m = PS_LINE.matcher(line);
            if (m.matches()) {
                double cpu = Double.parseDouble(m.group(2));
                long rss = Long.parseLong(m.group(4));
                return new CpuMemoryStats(cpu, rss);
            }
        }
        throw new IllegalArgumentException("无法解析 ps 输出");
    }
}