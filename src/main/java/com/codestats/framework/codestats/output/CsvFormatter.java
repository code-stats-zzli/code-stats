package com.codestats.framework.codestats.output;

import com.codestats.framework.codestats.core.StatisticsResult;
import java.io.IOException;
import java.util.stream.Collectors;

public class CsvFormatter extends OutputFormatter {
    public CsvFormatter(OutputWriter writer) { super(writer); }

    @Override
    public void format(StatisticsResult result) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("path,lines,codeLines,commentLines,blankLines,language,complexity\n");
        sb.append(result.getFileInfos().stream()
                .map(info -> String.format("\"%s\",%d,%d,%d,%d,%s,%d",
                        escapeCsv(info.getPath().toString()),
                        info.getLines(), info.getCodeLines(), info.getCommentLines(),
                        info.getBlankLines(), info.getLanguage().name(), info.getComplexity()))
                .collect(Collectors.joining("\n")));
        writer.write(sb.toString());
    }

    private String escapeCsv(String s) {
        return s.replace("\"", "\"\"");
    }
}