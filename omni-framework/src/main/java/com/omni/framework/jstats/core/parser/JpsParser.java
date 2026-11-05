package com.omni.framework.jstats.core.parser;

import com.omni.framework.jstats.core.model.JavaProcess;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class JpsParser {
    private static final Pattern PATTERN = Pattern.compile("^(\\d+)\\s+(.+)$");
    public List<JavaProcess> parse(String output) {
        List<JavaProcess> list = new ArrayList<>();
        for (String line : output.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            var m = PATTERN.matcher(line);
            if (m.find()) list.add(new JavaProcess(m.group(1), m.group(2)));
        }
        return list;
    }
}