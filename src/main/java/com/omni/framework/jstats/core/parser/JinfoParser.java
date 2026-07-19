package com.omni.framework.jstats.core.parser;

import com.omni.framework.jstats.core.model.JvmOption;
import java.util.ArrayList;
import java.util.List;

public class JinfoParser {
    public List<JvmOption> parse(String output) {
        List<JvmOption> list = new ArrayList<>();
        for (String line : output.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            int eq = line.indexOf('=');
            if (eq > 0) {
                String name = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                if (name.startsWith("-XX:")) name = name.substring(4);
                list.add(new JvmOption(name, value));
            }
        }
        return list;
    }
}