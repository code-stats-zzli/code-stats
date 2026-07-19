package com.omni.framework.jstats.core.parser;

import com.omni.framework.jstats.core.model.ThreadInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class JstackParser {
    private static final Pattern HEADER = Pattern.compile("^\"(.+)\"\\s+#(\\d+).*?state:\\s+(\\w+)");
    public List<ThreadInfo> parse(String output) {
        List<ThreadInfo> list = new ArrayList<>();
        String[] lines = output.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            var m = HEADER.matcher(line);
            if (m.find()) {
                String name = m.group(1);
                long id = Long.parseLong(m.group(2));
                String state = m.group(3);
                List<String> stack = new ArrayList<>();
                i++;
                while (i < lines.length) {
                    String sl = lines[i].trim();
                    if (sl.isEmpty() || HEADER.matcher(sl).find()) break;
                    stack.add(sl);
                    i++;
                }
                list.add(new ThreadInfo(name, id, state, stack));
            }
        }
        return list;
    }
}