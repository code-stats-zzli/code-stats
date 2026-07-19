package com.omni.framework.jstats.core.parser;

import com.omni.framework.jstats.core.model.GcStatistics;
import com.omni.framework.jstats.util.NumberUtils;
import java.util.StringTokenizer;

public class JstatGcParser {
    public GcStatistics parse(String output) {
        String[] lines = output.split("\n");
        if (lines.length < 2) throw new IllegalArgumentException("Invalid jstat -gc output");
        String dataLine = lines[1].trim();
        StringTokenizer tok = new StringTokenizer(dataLine);
        if (tok.countTokens() < 17) throw new IllegalArgumentException("Missing columns");

        long s0c = NumberUtils.parseLongSafely(tok.nextToken());
        long s1c = NumberUtils.parseLongSafely(tok.nextToken());
        long s0u = NumberUtils.parseLongSafely(tok.nextToken());
        long s1u = NumberUtils.parseLongSafely(tok.nextToken());
        long ec  = NumberUtils.parseLongSafely(tok.nextToken());
        long eu  = NumberUtils.parseLongSafely(tok.nextToken());
        long oc  = NumberUtils.parseLongSafely(tok.nextToken());
        long ou  = NumberUtils.parseLongSafely(tok.nextToken());
        long mc  = NumberUtils.parseLongSafely(tok.nextToken());
        long mu  = NumberUtils.parseLongSafely(tok.nextToken());
        long ccsc = NumberUtils.parseLongSafely(tok.nextToken());
        long ccsu = NumberUtils.parseLongSafely(tok.nextToken());
        int ygc   = (int) NumberUtils.parseLongSafely(tok.nextToken());
        double ygct = NumberUtils.parseDoubleSafely(tok.nextToken());
        int fgc    = (int) NumberUtils.parseLongSafely(tok.nextToken());
        double fgct = NumberUtils.parseDoubleSafely(tok.nextToken());
        double gct  = NumberUtils.parseDoubleSafely(tok.nextToken());

        return new GcStatistics(s0c, s1c, s0u, s1u, ec, eu, oc, ou, mc, mu,
                ygc, fgc, ygct, fgct, gct);
    }
}