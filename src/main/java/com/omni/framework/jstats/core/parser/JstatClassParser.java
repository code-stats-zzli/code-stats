package com.omni.framework.jstats.core.parser;

import com.omni.framework.jstats.core.model.ClassLoadingStats;
import com.omni.framework.jstats.util.NumberUtils;
import java.util.StringTokenizer;

public class JstatClassParser {
    public ClassLoadingStats parse(String output) {
        String[] lines = output.split("\n");
        if (lines.length < 2) {
            throw new IllegalArgumentException("Invalid jstat -class output");
        }
        String dataLine = lines[1].trim();
        StringTokenizer tok = new StringTokenizer(dataLine);
        if (tok.countTokens() < 2) {
            throw new IllegalArgumentException("Missing columns");
        }
        long loaded = NumberUtils.parseLongSafely(tok.nextToken());
        long unloaded = NumberUtils.parseLongSafely(tok.nextToken());
        return new ClassLoadingStats(loaded, unloaded, loaded + unloaded);
    }
}