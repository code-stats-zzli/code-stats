package com.omni.framework.codestats.output;

import com.omni.framework.codestats.core.FileInfo;
import com.omni.framework.codestats.core.StatisticsResult;
import com.omni.framework.codestats.language.Language;

import java.io.IOException;
import java.util.Comparator;
import java.util.Map;

public class ConsoleFormatter extends OutputFormatter {
    public ConsoleFormatter(OutputWriter writer) { super(writer); }

    @Override
    public void format(StatisticsResult result) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========== Code Statistics ==========\n\n");
        for (FileInfo info : result.getFileInfos()) {
            sb.append(String.format("%-80s : %8d lines", truncate(info.getPath().toString(), 78), info.getLines()));
            sb.append("\n");
        }
        sb.append("\n---------- Summary ----------\n");
        sb.append(String.format("Total files: %d\n", result.getFileInfos().size()));
        sb.append(String.format("Total lines: %d\n", result.getTotalLines()));
        sb.append(String.format("  Code lines: %d\n", result.getTotalCodeLines()));
        sb.append(String.format("  Comment lines: %d\n", result.getTotalCommentLines()));
        sb.append(String.format("  Blank lines: %d\n", result.getTotalBlankLines()));
        sb.append(String.format("Time: %d ms\n", result.getDurationMs()));
        sb.append("\n---------- By Language ----------\n");
        result.getLanguageSummary().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Enum::name)))
                .forEach(e -> {
                    Language lang = e.getKey();
                    StatisticsResult.LanguageSummary sum = e.getValue();
                    sb.append(String.format("%-12s : files=%d, lines=%d, code=%d, comment=%d, blank=%d\n",
                            lang, sum.getFiles(), sum.getLines(), sum.getCode(), sum.getComments(), sum.getBlanks()));
                });
        writer.write(sb.toString());
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : "..." + s.substring(s.length() - max + 3);
    }
}