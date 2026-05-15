package com.codestats.framework.codestats.output;

import com.codestats.framework.codestats.core.StatisticsResult;
import java.io.IOException;

public abstract class OutputFormatter {
    protected final OutputWriter writer;
    public OutputFormatter(OutputWriter writer) { this.writer = writer; }
    public abstract void format(StatisticsResult result) throws IOException;
}